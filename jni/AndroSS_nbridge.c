#include <linux/ioctl.h>
#include <sys/stat.h>
#include <sys/time.h>
#include <sys/wait.h>
#include <errno.h>
#include <fcntl.h>
#include <jni.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>

#include "android.h"
#define MAX_INFO_BYTES 128
#define MAX_CMD_LEN 255
#define MAX_BYTES_DIGITS 16


static const char * TAG = "AndroSS";
static const char * MODE_ENVVAR = "ANDROSS_MODE";
static const char * FB_BYTES_ENVVAR = "ANDROSS_FRAMEBUFFER_BYTES";
static const ssize_t TEGRA_SKIP_BYTES = 52;
// These MUST be kept consistent with the enum DeviceType in AndroSSService!
static const int TYPE_GENERIC = 1;
static const int TYPE_TEGRA = 2;
// Define some masks so we don't have to calculate them later.
const uint32_t masks[32] = {
    0x00000001,
    0x00000003,
    0x00000007,
    0x0000000f,
    0x0000001f,
    0x0000003f,
    0x0000007f,
    0x000000ff,
    0x000001ff,
    0x000003ff,
    0x000007ff,
    0x00000fff,
    0x00001fff,
    0x00003fff,
    0x00007fff,
    0x0000ffff,
    0x0001ffff,
    0x0003ffff,
    0x0007ffff,
    0x000fffff,
    0x001fffff,
    0x003fffff,
    0x007fffff,
    0x00ffffff,
    0x01ffffff,
    0x03ffffff,
    0x07ffffff,
    0x0fffffff,
    0x1fffffff,
    0x3fffffff,
    0x7fffffff,
    0xffffffff };


jint JNI_OnLoad(JavaVM * vm, void * reserved) {
    JNIEnv * env;
    if ((*vm)->GetEnv(vm, (void **)&env, JNI_VERSION_1_6) != JNI_OK) {
        return -1;
    } else {
        return JNI_VERSION_1_6;
    }
}


/**
 * @param argv - To be passed directly to execv. argv[0] must be the full path
 *      to the binary you want to execute.
 * @param output_buf - The buffer in which to store the child process's output.
 * @param buf_len - To be passed to read(). Passing 0 for this and toss_bytes
 *      will result in no accesses being made to output_buf, making it safe to
 *      pass NULL.
 * @param fd - 1 and 2 are probably the only values that make sense.
 * @param toss_bytes - Bytes to skip at the beginning of the child process's
 *      output. It is unsafe for this to be greater than the size of your
 *      buffer.
 */
static inline int execForOutput(char ** argv, uint8_t * output_buf, int buf_len,
        int fd, int toss_bytes) {
    int bytes_read = -1;
    int child_status;
    int pipefd[2];
    pipe(pipefd);

    int cpid = fork();
    if (cpid == 0) {
        close(pipefd[0]);
        dup2(pipefd[1], fd);
        execv(argv[0], argv);
    }

    close(pipefd[1]);
    if (toss_bytes > 0) {
        int bytes_tossed = read(pipefd[0], output_buf, toss_bytes);
        if (bytes_tossed != toss_bytes) {
            LogE("NBridge: Error skipping junk! Only tossed %d bytes.",
                    bytes_tossed);
            return -1;
        }
    }

    bytes_read = read(pipefd[0], output_buf, buf_len);
    LogD("NBridge: Read %d bytes from subprocess.", bytes_read);
    close(pipefd[0]);
    return waitpid(cpid, &child_status, 0);
}


/**
 * @param pixels - A pointer to the memory with all the pixels.
 * @param index - The desired pixel, indexed from 0.
 * @param size - The size of a pixel, in bytes.
 */
