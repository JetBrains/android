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
    int x10 = 10; // x10-decl

    int sum = x1 + x2 + x3 + x4  + x5 + x6 + x7 + x8 + x9 + x10;
    return sum; // add-return
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
    return product; // multiply-return
}

int divide_2_ints() {
    int x1 = 1024;
    int x2 = 2;
    int quotient = x1 / x2;
    return quotient; // divide-return
}

struct args {
    int x1, x2, x3, x4, x5;
};

int inspect_arguments(struct args a) {
    // Having the local variable here is important as it forces the compiler to
    // create a non-empty stack frame.
    int result = a.x1 + a.x2 + a.x3 + a.x4 + a.x5; // inspect_arguments
    return result;
}

jstring
Java_com_example_basiccmakeapp_MainActivity_stringFromJNI( JNIEnv* env, jobject thiz )
{
    int sum_of_10_ints = add_10_ints(); // stringFromJNI-first_line
    int product_of_10_ints = multiply_10_ints();
    int quotient = divide_2_ints();
    inspect_arguments((struct args){1, 2, 3, 4, 5});

    char message[100];
    sprintf(message,
            "Success. Sum = %d, Product = %d, Quotient = %d",
            sum_of_10_ints, product_of_10_ints, quotient);

    return (*env)->NewStringUTF(env, message);
}
