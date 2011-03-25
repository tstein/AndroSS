#include <linux/ioctl.h>
#include <sys/stat.h>
#include <sys/time.h>
#include <jni.h>
#include <stdio.h>
#include <stdlib.h>

#include <android.h>
#define MAX_INFO_BYTES 128
#define MAX_CMD_LEN 256
#define MAX_BYTES_DIGITS 16


static const char * TAG = "AndroSS";
static const char * envvar = "ANDROSS_FRAMEBUFFER_BYTES";


jint JNI_OnLoad(JavaVM * vm, void * reserved) {
    JNIEnv * env;
    if ((*vm)->GetEnv(vm, (void **)&env, JNI_VERSION_1_6) != JNI_OK) {
        return -1;
    } else {
        return JNI_VERSION_1_6;
    }
}


/**
 * @param in - The value of the input pixel.
 * @param offsets - An array of four bytes representing the offset of each
 * color in the input pixel. This will be interpreted as [b, g, r, a].
 * @param sizes - An array of four bytes representing how many bits each
 * color occupies in the input pixel. This will be interpreted as
 * [b, g, r, a].
 * @return The input pixel formatted as an ARGB_8888 int.
 */
unsigned int static inline formatPixel(unsigned int in, int * offsets, int * sizes) {
    unsigned char out[4];
    unsigned int mask;

    for (int color = 0; color < 4; ++color) {
        // Build the mask by repeatedly shifting and incrementing.
        mask = 0;
        for (int bits = 0; bits < sizes[color]; ++bits) {
            mask <<= 1;
            ++mask;
        }

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
    unsigned int ret = 0;
    for (int color = 3; color >= 0; --color) {
        ret <<= 8;
        ret |= out[color];
    }
    return ret;
}


jboolean Java_net_tedstein_AndroSS_ConfigurationActivity_testForSu(
        JNIEnv * env, jobject this) {
    return !(system("su -c true"));
}


jstring Java_net_tedstein_AndroSS_AndroSSService_getFBInfo(
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

    // Tell the external binary we just want info about the framebuffer.
    unsetenv(envvar);

    LogD("NBridge: Executing %s", cmd);
    FILE * from_extbin = popen(cmd, "r");
    int bytes_read = fread(strbuf, MAX_INFO_BYTES, 1, from_extbin);
    if (ferror(from_extbin)) {
        LogE("Nbridge: Error reading from subprocess!");
    } else {
        ret = (*env)->NewStringUTF(env, strbuf);
    }
    pclose(from_extbin);
    return ret;
}


jintArray Java_net_tedstein_AndroSS_AndroSSService_getFBPixels(
        JNIEnv * env, jobject this,
        jstring bin_location,
        jint pixels, jint bpp,
        jintArray offsets_j, jintArray sizes_j) {
    // Extract color offsets and sizes from the Java array types.
    int offsets[4], sizes[4];
    (*env)->GetIntArrayRegion(env, offsets_j, 0, 4, offsets);
    (*env)->GetIntArrayRegion(env, sizes_j, 0, 4, sizes);

    char cmd[MAX_CMD_LEN] = {0};
    const char * data_dir = (*env)->GetStringUTFChars(env, bin_location, 0);
    strncpy(cmd, "su -c    ", 9);
    strncat(cmd, data_dir, MAX_CMD_LEN - 9 - 16);
    strncat(cmd, "/AndroSS", 16);
    LogD("NBridge: Executing %s", cmd);

    // Allocate enough space to store all pixels in ARGB_8888. We'll initially
    // put the pixels at the highest address within our buffer they can fit.
    unsigned char * pixbuf = malloc(pixels * 4);
    int pixbuf_offset = (pixels * 4) - (pixels * bpp);
    char bytes_str[MAX_BYTES_DIGITS];
    sprintf(bytes_str, "%u", pixels * bpp);

    // Tell the external binary how many bytes to read from the framebuffer.
    setenv(envvar, bytes_str, 1);

    // And then slurp the data.
    LogD("NBridge: Executing %s", cmd);
    FILE * from_extbin = popen(cmd, "r");
    int chunks_read = fread(pixbuf + pixbuf_offset, pixels * bpp, 1, from_extbin);
    if (ferror(from_extbin) && !(feof(from_extbin))) {
        LogE("NBridge: Error reading framebuffer data from subprocess!");
        return 0;
    }

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

    unsigned char * curr_pix = pixbuf + pixbuf_offset;
    for (int i = 0; i < pixels; ++i) {
        unsigned int pix = *((unsigned int *)curr_pix) >> ((4 - bpp) * 8);
        *(unsigned int *)(pixbuf + (4 * i)) = formatPixel(pix, offsets, sizes);
        curr_pix += bpp;
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

