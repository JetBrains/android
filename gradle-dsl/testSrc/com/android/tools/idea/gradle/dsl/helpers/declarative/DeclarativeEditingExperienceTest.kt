/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.tools.idea.gradle.dsl.helpers.declarative

import com.intellij.testFramework.fixtures.BasePlatformTestCase

class DeclarativeEditingExperienceTest : BasePlatformTestCase() {
  fun testDoubleQuotes() {
    myFixture.configureByText("build.gradle.dcl", """
    androidApplication {
        namespace = <caret>
    }
    """.trimIndent())

    myFixture.type('"')

    myFixture.checkResult("""
        androidApplication {
            namespace = "<caret>"
        }
    """.trimIndent())
  }

  fun testDoubleQuotes2() {
    myFixture.configureByText("build.gradle.dcl", """
    androidApplication {
        namespace = "<caret>"
    }
    """.trimIndent())

    myFixture.type('"')

    myFixture.checkResult("""
        androidApplication {
            namespace = ""<caret>
        }
    """.trimIndent())
  }

  fun testTripleQuotes() {
    myFixture.configureByText("build.gradle.dcl", """
    androidApplication {
        namespace = ""<caret>
    }
    """.trimIndent())

    myFixture.type('"')
    val tripleQuotes = "\"\"\""
    myFixture.checkResult("""
        androidApplication {
            namespace = $tripleQuotes<caret>$tripleQuotes
        }
    """.trimIndent())
  }

  fun testTripleQuotes2() {
    val tripleQuotes = "\"\"\""
    myFixture.configureByText("build.gradle.dcl", """
    androidApplication {
        namespace = $tripleQuotes<caret>$tripleQuotes
    }
    """.trimIndent())

    myFixture.type('"')
    myFixture.checkResult("""
        androidApplication {
            namespace = $tripleQuotes"<caret>""
        }
    """.trimIndent())

    myFixture.type('"')
    myFixture.checkResult("""
        androidApplication {
            namespace = $tripleQuotes""<caret>"
        }
    """.trimIndent())
    myFixture.type('"')
    myFixture.checkResult("""
        androidApplication {
            namespace = $tripleQuotes$tripleQuotes<caret>
        }
    """.trimIndent())
  }

  fun testBlockIntentOnEnter() {
    myFixture.configureByText("build.gradle.dcl", """
    androidApplication {<caret>
    }
    """.trimIndent())

    myFixture.type('\n')

    myFixture.checkResult("""
    androidApplication {
        <caret>
    }
    """.trimIndent())
  }
}