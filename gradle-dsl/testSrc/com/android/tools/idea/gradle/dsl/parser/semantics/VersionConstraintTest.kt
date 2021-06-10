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

import com.google.common.truth.Expect
import org.junit.Rule
import org.junit.Test

class VersionConstraintTest {
  @get:Rule
  val expect: Expect = Expect.createAndEnableStackTrace()

  @Test
  fun testVersionConstraintEquality() {
    val versions = listOf("3.0.0", "4.0.0", "4.1.0-alpha01", "4.1.0-alpha02", "4.1.0-beta01", "4.1.0-rc01", "4.1.0", "4.1.1")
    versions.forEach { v1 ->
      val vc1 = VersionConstraint.agpFrom(v1)
      val vc1b = VersionConstraint.agpFrom(AndroidGradlePluginVersion.parse(v1))
      expect.that(vc1 == vc1b).isTrue()
      expect.that(vc1.hashCode() == vc1b.hashCode()).isTrue()
      versions.forEach { v2 ->
        val vc2 = VersionConstraint.agpFrom(v2)
        if (v1 == v2) {
          expect.that(vc1 == vc2).isTrue()
          expect.that(vc1.hashCode() == vc2.hashCode()).isTrue()
        }
        else {
          expect.that(vc1 == vc2).isFalse()
        }
      }
      versions.forEach { v3 ->
        val vc3 = VersionConstraint.agpBefore(v3)
        val vc3b = VersionConstraint.agpBefore(AndroidGradlePluginVersion.parse(v3))
        expect.that(vc3 == vc3b).isTrue()
        expect.that(vc3.hashCode() == vc3b.hashCode()).isTrue()
        expect.that(vc1 == vc3).isFalse()
      }
    }
  }

  @Test
  fun testVersionConstraintAgpFrom() {
    val versions = listOf("3.0.0", "4.0.0", "4.1.0-alpha01", "4.1.0-alpha02", "4.1.0-beta01", "4.1.0-rc01", "4.1.0", "4.1.1")
    versions.forEachIndexed { i, v1 ->
      val vc = VersionConstraint.agpFrom(v1)
      versions.forEachIndexed { j, v2 ->
        val v = AndroidGradlePluginVersion.parse(v2)
        if (i > j) {
          expect.that(vc.isOkWith(v)).isFalse()
        }
        else {
          expect.that(vc.isOkWith(v)).isTrue()
        }
      }
      expect.that(vc.isOkWith(null)).isTrue()
    }
  }

  @Test
  fun testVersionConstraintAgpBefore() {
    val versions = listOf("3.0.0", "4.0.0", "4.1.0-alpha01", "4.1.0-alpha02", "4.1.0-beta01", "4.1.0-rc01", "4.1.0", "4.1.1")
    versions.forEachIndexed { i, v1 ->
      val vc = VersionConstraint.agpBefore(v1)
      versions.forEachIndexed { j, v2 ->
        val v = AndroidGradlePluginVersion.parse(v2)
        if (i <= j) {
          expect.that(vc.isOkWith(v)).isFalse()
        }
        else {
          expect.that(vc.isOkWith(v)).isTrue()
        }
      }
      expect.that(vc.isOkWith(null)).isFalse()
    }
  }
}