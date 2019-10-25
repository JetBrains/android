/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.uibuilder.visual.colorblindmode

/**
 * All the numbers, math and explanation on how things work is documented in:
 * go/cbm_simulator
 */

const val DEBUG = false
const val MUTATED_FACTOR = 0.25
const val GAMMA = 2.2
const val DIM = 16

/**
 * All supported color blind modes.
 */
enum class ColorBlindMode(val displayName: String) {
  NONE("Original"), // Original
  PROTANOPES("Protanopes"), // Missing L
  PROTANOMALY("Protanomaly"), // Mutated L
  DEUTERANOPES("Deuteranopes"), // Missing M
  DEUTERANOMALY("Deuteranomaly"), // Mutated M
  TRITANOPES("Tritanopes"), // Missing S
}

/**
 * RGB to LMS based on Hunt-Pointer-Estevez transformation matrix in D65.
 */
val RGB_TO_LMS: Mat3D = Mat3D(
  0.31399, 0.63951, 0.04649,
  0.1577, 0.7696, 0.0880,
  0.0193, 0.1191, 0.9503)

/**
 * LMS to RGB, inverse of the [RGB_TO_LMS].
 */
val LMS_TO_RGB: Mat3D = Mat3D(
  //        Newer
  5.47041 , -4.56978 , 0.155553,
  -1.12436 , 2.25752 , -0.154046,
  0.0298141 , -0.190123 , 1.06845)

/**
 * Simple identity matrix.
 */
val IDENTITY_MATRIX: Mat3D = Mat3D(
  1.0, 0.0, 0.0,
  0.0, 1.0, 0.0,
  0.0, 0.0, 1.0)

/**
 * For protanopes lms to lms matrix built based on [RGB_TO_LMS]
 */
fun buildLms2Lmsp(factor: Double = 0.0): Mat3D {
  return Mat3D(
    0.0 + factor, 1.03526 * (1.0 - factor), -0.04694 * (1.0 - factor),
    0.0, 1.0, 0.0,
    0.0, 0.0, 1.0)
}

/**
 * Deuteropes lms to lms matrix
 */
fun buildLms2Lmsd(factor: Double = 0.0): Mat3D {
  return Mat3D(
    1.0, 0.0, 0.0,
    0.96594 * (1.0 - factor), 0.0 + factor, 0.045347 * (1.0 - factor),
    0.0, 0.0, 1.0)
}

/**
 * Tritanopes lms to lms matrix
 */
fun buildLms2Lmst(factor: Double = 0.0): Mat3D {
  return Mat3D(
    1.0, 0.0, 0.0,
    0.0, 1.0, 0.0,
    -0.94411 * (1.0 - factor), 2.0021 * (1.0 - factor), 0.0 + factor)
}
