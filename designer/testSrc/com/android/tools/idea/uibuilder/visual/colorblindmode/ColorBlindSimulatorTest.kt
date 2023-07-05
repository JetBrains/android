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

import java.util.function.Function
import junit.framework.TestCase
import kotlin.math.pow

private const val ERROR_THRESHOLD = 0.0001

class ColorBlindSimulatorTest : TestCase() {

  /** Smith and Pokorny XYZ to LMS. */
  fun testrgb2lms() {
    val rgb2xyz =
      Mat3D(40.9568, 35.5041, 17.9167, 21.3389, 70.6743, 7.98680, 1.86297, 11.4620, 91.2367)

    val xyz2lmx = Mat3D(0.15514, 0.54312, -0.03286, -0.15514, 0.45684, 0.03286, 0.0, 0.0, 0.01608)

    val rgb2lmsCalc = xyz2lmx * rgb2xyz
    assertTrue(RGB_TO_LMS.close(rgb2lmsCalc))
  }

  /**
   * Calculate with white and blue balance! (Meaning:
   *
   * For white: T [1, 1, 1] = [1, 1, 1]
   *
   * For blue: T [0, 0, 1] = bluetmp
   *
   * and calculate for [0, a, b] [ l ] [ am + bs ] [0, 1, 0] * [ m ] = [ m ] [0, 0, 1] [ s ] [ s ]
   *
   * Since we have value for white, and blue to match, just a simple linear algebra at this point.
   */
  fun testProtanopesCoefficients() {
    val removeGammaCLut = buildGammaCLut(Function { (it / 255.0).pow(GAMMA) })
    val whiteRgb = removeGamma(RGB(1.0, 1.0, 1.0), removeGammaCLut)
    val blueRgb = removeGamma(RGB(0.0, 0.0, 1.0), removeGammaCLut)

    val whitetmp = RGB_TO_LMS * whiteRgb
    val bluetmp = RGB_TO_LMS * blueRgb

    val w = LMS(whitetmp.first, whitetmp.second, whitetmp.third)
    val b = LMS(bluetmp.first, bluetmp.second, bluetmp.third)

    val aa = (b.l * w.s - w.l * b.s) / (b.m * w.s - w.m * b.s)
    val expected_aa = 2.0234421986713973
    assertTrue(Math.abs(aa - expected_aa) < ERROR_THRESHOLD)

    val bb = (b.l * w.m - w.l * b.m) / (b.s * w.m - w.s * b.m)
    val expected_bb = -2.5257918939861366
    assertTrue(Math.abs(bb - expected_bb) < ERROR_THRESHOLD)

    val mat3D = Mat3D(0.0, aa, bb, 0.0, 1.0, 0.0, 0.0, 0.0, 1.0)
    assertTrue(mat3D.close(buildLms2Lmsp(0.0)))
  }

  /**
   * Similar to above but:
   *
   * [ 1, 0, 0 ] [ l ] [ l ] [ a, 0, b ] * [ m ] = [ al + bs ] [ 0, 0, 1 ] [ s ] [ s ]
   */
  fun testDeuteranopesCoefficients() {
    val removeGammaCLut = buildGammaCLut(Function { (it / 255.0).pow(GAMMA) })
    val whiteRgb = removeGamma(RGB(1.0, 1.0, 1.0), removeGammaCLut)
    val blueRgb = removeGamma(RGB(0.0, 0.0, 1.0), removeGammaCLut)
    val whitetmp = RGB_TO_LMS * whiteRgb
    val bluetmp = RGB_TO_LMS * blueRgb

    val w = LMS(whitetmp.first, whitetmp.second, whitetmp.third)
    val b = LMS(bluetmp.first, bluetmp.second, bluetmp.third)

    val aa = (b.m * w.s - b.s * w.m) / (b.l * w.s - w.l * b.s)
    val exptected_aa = 0.49420734659809173
    assertTrue(Math.abs(aa - exptected_aa) < ERROR_THRESHOLD)

    val bb = (w.l * b.m - b.l * w.m) / (w.l * b.s - b.l * w.s)
    val exptected_bb = 1.2482649099858572
    assertTrue(Math.abs(bb - exptected_bb) < ERROR_THRESHOLD)

    val mat3D = Mat3D(1.0, 0.0, 0.0, aa, 0.0, bb, 0.0, 0.0, 1.0)
    assertTrue(mat3D.close(buildLms2Lmsd(0.0)))
  }

  /**
   * Similar to above but:
   *
   * [ 1, 0, 0 ] [ l ] [ l ] [ 0, 1, 0 ] * [ m ] = [ m ] [ a, b, 0 ] [ s ] [ al + bm ]
   *
   * Here, we balance on red instead.
   */
  fun testTritanopesCoefficients() {
    val whiteRgb = RGB(1.0, 1.0, 1.0)
    val redRgb = RGB(1.0, 0.0, 0.0)
    val whitetmp = RGB_TO_LMS * whiteRgb
    val redtmp = RGB_TO_LMS * redRgb

    val w = LMS(whitetmp.first, whitetmp.second, whitetmp.third)
    val r = LMS(redtmp.first, redtmp.second, redtmp.third)

    val aa = (r.s * w.m - w.s * r.m) / (r.l * w.m - w.l * r.m)
    val exptected_aa = -0.01224497828329193
    assertTrue(Math.abs(aa - exptected_aa) < ERROR_THRESHOLD)

    val bb = (r.s * w.l - w.s * r.l) / (r.m * w.l - w.m * r.l)
    val exptected_bb = 0.07203455200993725
    assertTrue(Math.abs(bb - exptected_bb) < ERROR_THRESHOLD)

    val mat3D = Mat3D(1.0, 0.0, 0.0, 0.0, 1.0, 0.0, aa, bb, 0.0)
    assertTrue(mat3D.close(buildLms2Lmst(0.0)))
  }
}
