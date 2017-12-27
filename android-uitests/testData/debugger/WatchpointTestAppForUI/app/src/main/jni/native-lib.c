#include <stdio.h>
#include <jni.h>

void function() {
  int read1 = 10;
  int read2 = 11;
  int write1 = 5;
  int write2 = 6;

  int i = write1 + write2;
  int ignore1 = write1 + write2;
  for (i = 0; i < 2; ++i) {
    // 1 stop
    int v_1 = write1 + read1;
    // 2 stops
    int v_2 = write1 + read1 + read2;

    // 1 stop
    write1 = v_1 + v_2;
    // 2 stops
    write1 = write1 + read1;
    // 1 stop
    write2 = write1;

    // 0 stops
    read1 = 12;

    // 1 stop
    read2 = read2 * 2;
  }
}

void function_for_watchpoint() {
    int write = 5;
    int read = 10;
    int dummy = 1;

    write = 8;

    int i = read + 10;
}

jstring
Java_com_example_watchpointtestapp_MainActivity_stringFromJNI( JNIEnv* env, jobject thiz )
{
  function();
  function_for_watchpoint();

  return (*env)->NewStringUTF(env, "Success");
}