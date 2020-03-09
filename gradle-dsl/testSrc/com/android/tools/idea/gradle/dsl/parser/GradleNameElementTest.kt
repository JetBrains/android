/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.gradle.dsl.parser

import com.android.tools.idea.gradle.dsl.parser.elements.GradleNameElement
import com.google.common.truth.Truth.assertThat
import com.intellij.testFramework.UsefulTestCase

// These tests check that we have correctly implemented the isomorphism between a list of unescaped strings and a single string with
// a delimiter to separate suitably-escaped parts.
class GradleNameElementTest : UsefulTestCase() {
  fun testSplitNoDots() {
    assertThat(GradleNameElement.split("abcde")).isEqualTo(listOf("abcde"))
  }

  fun testSplitDot() {
    assertThat(GradleNameElement.split("ab.de")).isEqualTo(listOf("ab", "de"))
  }

  fun testSplitMultipleDots() {
    assertThat(GradleNameElement.split("ab.de.gh")).isEqualTo(listOf("ab", "de", "gh"))
  }

  fun testSplitMultipleConsecutiveDots() {
    assertThat(GradleNameElement.split("ab..ef")).isEqualTo(listOf("ab", "", "ef"))
  }

  fun testSplitDotAtBeginning() {
    assertThat(GradleNameElement.split(".bcdef")).isEqualTo(listOf("", "bcdef"))
  }

  fun testSplitDotAtEnd() {
    assertThat(GradleNameElement.split("abcde.")).isEqualTo(listOf("abcde", ""))
  }

  fun testSplitEscapedDot() {
    assertThat(GradleNameElement.split("ab\\.de")).isEqualTo(listOf("ab.de"))
  }

  fun testSplitEscapedBackslash() {
    assertThat(GradleNameElement.split("ab\\\\de")).isEqualTo(listOf("ab\\de"))
  }

  fun testSplitExoticCharacters() {
    assertThat(GradleNameElement.split("ab \t \n é Å £ € \u1234")).isEqualTo(listOf("ab \t \n é Å £ € \u1234"))
  }

  fun testJoinNoDots() {
    assertThat(GradleNameElement.join(listOf("abcde"))).isEqualTo("abcde")
  }

  fun testJoinDot() {
    assertThat(GradleNameElement.join(listOf("ab", "de"))).isEqualTo("ab.de")
  }

  fun testJoinMultipleDots() {
    assertThat(GradleNameElement.join(listOf("ab", "de", "gh"))).isEqualTo("ab.de.gh")
  }

  fun testJoinMultipleConsecutiveDots() {
    assertThat(GradleNameElement.join(listOf("ab", "", "ef"))).isEqualTo("ab..ef")
  }

  fun testJoinDotAtBeginning() {
    assertThat(GradleNameElement.join(listOf("", "bcdef"))).isEqualTo(".bcdef")
  }

  fun testJoinDotAtEnd() {
    assertThat(GradleNameElement.join(listOf("abcde", ""))).isEqualTo("abcde.")
  }

  fun testJoinEscapedDot() {
    assertThat(GradleNameElement.join(listOf("ab.de"))).isEqualTo("ab\\.de")
  }

  fun testJoinEscapedBackslash() {
    assertThat(GradleNameElement.join(listOf("ab\\de"))).isEqualTo("ab\\\\de")
  }

  fun testJoinExoticCharacters() {
    assertThat(GradleNameElement.join(listOf("ab \t \n é Å £ € \u1234"))).isEqualTo("ab \t \n é Å £ € \u1234")
  }
}