/*
 * Copyright (C) 2021 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
#include <jni.h>
#include <stdbool.h>
#include <stddef.h>
#include <stdint.h>

#if defined(__x86_64__)

#include <cpuid.h>
#include <immintrin.h>

static bool hasMovbe = false;

static uint8_t alignmentAdjusters[] = { 0, 5, 2, 7, 4, 1, 6, 3 };

/*
 * Unpacking function using the x86-64 MOVBE instruction. See
 * https://software.intel.com/sites/landingpage/IntrinsicsGuide/#text=movbe.
 *
 * The 'numPixels' parameter has to be a multiple of 8.
 * For optimal performance the 'bytes' and 'pixels' pointers have to be 8-byte aligned.
 */
static void unpackRgb888Movbe(const uint8_t* bytes, int numPixels, uint32_t* pixels) {
  uint64_t* limit = (uint64_t*) (pixels + numPixels);
  uint64_t* p = (uint64_t*) pixels;
  while (p < limit) {
    // Load 24 bytes into three 64-bit variables replacing big endian order by little endian one.
    uint64_t a = _loadbe_i64(bytes); // Intrinsic for MOVBE instruction.
    bytes += 8;
    uint64_t b = _loadbe_i64(bytes);
    bytes += 8;
    uint64_t c = _loadbe_i64(bytes);
    bytes += 8;
    // Convert the three 64-bit values into four by adding 0xFF opacity bytes and compensate for
    // swapping of neighbouring pixels that happened when changing the byte order to little endian.
    *p++ = a >> 40 | (a << 16 & 0xFFFFFFFF00000000) | 0xFF000000FF000000;
    *p++ = ((a << 8 | b >> 56) & 0x00000000FFFFFFFF) | (b & 0xFFFFFFFF00000000) | 0xFF000000FF000000;
    *p++ = (b >> 8 & 0x00000000FFFFFFFF) | ((b << 48 | c >> 16) & 0xFFFFFFFF00000000) | 0xFF000000FF000000;
    *p++ = (c  >> 24 & 0x00000000FFFFFFFF) | (c << 32 & 0xFFFFFFFF00000000) | 0xFF000000FF000000;
  }
}
#endif // defined(__x86_64__)

/* Generic unpacking function not using any special instructions. */
static void unpackRgb888Universal(const uint8_t* bytes, int numPixels, uint32_t* pixels) {
  uint32_t* limit = pixels + numPixels;
  while (pixels < limit) {
    uint32_t red = *bytes++;
    uint32_t green = *bytes++;
    uint32_t blue = *bytes++;
    *pixels++ = 0xFF000000 | red << 16 | green << 8 | blue;
  }
}

static void throwException(JNIEnv* env, const char* exceptionClassName, const char* message) {
  jclass exceptionClass = (*env)->FindClass(env, exceptionClassName);
  (*env)->ThrowNew(env, exceptionClass, message);
}

/*
 * Class:  com.android.emulator.ImageConverter
 * Method: static native void initNative()
 */
JNIEXPORT void JNICALL Java_com_android_emulator_ImageConverter_initNative(JNIEnv* env, jclass thisClass) {
#if defined(__x86_64__)
  int m = __get_cpuid_max(0, 0);
  if (m >= 1) {
    // Check if the MOVBE instruction is available.
    uint32_t a = 0;
    uint32_t b = 0;
    uint32_t c = 0;
    uint32_t d = 0;
    __cpuid(1, a, b, c, d);
    hasMovbe = (c & bit_MOVBE) != 0;
  }
#endif // defined(__x86_64__)
}

/*
 * Class:  com.android,emulator.ImageConverter
 * Method: static native void unpackRgb888(byte[] imageBytes, int offset, int length, int[] pixels)
 */
JNIEXPORT void JNICALL Java_com_android_emulator_ImageConverter_unpackRgb888(
    JNIEnv* env, jclass thisClass, jbyteArray byteArray, jint offset, jint length, jintArray pixelArray) {
  if (length == 0) {
    return;
  }
  if (offset < 0) {
    throwException(env, "java/lang/IllegalArgumentException", "The offset is negative");
  }
  if (length < 0) {
    throwException(env, "java/lang/IllegalArgumentException", "The number of bytes is negative");
  }
  if (length % 3 != 0) {
    throwException(env, "java/lang/IllegalArgumentException", "The number of bytes is not a multiple of 3");
  }
  if (offset + length > (*env)->GetArrayLength(env, byteArray)) {
    throwException(env, "java/lang/ArrayIndexOutOfBoundsException", "Data outside if the input array");
  }
  uint32_t numPixels = length / 3;
  if (numPixels > (*env)->GetArrayLength(env, pixelArray)) {
    throwException(env, "java/lang/ArrayIndexOutOfBoundsException", "The output array is too small");
  }
  jboolean isCopy;
  uint8_t* bytes = (*env)->GetPrimitiveArrayCritical(env, byteArray, &isCopy);
  if (bytes == NULL || isCopy) {
    (*env)->ReleasePrimitiveArrayCritical(env, byteArray, bytes, 0);
    throwException(env, "java/lang/IllegalStateException", "The input array cannot be pinned in memory");
  }
  uint32_t* pixels = (*env)->GetPrimitiveArrayCritical(env, pixelArray, &isCopy);
  if (pixels == NULL || isCopy) {
    (*env)->ReleasePrimitiveArrayCritical(env, pixelArray, pixels, 0);
    (*env)->ReleasePrimitiveArrayCritical(env, byteArray, bytes, 0);
    throwException(env, "java/lang/IllegalStateException", "The output array cannot be pinned in memory");
  }

  bytes += offset;

#if defined(__x86_64__)
  if (hasMovbe) {
    uint32_t alignment = offset & 0x07;
    if (alignment != 0) {
      uint32_t headLength = alignmentAdjusters[alignment];
      if (headLength > numPixels) {
        headLength = numPixels;
      }
      unpackRgb888Universal(bytes, headLength, pixels);
      if ((numPixels -= headLength) == 0) {
        goto release_arrays;
      }
      bytes += headLength * 3;
      pixels += headLength;
    }
    uint32_t tailLength = numPixels & 0x07;
    uint32_t numPixelsRounded = numPixels - tailLength;
    if (numPixelsRounded != 0) {
      unpackRgb888Movbe(bytes, numPixelsRounded, pixels);
    }
    if (tailLength != 0) {
      unpackRgb888Universal(bytes + numPixelsRounded * 3, tailLength, pixels + numPixelsRounded);
    }
  } else {
    unpackRgb888Universal(bytes, numPixels, pixels);
  }
#else // !defined(__x86_64__)
  unpackRgb888Universal(bytes, numPixels, pixels);
#endif // !defined(__x86_64__)

release_arrays:
  (*env)->ReleasePrimitiveArrayCritical(env, pixelArray, pixels, 0);
  (*env)->ReleasePrimitiveArrayCritical(env, byteArray, bytes, 0);
}
