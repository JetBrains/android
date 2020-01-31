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
package com.android.tools.idea.lang.proguardR8

import com.android.tools.idea.lang.com.android.tools.idea.lang.proguardR8.ProguardR8TestCase
import com.android.tools.idea.testing.caret
import com.google.common.truth.Truth.assertThat

class ProguardR8CompletionContributorTest : ProguardR8TestCase() {

  fun testFlagCompletion() {

    myFixture.configureByText(ProguardR8FileType.INSTANCE, """
        -k$caret
    """.trimIndent())

    var keys = myFixture.completeBasic()

    assertThat(keys).isNotEmpty()
    assertThat(keys.map { it.lookupString }).contains("keepattributes")

    myFixture.configureByText(ProguardR8FileType.INSTANCE, """
        -$caret
    """.trimIndent())

    keys = myFixture.completeBasic()

    assertThat(keys).isNotEmpty()
    assertThat(keys.map { it.lookupString }).contains("allowaccessmodification")

    myFixture.configureByText(ProguardR8FileType.INSTANCE, """
        -keep class myClass {
          -k$caret
        }
    """.trimIndent())

    keys = myFixture.completeBasic()

    // No flag suggestions within JAVA specification
    assertThat(keys).isEmpty()
  }

  fun testClassTypeCompletion() {
    myFixture.configureByText(ProguardR8FileType.INSTANCE, """
        -koop $caret
    """.trimIndent())

    var keys = myFixture.completeBasic()

    // don't appear outside class specification header
    assertThat(keys).isEmpty()

    myFixture.configureByText(ProguardR8FileType.INSTANCE, """
        -keep $caret
    """.trimIndent())

    keys = myFixture.completeBasic()

    // after keep flags
    assertThat(keys).isNotEmpty()
    assertThat(keys.map { it.lookupString }.toList()).containsExactly("class", "interface", "enum")


    myFixture.configureByText(ProguardR8FileType.INSTANCE, """
        -if $caret
    """.trimIndent())

    keys = myFixture.completeBasic()

    // after if flag
    assertThat(keys).isNotEmpty()
    assertThat(keys.map { it.lookupString }.toList()).containsExactly("class", "interface", "enum")
  }


  fun testFieldMethodWildcardsCompletion() {
    myFixture.configureByText(ProguardR8FileType.INSTANCE, """
        <$caret
    """.trimIndent())

    var keys = myFixture.completeBasic()

    // Don't appear outside class specification body.
    assertThat(keys).isEmpty()
    assertThat(myFixture.editor.document.text).isEqualTo("<")

    myFixture.configureByText(ProguardR8FileType.INSTANCE, """
        -keep class * {
          <$caret
        }
    """.trimIndent())

    keys = myFixture.completeBasic()

    assertThat(keys).isNotEmpty()
    assertThat(keys.map { it.lookupString }.toList()).containsExactly("<fields>", "<init>", "<methods>", "<clinit>")
  }


  fun testFieldMethodModifiersCompletion() {
    myFixture.configureByText(ProguardR8FileType.INSTANCE, """
        pu$caret
    """.trimIndent())

    var keys = myFixture.completeBasic()

    // don't appear outside class specification body
    assertThat(keys).isEmpty()
    assertThat(myFixture.editor.document.text).isEqualTo("pu")

    myFixture.configureByText(ProguardR8FileType.INSTANCE, """
        -keep class * {
          $caret
        }
    """.trimIndent())

    keys = myFixture.completeBasic()

    // suggests at the start of new rule
    assertThat(keys).isNotEmpty()
    assertThat(keys.map { it.lookupString }.toList()).containsAllOf("public", "private", "protected",
                                                                    "static", "synchronized", "native", "abstract", "strictfp",
                                                                    "volatile", "transient", "final")

    myFixture.configureByText(ProguardR8FileType.INSTANCE, """
        -keep class * {
          public $caret
        }
    """.trimIndent())

    keys = myFixture.completeBasic()

    // suggests after another modifier
    assertThat(keys).isNotEmpty()
    assertThat(keys.map { it.lookupString }.toList()).containsAllOf("public", "private", "protected",
                                                                    "static", "synchronized", "native", "abstract", "strictfp",
                                                                    "volatile", "transient", "final")

    myFixture.configureByText(ProguardR8FileType.INSTANCE, """
        -keep class * {
          int $caret
        }
    """.trimIndent())

    keys = myFixture.completeBasic()

    // don't suggests after type
    assertThat(keys.map { it.lookupString }.toList()).containsNoneOf("public", "private", "protected",
                                                                     "static", "synchronized", "native", "abstract", "strictfp",
                                                                     "volatile", "transient", "final")
  }
}