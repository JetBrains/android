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
package com.android.tools.idea.gradle.dsl.parser.semantics

import com.android.tools.idea.gradle.dsl.parser.semantics.AndroidGradlePluginVersion.PreviewKind.ALPHA
import com.android.tools.idea.gradle.dsl.parser.semantics.AndroidGradlePluginVersion.PreviewKind.BETA
import com.android.tools.idea.gradle.dsl.parser.semantics.AndroidGradlePluginVersion.PreviewKind.DEV
import com.android.tools.idea.gradle.dsl.parser.semantics.AndroidGradlePluginVersion.PreviewKind.NONE
import com.android.tools.idea.gradle.dsl.parser.semantics.AndroidGradlePluginVersion.PreviewKind.RC
import com.google.common.truth.Expect
import org.junit.Rule
import org.junit.Test
import kotlin.math.sign

class AndroidGradlePluginVersionTest {
  @get:Rule
  val expect: Expect = Expect.createAndEnableStackTrace()

  private val valid = mapOf(
    "1.2.3" to AndroidGradlePluginVersion(1, 2, 3, NONE, null),
    "0.1.2" to AndroidGradlePluginVersion(0, 1, 2, NONE, null),
    "1.0.2" to AndroidGradlePluginVersion(1, 0, 2, NONE, null),
    "1.2.0" to AndroidGradlePluginVersion(1, 2, 0, NONE, null),
    "11.0.0" to AndroidGradlePluginVersion(11, 0, 0, NONE, null),
    "0.12.0" to AndroidGradlePluginVersion(0, 12, 0, NONE, null),
    "0.0.13" to AndroidGradlePluginVersion(0, 0, 13, NONE, null),
    "1.2.3-alpha01" to AndroidGradlePluginVersion(1, 2, 3, ALPHA, 1),
    "1.2.3-beta10" to AndroidGradlePluginVersion(1, 2, 3, BETA, 10),
    "1.2.3-rc99" to AndroidGradlePluginVersion(1, 2, 3, RC, 99),
    "1.2.3-dev" to AndroidGradlePluginVersion(1, 2, 3, DEV, null),
  )
  private val invalid = listOf(
    "", "0", "1", "1.2", "01.2.3", "1.01.3", "3.0.00", "1.2.3.4", "3.0.0-", "3.0.0.",
    "1.2.3-dev01", "1.2.3-dev0", "1.2.3-dev1", "1.2.3-dev01", "1.2.3-dev.",
    "1.2.3-alpha", "1.2.3-alpha0", "1.2.3-alpha100", "1.2.3-alpha01.",
    "1.2.3-beta", "1.2.3-beta0", "1.2.3-beta100", "1.2.3-beta10.",
    "1.2.3-rc", "1.2.3-rc0", "1.2.3-rc100", "1.2.3-rc99.",
    "a", "a.b", "a.b.c", "1.2.a", "1.a.2", "a.1.2",
    "1.2.3-a", "1.2.3-a01", "1.2.3-b", "1.2.3-b01", "1.2.3-c", "1.2.3-c01", "1.2.3-d", "1.2.3-d01", "1.2.3-SNAPSHOT",
    "1_1.2.3", "1.2_2.3", "1.2.3_3", "1.2.3-alpha0_1", "1.2.3-beta1_0", "1.2.3-rc9_9",
    // non-ASCII digits (non-exhaustive)
    "\u0660.\u0661.\u0662", "\u06f0.\u06f1.\u06f2", "\u07c0.\u07c1.\u07c2", "\u0966.\u0967.\u0968",
    "\u09e6.\u09e7.\u09e8", "\u0a66.\u0a67.\u0a68", "\u0ae6.\u0ae7.\u0ae8", "\u0b66.\u0b67.\u0b68",
    "\u0be6.\u0be7.\u0be8", "\u0c66.\u0c67.\u0c68", "\u0ce6.\u0ce7.\u0ce8", "\u0d66.\u0d67.\u0d68",
    "\u0de6.\u0de7.\u0de8", "\u0e50.\u0e51.\u0e52", "\u0ed0.\u0ed1.\u0ed2", "\u0f20.\u0f21.\u0f22",
  )
  private val ordered =
    listOf("0.1.2", "0.1.3", "0.2.1", "1.0.0-alpha03", "1.0.0-beta02", "1.0.0-rc01", "1.0.0-dev",
           "1.0.0", "1.0.1-alpha01", "1.1.0", "1.2.0-beta01", "1.3.0", "1.3.999", "2.0.0-rc01")

  @Test
  fun testValidParse() {
    valid.forEach { (string, expected) ->
      AndroidGradlePluginVersion.tryParse(string).run {
        expect.that(this).isInstanceOf(AndroidGradlePluginVersion::class.java)
        // if `this` is null the above expectation will have failed.
        this?.run {
          expect.that(major).isEqualTo(expected.major)
          expect.that(minor).isEqualTo(expected.minor)
          expect.that(micro).isEqualTo(expected.micro)
          expect.that(previewKind).isEqualTo(expected.previewKind)
          expect.that(preview).isEqualTo(expected.preview)
        }
      }
      AndroidGradlePluginVersion.parse(string).run {
        expect.that(this).isInstanceOf(AndroidGradlePluginVersion::class.java)
        expect.that(major).isEqualTo(expected.major)
        expect.that(minor).isEqualTo(expected.minor)
        expect.that(micro).isEqualTo(expected.micro)
        expect.that(previewKind).isEqualTo(expected.previewKind)
        expect.that(preview).isEqualTo(expected.preview)
      }
    }
  }

  @Test
  fun testInvalidParse() {
    invalid.forEach {
      expect.that(AndroidGradlePluginVersion.tryParse(it)).isNull()
      expect.that(try { AndroidGradlePluginVersion.parse(it) } catch(e: java.lang.IllegalArgumentException) { e })
        .isInstanceOf(java.lang.IllegalArgumentException::class.java)
    }
  }

  @Test
  fun testEquality() {
    valid.forEach { (string, _) ->
      val v1 = AndroidGradlePluginVersion.parse(string)
      val v2 = AndroidGradlePluginVersion.parse(string)
      expect.that(v1).isEqualTo(v2)
      expect.that(v1).isEquivalentAccordingToCompareTo(v2)
      expect.that(v1.hashCode()).isEqualTo(v2.hashCode())
    }
  }

  @Test
  fun testComparison() {
    ordered.map { AndroidGradlePluginVersion.parse(it) }.forEachIndexed { i1, v1 ->
      ordered.map { AndroidGradlePluginVersion.parse(it) }.forEachIndexed { i2, v2 ->
        expect.that(compareValues(v1, v2).sign).isEqualTo(compareValues(i1, i2).sign)
      }
    }
  }

  @Test
  fun testToString() {
    valid.forEach { (string, expected) ->
      expect.that(expected.toString()).isEqualTo(string)
    }
  }
}