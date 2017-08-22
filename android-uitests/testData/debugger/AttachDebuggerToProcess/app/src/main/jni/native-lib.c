#include <stdio.h>
#include <string.h>
#include <jni.h>

int add_10_ints() {
    int x1 = 1;
    int x2 = 2;
    int x3 = 3;
    int x4 = 4;
    int x5 = 5;
    int x6 = 6;
    int x7 = 7;
    int x8 = 8;
    int x9 = 9;
    int x10 = 10;

    int sum = x1 + x2 + x3 + x4  + x5 + x6 + x7 + x8 + x9 + x10;
    return sum;
}

int multiply_10_ints() {
    int x1 = 1;
    int x2 = 2;
    int x3 = 3;
    int x4 = 4;
    int x5 = 5;
    int x6 = 6;
    int x7 = 7;
    int x8 = 8;
    int x9 = 9;
    int x10 = 10;

    int product = x1 * x2 * x3 * x4  * x5 * x6 * x7 * x8 * x9 * x10;
    return product;
}

int divide_2_ints() {
    int x1 = 1024;
    int x2 = 2;
    int quotient = x1 / x2;
    return quotient;
}

jstring
Java_com_example_basiccmakeapp_MainActivity_stringFromJNI( JNIEnv* env, jobject thiz )
{
    int sum_of_10_ints = add_10_ints();
    int product_of_10_ints = multiply_10_ints();
    int quotient = divide_2_ints();

    char message[100];
    sprintf(message,
            "Success. Sum = %d, Product = %d, Quotient = %d",
            sum_of_10_ints, product_of_10_ints, quotient);

    return (*env)->NewStringUTF(env, message);
}