static inline uint32_t extractPixel(uint8_t * pixels, uint32_t index, uint8_t size) {
    // pix_ptr points to the low byte of the pixel and is not necessarily
    // aligned.
    uint8_t * pix_ptr = pixels + (index * size);
    uint8_t misalignment = (uint32_t)pix_ptr % 4;

    // Given that pixels are no more than four bytes, each pixel will have some
    // data in the 32-bit word at this address and may overflow to the next.
    uint32_t * lower_word_ptr = (uint32_t *)(pix_ptr - misalignment);
    uint8_t overflow = misalignment + size <= 4 ? 0 : (misalignment + size) % 4;

    uint32_t ret = *lower_word_ptr;
    ret >>= misalignment * 8;
    ret &= masks[(size - overflow) * 8 - 1];

    if (overflow > 0) {
        // There are relevant bits in the next word. Mask them out and add them
        // to ret.
        uint32_t top = *(lower_word_ptr + 1);
        top &= masks[overflow * 8 - 1];
        top <<= (size - overflow) * 8;
        ret += top;
    }

    return ret;
}


/**
 * @param in - The value of the input pixel.
 * @param offsets - An array of four bytes representing the offset of each
 *      color in the input pixel. This will be interpreted as [b, g, r, a].
 * @param sizes - An array of four bytes representing how many bits each
 *      color occupies in the input pixel. This will be interpreted as
 *      [b, g, r, a].
 * @return The input pixel formatted as an ARGB_8888 int.
 */
static inline uint32_t formatPixel(uint32_t in, int * offsets, int * sizes) {
    uint8_t out[4];
    uint32_t mask;

    for (int color = 0; color < 4; ++color) {
        mask = masks[sizes[color] - 1];

        // Extract the desired bits from in, then shift them up if we have
        // less than a full byte of information.
        out[color] = (in >> offsets[color]) & mask;
        out[color] <<= 8 - sizes[color];
    }

    // If the framebuffer had no alpha channel, we're about to return an
    // invisible pixel.
    if (sizes[3] == 0) {
        out[3] = 255;
    }

    // Finally, combine the components, and that's a pixel.
    uint32_t ret = 0;
    for (int color = 3; color >= 0; --color) {
        ret <<= 8;
        ret |= out[color];
    }
    return ret;
}



/**
 * Make a file user- and group-executable.
 *
 * @param file_location_j - The file to chmod.
 * @return True if chmod returned 0, false otherwise.
 */
jboolean Java_net_tedstein_AndroSS_AndroSSService_chmodUPlusX(
        JNIEnv * env, jobject this,
        jstring file_location_j) {
    const char * file_location =
        (*env)->GetStringUTFChars(env, file_location_j, 0);
    char cmd[256] = {0};
    strncpy(cmd, "chmod 770 ", 10);
    strncat(cmd, file_location, MAX_CMD_LEN - 10);

    if (system(cmd) == 0) {
        return JNI_TRUE;
    } else {
        return JNI_FALSE;
    }
}



/*
 * Tests that need to be done in native code.
 */
jint Java_net_tedstein_AndroSS_AndroSSService_testForSu(
        JNIEnv * env, jobject this,
        jstring bin_location) {
    LogD("NBridge: Testing for su.");
    char cmd[MAX_CMD_LEN] = {0};
    const char * data_dir = (*env)->GetStringUTFChars(env, bin_location, 0);

    // Now change that buffer so we're ready to exec.
    strncpy(cmd, "su -c    ", 9);
    strncat(cmd, data_dir, MAX_CMD_LEN - 9 - 16);
    strncat(cmd, "/AndroSS", 16);

    // Tell the external binary to just return.
    setenv(MODE_ENVVAR, "TRUE", 1);

    LogD("NBridge: Executing %s", cmd);
    int ret = system(cmd);
    LogD("NBridge: system() returned %d.", ret);
    return ret;
}



/*
 * Generic screenshot code for devices where we have to go root and read fb0.
 */
