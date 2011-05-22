#include <linux/fb.h>
#include <sys/ioctl.h>
#include <fcntl.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>

static const char * TAG = "AndroSS";
#include "android.h"

#define STRING_BUFFER_SIZE 128
#define FD_STDOUT 1


/*
 * I've tried to pack all necessary functionality into this program without the
 * need of arguments so AndroSS only has to be su whitelisted once. Therefore,
 * this program's behavior is determined by the value of the envvar
 * ANDROSS_MODE.
 *      case (TRUE):
 *      return 0. (Useful for su tests and initial whitelisting.)
 *
 *      case (FB_PARAMS):
 *      This binary will query the framebuffer for the following information,
 *      printing it in decimal in this order, separated by spaces:
 *          horizontal resolution
 *          vertical resolution
 *          pixel depth
 *          red offset
 *          red length
 *          green offset
 *          green length
 *          blue offset
 *          blue length
 *          alpha offset
 *          alpha length
 *
 *      case (FB_DATA):
 *      If this has a value, this binary will interpret it as the number of
 *      bytes it should read from the framebuffer and write out.
 */

int writeFBParams(int output_fd, int fb_fd)
{
    // Run the appropriate ioctls to find out what we're dealing with.
    struct fb_var_screeninfo fb_varinfo;
    if (ioctl(fb_fd, FBIOGET_VSCREENINFO, &fb_varinfo) < 0) {
        LogE("External: ioctl failed.");
        close(fb_fd);
        return(1);
    }

    char output_data[STRING_BUFFER_SIZE] = {0};
    sprintf(output_data, "%u %u %u %u %u %u %u %u %u %u %u",
            fb_varinfo.xres,
            fb_varinfo.yres,
            fb_varinfo.bits_per_pixel,
            fb_varinfo.blue.offset,
            fb_varinfo.blue.length,
            fb_varinfo.green.offset,
            fb_varinfo.green.length,
            fb_varinfo.red.offset,
            fb_varinfo.red.length,
            fb_varinfo.transp.offset,
            fb_varinfo.transp.length);

    write(output_fd, output_data, STRING_BUFFER_SIZE * sizeof(char));
    return(0);
}

int writeFBData(int output_fd, int fb_fd, int fb_bytes)
{
    void * bytes = malloc(fb_bytes);
    int bytes_read = read(fb_fd, bytes, fb_bytes);
    if (bytes_read < fb_bytes) {
        LogE("External: Only read %d bytes from framebuffer!", bytes_read);
        return 1;
    }
    LogD("External: read %u bytes from framebuffer.", bytes_read);

    int bytes_written = write(output_fd, bytes, fb_bytes);
    if (bytes_written < fb_bytes) {
        LogE("External: Only wrote %d bytes!", bytes_written);
        return 1;
    }
    LogD("External: wrote %u bytes.", bytes_written);

    return 0;
}

int main()
{
    // Find and open the correct framebuffer device.
#ifdef ANDROID
    int fb_fd = open("/dev/graphics/fb0", O_RDONLY);
#else
    int fb_fd = open("/dev/fb0", O_RDONLY);
#endif
    if (fb_fd < 0) {
        LogE("External: Could not open framebuffer device. Permissions problem?");
        return(1);
    }

    const char * mode_str = getenv("ANDROSS_MODE");
    if (mode_str == NULL) {
        LogE("External: ANDROSS_MODE was not set!");
        return 127;
    }

    int ret;
    if (strcmp(mode_str, "TRUE") == 0) {
        LogD("External: Running in True mode.");
        ret = 0;
    } else if (strcmp(mode_str, "FB_PARAMS") == 0) {
        LogD("External: Running in Param mode.");
        ret = writeFBParams(FD_STDOUT, fb_fd);
    } else if (strcmp(mode_str, "FB_DATA") == 0) {
        LogD("External: Running in Data mode.");
        const char * fb_bytes_str = getenv("ANDROSS_FRAMEBUFFER_BYTES");
        int fb_bytes = atoi(fb_bytes_str);
        ret = writeFBData(FD_STDOUT, fb_fd, fb_bytes);
    } else {
        char errmsg[STRING_BUFFER_SIZE] = {0};
        strcpy(errmsg, "External: Invalid ANDROSS_MODE: ");
        strncat(errmsg, mode_str, STRING_BUFFER_SIZE - 32 - 1);
        ret = 127;
    }
    return ret;
}

