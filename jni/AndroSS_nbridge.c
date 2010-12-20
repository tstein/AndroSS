#include <linux/ioctl.h>
#include <sys/stat.h>
#include <jni.h>
#include <stdio.h>
#include <stdlib.h>

#include <android.h>
#define MAX_INFO_BYTES 128
#define MAX_CMD_LEN 256
#define MAX_BYTES_DIGITS 16


static const char * TAG = "AndroSS";
static const char * envvar = "ANDROSS_FRAMEBUFFER_BYTES";


jstring Java_net_tedstein_AndroSS_AndroSSService_getFBInfo(
		JNIEnv * env, jobject this,
		jstring bin_location) {
	char strbuf[MAX_INFO_BYTES] = {0};
	jstring ret = (*env)->NewStringUTF(env, strbuf);
	char cmd[MAX_CMD_LEN] = {0};
	const char * data_dir = (*env)->GetStringUTFChars(env, bin_location, 0);

	// Bad things will happen if the binary isn't executable, so let's make sure
	// it is before we try to use it.
	strncpy(cmd, "chmod +x ", 9);
	strncat(cmd, data_dir, MAX_CMD_LEN - 9 - 16);
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

jbyteArray Java_net_tedstein_AndroSS_AndroSSService_getFBPixels(
		JNIEnv * env, jobject this,
		jstring bin_location, jint bytes) {
	char cmd[MAX_CMD_LEN] = {0};
	const char * data_dir = (*env)->GetStringUTFChars(env, bin_location, 0);
	strncpy(cmd, "su -c    ", 9);
	strncat(cmd, data_dir, MAX_CMD_LEN - 9 - 16);
	strncat(cmd, "/AndroSS", 16);
	LogD("NBridge: Executing %s", cmd);

	void * pixbuf = malloc(bytes);
	char bytes_str[MAX_BYTES_DIGITS];
	sprintf(bytes_str, "%u", bytes);

	// Tell the external binary how many bytes to read from the framebuffer.
	setenv(envvar, bytes_str, 1);

	// And then slurp the data.
	LogD("NBridge: Executing %s", cmd);
	FILE * from_extbin = popen(cmd, "r");
	int chunks_read = fread(pixbuf, bytes, 1, from_extbin);
	if (ferror(from_extbin) && !(feof(from_extbin))) {
		LogE("NBridge: Error reading framebuffer data from subprocess!");
		return 0;
	}

	// Finally, cast pixbuf as an jbyte[] and convert it to a jbyteArray we can
	// return to Java.
	jbyteArray ret = (*env)->NewByteArray(env, bytes);
	(*env)->SetByteArrayRegion(env, ret, 0, bytes, (jbyte *)pixbuf);
	free(pixbuf);
	LogD("NBridge: Returning data.");
	return ret;
}