jstring Java_net_tedstein_AndroSS_AndroSSService_getFBInfoGeneric(
        JNIEnv * env, jobject this,
        jstring bin_location) {
    char strbuf[MAX_INFO_BYTES] = {0};
    jstring ret = (*env)->NewStringUTF(env, strbuf);
    char cmd[MAX_CMD_LEN] = {0};
    const char * data_dir = (*env)->GetStringUTFChars(env, bin_location, 0);

    // Bad things will happen if the binary isn't executable, so let's make sure
    // it is before we try to use it.
    strncpy(cmd, "chmod 770 ", 10);
    strncat(cmd, data_dir, MAX_CMD_LEN - 10 - 16);
    strncat(cmd, "/AndroSS", 16);
    LogD("NBridge: Executing %s", cmd);
    system(cmd);

    // Now change that buffer so we're ready to exec.
    strncpy(cmd, "su -c    ", 9);

    // Tell the external binary we want info about the framebuffer.
    setenv(MODE_ENVVAR, "FB_PARAMS", 1);

    LogD("NBridge: Executing %s", cmd);
    FILE * from_extbin = popen(cmd, "r");
    int bytes_read = fread(strbuf, MAX_INFO_BYTES, 1, from_extbin);
    if (ferror(from_extbin)) {
        LogE("Nbridge: Error reading from subprocess!");
    } else {
        LogD("NBridge: Read %d bytes from subprocess.", bytes_read);
        ret = (*env)->NewStringUTF(env, strbuf);
    }
    pclose(from_extbin);
    return ret;
}


/*
 * Screenshot code for Tegra 2 devices.
 */
jstring Java_net_tedstein_AndroSS_AndroSSService_getFBInfoTegra2(
        JNIEnv * env, jobject this,
        jstring bin_location) {
    char * strbuf = (char *)calloc(1, MAX_INFO_BYTES);
    jstring ret = (*env)->NewStringUTF(env, strbuf);

    const char * fbread_path_const = (*env)->GetStringUTFChars(env, bin_location, 0);
    char * fbread_path = (char *)calloc(1, MAX_CMD_LEN);
    strncpy(fbread_path, fbread_path_const, MAX_CMD_LEN - 1);

    char ** fbread_args = (char **)calloc(2, sizeof(char *));
    *fbread_args = fbread_path;
    *(fbread_args + 1) = 0;

    int bytes_read = -1;
    int pipefd[2];
    int child_status;

    pipe(pipefd);
    LogD("NBridge: About to fork+exec for framebuffer info.");
    int cpid = fork();
    if (cpid == 0) {
        LogD("NBridge2: Child here!");
        close(pipefd[0]);
        LogD(fbread_path);
        dup2(pipefd[1], 2);
        dup2(open("/dev/null", O_WRONLY), 1);
        execv(fbread_path, fbread_args);
    }
    LogD("NBridge: Parent here! Child pid is %d", cpid);
    close(pipefd[1]);
    bytes_read = read(pipefd[0], strbuf, MAX_INFO_BYTES - 1);
    close(pipefd[0]);
    waitpid(cpid, &child_status, 0);

    LogD("NBridge: read %d bytes from child: %s", bytes_read, strbuf);
    ret = (*env)->NewStringUTF(env, strbuf);
    return ret;
}


/*
 * Retrieve the pixels. With appropriate arguments, this function works for all
 * device types.
 */
