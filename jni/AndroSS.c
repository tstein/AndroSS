#include <linux/fb.h>
#include <sys/ioctl.h>
#include <fcntl.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>

static const char * TAG = "AndroSS";
#include "android.h"

#define PARAM_BUFFER_SIZE 32
#define FD_STDOUT 1


/*
 * I've tried to pack all necessary functionality into this program without the
 * need of arguments so AndroSS only has to be su whitelisted once. Therefore,
 * this program's behavior is determined by the value of the envvar
 * ANDROSS_FRAMEBUFFER_BYTES.
 *      If this is not defined (or an empty string), this binary will query the
 *      framebuffer for horizontal resolution, vertical resolution, and pixel
 *      depth, and write those values in that order, separated by spaces.
 *
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

    char output_data[PARAM_BUFFER_SIZE] = {0};
    sprintf(output_data, "%d %d %d",
            fb_varinfo.xres,
            fb_varinfo.yres,
            fb_varinfo.bits_per_pixel);

    write(output_fd, output_data, PARAM_BUFFER_SIZE * sizeof(char));
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
        LogE("External: Only wrote %d bytes!");
        return 1;
    }
    LogD("External: wrote %u bytes.", bytes_written);

    return 0;
}

int main(int argc, const char * argv[])
{
    // Find and open the correct framebuffer device.
    int fb_fd = open("/dev/graphics/fb0", O_RDONLY);
    if (fb_fd < 0) {
        LogE("External: Could not open framebuffer device. Permissions problem?");
        return(1);
    }

    const char * fb_bytes_str = getenv("ANDROSS_FRAMEBUFFER_BYTES");
    int ret;
    if (fb_bytes_str == NULL || strlen(fb_bytes_str) == 0) {
        LogD("External: Running in Param mode.");
        ret = writeFBParams(FD_STDOUT, fb_fd);
    } else {
        LogD("External: Running in Data mode.");
        int fb_bytes = atoi(fb_bytes_str);
        ret = writeFBData(FD_STDOUT, fb_fd, fb_bytes);
    }

    return ret;
}

