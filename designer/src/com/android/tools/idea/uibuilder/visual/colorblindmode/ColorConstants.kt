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

/** All the numbers, math and explanation on how things work is documented in: go/cbm_simulator */
const val MUTATED_FACTOR = 0.25
const val GAMMA = 2.2
const val DIM = 16

/** All supported color blind modes. */
enum class ColorBlindMode(val displayName: String) {
  NONE("Original"), // Original
  PROTANOPES("Protanopes"), // Missing L
  PROTANOMALY("Protanomaly"), // Mutated L
  DEUTERANOPES("Deuteranopes"), // Missing M
  DEUTERANOMALY("Deuteranomaly"), // Mutated M
  TRITANOPES("Tritanopes"), // Missing S
  TRITANOMALY("Tritanomaly"), // Mutated S
}

/**
 * RGB to LMS based on the paper "Digital Vido Colourmaps for Checking the Legibility of Displays by
 * Dichromats"
 */
val RGB_TO_LMS: Mat3D =
  Mat3D(17.8824, 43.5161, 4.11935, 3.45565, 27.1554, 3.86713, 0.0299566, 0.184309, 1.46709)

/** LMS to RGB, inverse of the [RGB_TO_LMS]. */
val LMS_TO_RGB: Mat3D =
  Mat3D(
    0.080944,
    -0.13054,
    0.116721,
    -0.0102485,
    0.0540194,
    -0.113615,
    -0.000365294,
    -0.00412163,
    0.693513,
  )

/** Simple identity matrix. */
val IDENTITY_MATRIX: Mat3D = Mat3D(1.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 1.0)

/** For protanopes lms to lms matrix built based on [RGB_TO_LMS] */
fun buildLms2Lmsp(factor: Double = 0.0): Mat3D {
  return Mat3D(
    0.0 + factor,
    2.02344 * (1.0 - factor),
    -2.52579 * (1.0 - factor),
    0.0,
    1.0,
    0.0,
    0.0,
    0.0,
    1.0,
  )
}

/** Deuteropes lms to lms matrix */
fun buildLms2Lmsd(factor: Double = 0.0): Mat3D {
  return Mat3D(
    1.0,
    0.0,
    0.0,
    0.494207 * (1.0 - factor),
    0.0 + factor,
    1.24826 * (1.0 - factor),
    0.0,
    0.0,
    1.0,
  )
}

/** Tritanopes lms to lms matrix */
fun buildLms2Lmst(factor: Double = 0.0): Mat3D {
  return Mat3D(
    1.0,
    0.0,
    0.0,
    0.0,
    1.0,
    0.0,
    -0.012244 * (1.0 - factor),
    0.072034 * (1.0 - factor),
    0.0 + factor,
  )
}
