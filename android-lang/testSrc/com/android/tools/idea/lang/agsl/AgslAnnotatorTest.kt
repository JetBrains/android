/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.lang.agsl

import com.android.tools.idea.flags.StudioFlags
import org.intellij.lang.annotations.Language
import org.jetbrains.android.JavaCodeInsightFixtureAdtTestCase
import org.jetbrains.kotlin.idea.KotlinFileType

class AgslAnnotatorTest : JavaCodeInsightFixtureAdtTestCase() {
  override fun setUp() {
    super.setUp()
    StudioFlags.AGSL_LANGUAGE_SUPPORT.override(true)
  }

  override fun tearDown() {
    try {
      StudioFlags.AGSL_LANGUAGE_SUPPORT.clearOverride()
    }
    finally {
      super.tearDown()
    }
  }

  fun testSimple() {
    checkHighlighting(
      """
      uniform shader imageA;
      uniform shader imageB;
      uniform ivec2 imageDimensions;
      uniform float progress;

      const vec2 iSize = vec2(48.0, 48.0);
      const float iDir = 0.5;
      const  float iRand = 0.81;

      float hash12(vec2 p) {
          vec3 p3  = fract(vec3(p.xyx) * .1031);
          p3 += dot(p3, p3.yzx + 33.33);
          return fract((p3.x + p3.y) * p3.z);
      }

      float ramp(float2 p) {
        return mix(hash12(p),
                   dot(p/vec2(imageDimensions), float2(iDir, 1 - iDir)),
                   iRand);
      }

      half4 main(float2 p) {
        float2 lowRes = p / iSize;
        float2 cellCenter = (floor(lowRes) + 0.5) * iSize;
        float2 posInCell = fract(lowRes) * 2 - 1;

        float v = ramp(cellCenter) + progress;
        float distToCenter = max(abs(posInCell.x), abs(posInCell.y));

        return distToCenter > v ? imageA.eval(p).rgb1 : imageB.eval(p).rgb1;
      }
      """
    )
  }

  fun test_NOT_highlightingWithFlagOff() {
    StudioFlags.AGSL_LANGUAGE_SUPPORT.override(false)
    checkHighlighting(
      """
      void main() {
        const double scale = 2.0;
      }
      """
    )
  }

  fun testReservedKeywords() {
    checkHighlighting(
      """
      void main() {
        const <error descr="`double` is a reserved future keyword">double</error> scale = 2.0;
      }
      """
    )
  }

  fun testUnsupportedKeywords() {
    checkHighlighting(
      """
       void main() {
        <error descr="`discard` is not allowed in AGSL (Android Graphics Shading Language)">discard</error>
      }
      """
    )
  }

  fun testGlslBuiltins() {
    checkHighlighting(
      """
      in int <error descr="GLSL predefined variables (`gl_*`) are not allowed in AGSL (Android Graphics Shading Language)">gl_VertexID</error>;
      """
    )
  }

  private fun checkHighlighting(@Language("AGSL") code: String) {
    // Note that we don't have a file type for AGSL files; they're only supported
    // as strings nested in code. So in this unit test we'll place them inside
    // a Kotlin string.
     @Language("kotlin") val kotlin = """
      //language=AGSL
      val s = ""${'"'}
      ""${'"'}
     """.trimIndent()
    val index = kotlin.indexOf("\n\"\"\"") + 1
    assertFalse(
      "Don't include the surrounding Kotlin code in the AGSL snippet",
      code.startsWith(kotlin.substring(0, index))
    )
    val inserted = kotlin.substring(0, index) + code.trimIndent() + kotlin.substring(index)
    val file = myFixture.configureByText(KotlinFileType.INSTANCE, inserted)
    myFixture.testHighlighting(true, false, true, file.virtualFile)
  }
}