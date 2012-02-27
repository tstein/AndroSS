#define _GNU_SOURCE
#include <sys/mman.h>
#include <sys/stat.h>
#include <fcntl.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#include "bitwork.h"


static const int MAX_TEST_SIZE = 128;

int main(int argc, char ** argv) {
    int tests = 0;
    int tests_failed = 0;

    int format_tests_fd = open("./format_tests.txt", O_RDONLY);
    struct stat format_tests_stat;
    fstat(format_tests_fd, &format_tests_stat);
    int format_tests_size = format_tests_stat.st_size;

    char * format_tests = (char *) mmap(NULL, format_tests_size, PROT_READ,
            MAP_PRIVATE, format_tests_fd, 0);
    char * test_buf = (char *)calloc(MAX_TEST_SIZE, 1);

    for (char * i = format_tests; i < format_tests + format_tests_size;
            i = strchrnul(i, '\n') + 1) {
        memset(test_buf, 0, MAX_TEST_SIZE);
        char * eol = strchrnul(i, '\n');
        int test_size = eol - i < MAX_TEST_SIZE ? eol - i : MAX_TEST_SIZE - 1;
        if (test_size == 0) continue;
        strncpy(test_buf, i, test_size);
        if (test_buf[0] == '#') continue;

        uint32_t in;
        int offsets[4];
        int sizes[4];
        uint32_t expected;
        in = strtol(strtok(test_buf, " "), NULL, 16);
        for (int i = 0; i < 4; ++i) {
            sizes[i] = atoi(strtok(NULL, " "));
            offsets[i] = atoi(strtok(NULL, " "));
        }
        expected = strtol(strtok(NULL, " "), NULL, 16);

        uint32_t actual = formatPixel(in, offsets, sizes);
        if (actual != expected) {
            printf("%3d: r%d@%2d g%d@%2d b%d@%2d a%d@%2d: given 0x%08x, expected 0x%08x, got 0x%08x\n",
                    tests,
                    sizes[0],
                    offsets[0],
                    sizes[1],
                    offsets[1],
                    sizes[2],
                    offsets[2],
                    sizes[3],
                    offsets[3],
                    in,
                    expected,
                    actual);
            ++tests_failed;
        }
        ++tests;
    }

    if (tests_failed > 0) {
        printf("Failed %d of %d tests.\n", tests_failed, tests);
        return 1;
    } else {
        return 0;
    }
}