jintArray Java_net_tedstein_AndroSS_AndroSSService_getFBPixels(
        JNIEnv * env, jobject this,
        jint type,
        jstring command_j,
        jint pixels, jint bpp,
        jintArray offsets_j, jintArray sizes_j) {
    if (type == TYPE_GENERIC) {
        LogD("Getting pixels on a GENERIC device.");
    } else if (type == TYPE_TEGRA) {
        LogD("Getting pixels on a TEGRA device.");
    } else {
        LogE("What the hell am I getting pixels on?! Got type %d", type);
        return 0;
    }

    // Extract color offsets and sizes from the Java array types.
    int offsets[4], sizes[4];
    (*env)->GetIntArrayRegion(env, offsets_j, 0, 4, offsets);
    (*env)->GetIntArrayRegion(env, sizes_j, 0, 4, sizes);
    const char * command_const = (*env)->GetStringUTFChars(env, command_j, 0);
    char * command = (char *)calloc(1, strlen(command_const) + 1);
    strncpy(command, command_const, strlen(command_const));

    // Allocate enough space to store all pixels in ARGB_8888. We'll initially
    // put the pixels at the highest address within our buffer they can fit.
    uint8_t * pixbuf = malloc(pixels * 4);
    unsigned int pixbuf_offset = (pixels * 4) - (pixels * bpp);

    if (type == TYPE_GENERIC) {
        char bytes_str[MAX_BYTES_DIGITS];
        sprintf(bytes_str, "%u", pixels * bpp);

        // Tell the external binary to read the framebuffer and how many bytes we want.
        setenv(MODE_ENVVAR, "FB_DATA", 1);
        setenv(FB_BYTES_ENVVAR, bytes_str, 1);
    }

    // And then slurp the data.
    char * command_path = NULL;
    char ** command_args = NULL;
    if (type == TYPE_GENERIC) {
        int parts = 1;
        char * next_space = command;
        while (next_space != NULL) {
            ++parts;
            next_space = strchr(next_space + 1, ' ');
        }

        command_path = strtok(command, " ");
        command_args = (char **)calloc(parts + 1, sizeof(char *));
        *command_args = command_path;
        // strtok will helpfully provide a null to terminate the array.
        for (int i = 1; i < parts; ++i) {
            *(command_args + i) = strtok(NULL, " ");
        }
    } else if (type == TYPE_TEGRA) {
        command_path = command;
        command_args = (char **)calloc(2, sizeof(char *));
        *command_args = command_path;
        *(command_args + 1) = NULL;
    }

    LogD("Executing %s with args:", command_path);
    char ** arg = command_args + 1;
    while (*arg != NULL) {
        LogD("\t%s", *arg);
        ++arg;
    }

    execForOutput(command_args, pixbuf + pixbuf_offset, pixels * bpp, 1,
        type == TYPE_TEGRA ? TEGRA_SKIP_BYTES : 0);

    // Convert all of the pixels to ARGB_8888 according to the parameters passed
    // in from Dalvikspace. To save space and time, we do this in-place. If each
    // pixel is fewer than four bytes, this involves shifting data like this:
    // (lower addresses to the left, r = raw, f = formatted, two bytes per char)
    // < -- -- -- -- r1 r2 r3 r4 >
    // < f1 f1 -- -- r1 r2 r3 r4 >
    // < f1 f1 f2 f2 r1 r2 r3 r4 >
    // < f1 f1 f2 f2 f3 f3 r3 r4 >
    // < f1 f1 f2 f2 f3 f3 f4 f4 >
    LogD("NBridge: Converting %u pixels.", pixels);
    struct timeval start_tv, end_tv;
    gettimeofday(&start_tv, NULL);

    uint8_t * unformatted_pixels = pixbuf + pixbuf_offset;
    for (int i = 0; i < pixels; ++i) {
        uint32_t pix = extractPixel(unformatted_pixels, i, bpp);
        *(((uint32_t *)pixbuf) + i) = formatPixel(pix, offsets, sizes);
    }

    gettimeofday(&end_tv, NULL);
    int seconds = end_tv.tv_sec - start_tv.tv_sec;
    int useconds = end_tv.tv_usec - start_tv.tv_usec;
    LogD("NBridge: Conversion finished in %u ms.", (seconds * 1000) + (useconds / 1000));


    // Finally, cast pixbuf as an jint[] and convert it to a jintArray we can
    // return to Java.
    jintArray ret = (*env)->NewIntArray(env, pixels);
    (*env)->SetIntArrayRegion(env, ret, 0, pixels, (jint *)pixbuf);
    free(pixbuf);
    LogD("NBridge: Returning data.");
    return ret;
}

