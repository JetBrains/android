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
package com.android.tools.idea.gradle.project.build.output

import com.google.common.truth.Truth.assertThat
import com.intellij.build.events.MessageEvent
import com.intellij.pom.java.LanguageLevel
import org.junit.Test

class JavaLanguageLevelDeprecationOutputParserTest {

  private val parser = JavaLanguageLevelDeprecationOutputParser()

  private val lineAfterWarning = "line after the warning"

  @Test
  fun testUnrelatedLineNotParsed() {
    val reader = TestBuildOutputInstantReader(
      lines = listOf(
        "warning: someOtherWarning",
        lineAfterWarning
      ),
      parentEventId = "[root build id] > [Task :app:compileDebugAndroidTestJavaWithJavac]"
    )

    assertThat(parser.parseLines(reader.readLine()!!, reader)).isNull()
    assertThat(reader.readLine()).isEqualTo(lineAfterWarning)
  }
  @Test
  fun testWarning() {
    val reader = TestBuildOutputInstantReader(
      lines = listOf(
        "warning: [options] source value 8 is obsolete and will be removed in a future release",
        "warning: [options] target value 8 is obsolete and will be removed in a future release",
        "warning: [options] To suppress warnings about obsolete options, use -Xlint:-options.",
        lineAfterWarning
      ),
      parentEventId = "[root build id] > [Task :app:compileDebugAndroidTestJavaWithJavac]"
    )

    assertThat(parser.parseLines(reader.readLine()!!, reader)).isEqualTo(JavaLanguageLevelDeprecationOutputParser.ParsingResult(
      kind = MessageEvent.Kind.WARNING,
      title = "Java compiler has deprecated support for compiling with source/target compatibility version 8.",
      suggestedToolchainVersion = 17,
      suggestedLanguageLevel = LanguageLevel.JDK_11,
      modulePath = ":app"
    ))
    assertThat(reader.readLine()).isEqualTo(lineAfterWarning)
  }

  @Test
  fun testWarningWithDifferentVersions() {
    // This seems to be unreal for now but maybe they will deprecate several versions at once in the future.
    val reader = TestBuildOutputInstantReader(
      lines = listOf(
        "warning: [options] source value 7 is obsolete and will be removed in a future release",
        "warning: [options] target value 8 is obsolete and will be removed in a future release",
        "warning: [options] To suppress warnings about obsolete options, use -Xlint:-options.",
        lineAfterWarning
      ),
      parentEventId = "[root build id] > [Task :app:compileDebugAndroidTestJavaWithJavac]"
    )

    assertThat(parser.parseLines(reader.readLine()!!, reader)).isEqualTo(JavaLanguageLevelDeprecationOutputParser.ParsingResult(
      kind = MessageEvent.Kind.WARNING,
      title = "Java compiler has deprecated support for compiling with source/target compatibility version 7.",
      suggestedToolchainVersion = 11,
      suggestedLanguageLevel = LanguageLevel.JDK_11,
      modulePath = ":app"
    ))
    assertThat(reader.readLine()).isEqualTo(lineAfterWarning)
  }

  @Test
  fun testWarningSingleLine() {
    val reader = TestBuildOutputInstantReader(
      lines = listOf(
        "warning: [options] source value 8 is obsolete and will be removed in a future release",
        "warning: [options] To suppress warnings about obsolete options, use -Xlint:-options.",
        lineAfterWarning
      ),
      parentEventId = "[root build id] > [Task :app:compileDebugAndroidTestJavaWithJavac]"
    )

    assertThat(parser.parseLines(reader.readLine()!!, reader)).isEqualTo(JavaLanguageLevelDeprecationOutputParser.ParsingResult(
      kind = MessageEvent.Kind.WARNING,
      title = "Java compiler has deprecated support for compiling with source/target compatibility version 8.",
      suggestedToolchainVersion = 17,
      suggestedLanguageLevel = LanguageLevel.JDK_11,
      modulePath = ":app"
    ))
    assertThat(reader.readLine()).isEqualTo(lineAfterWarning)
  }

  @Test
  fun testError() {
    val reader = TestBuildOutputInstantReader(
      lines = listOf(
        "error: Source option 7 is no longer supported. Use 8 or later.",
        "error: Target option 7 is no longer supported. Use 8 or later.",
        lineAfterWarning
      ),
      parentEventId = "[root build id] > [Task :app:compileDebugAndroidTestJavaWithJavac]"
    )
    assertThat(parser.parseLines(reader.readLine()!!, reader)).isEqualTo(JavaLanguageLevelDeprecationOutputParser.ParsingResult(
      kind = MessageEvent.Kind.ERROR,
      title = "Java compiler has removed support for compiling with source/target compatibility version 7.",
      suggestedToolchainVersion = 11,
      suggestedLanguageLevel = LanguageLevel.JDK_1_8,
      modulePath = ":app"
    ))
    assertThat(reader.readLine()).isEqualTo(lineAfterWarning)
  }

  @Test
  fun testOnlySourceError() {
    val reader = TestBuildOutputInstantReader(
      lines = listOf(
        "error: Source option 7 is no longer supported. Use 8 or later.",
        lineAfterWarning
      ),
      parentEventId = "[root build id] > [Task :app:compileDebugAndroidTestJavaWithJavac]"
    )
    assertThat(parser.parseLines(reader.readLine()!!, reader)).isEqualTo(JavaLanguageLevelDeprecationOutputParser.ParsingResult(
      kind = MessageEvent.Kind.ERROR,
      title = "Java compiler has removed support for compiling with source/target compatibility version 7.",
      suggestedToolchainVersion = 11,
      suggestedLanguageLevel = LanguageLevel.JDK_1_8,
      modulePath = ":app"
    ))
    assertThat(reader.readLine()).isEqualTo(lineAfterWarning)
  }

  //TODO is it possible to have two warnings for different versions?
  @Test
  fun testErrorWithDifferentVersions() {
    val reader = TestBuildOutputInstantReader(
      lines = listOf(
        "error: Source option 7 is no longer supported. Use 11 or later.",
        "error: Target option 8 is no longer supported. Use 11 or later.",
        lineAfterWarning
      ),
      parentEventId = "[root build id] > [Task :app:compileDebugAndroidTestJavaWithJavac]"
    )
    assertThat(parser.parseLines(reader.readLine()!!, reader)).isEqualTo(JavaLanguageLevelDeprecationOutputParser.ParsingResult(
      kind = MessageEvent.Kind.ERROR,
      title = "Java compiler has removed support for compiling with source/target compatibility version 7.",
      suggestedToolchainVersion = 11,
      suggestedLanguageLevel = LanguageLevel.JDK_11,
      modulePath = ":app"
    ))
    assertThat(reader.readLine()).isEqualTo(lineAfterWarning)
  }

  @Test
  fun testErrorWithDifferentOlderVersions() {
    val reader = TestBuildOutputInstantReader(
      lines = listOf(
        "error: Source option 5 is no longer supported. Use 11 or later.",
        "error: Target option 6 is no longer supported. Use 11 or later.",
        lineAfterWarning
      ),
      parentEventId = "[root build id] > [Task :app:compileDebugAndroidTestJavaWithJavac]"
    )
    assertThat(parser.parseLines(reader.readLine()!!, reader)).isEqualTo(JavaLanguageLevelDeprecationOutputParser.ParsingResult(
      kind = MessageEvent.Kind.ERROR,
      title = "Java compiler has removed support for compiling with source/target compatibility version 5.",
      suggestedToolchainVersion = null,
      suggestedLanguageLevel = LanguageLevel.JDK_11,
      modulePath = ":app"
    ))
    assertThat(reader.readLine()).isEqualTo(lineAfterWarning)
  }

  @Test
  fun testMixedErrorAndWarning() {
    val reader = TestBuildOutputInstantReader(
      lines = listOf(
        "error: Source option 7 is no longer supported. Use 8 or later.",
        "warning: [options] target value 8 is obsolete and will be removed in a future release",
        "warning: [options] To suppress warnings about obsolete options, use -Xlint:-options.",
        lineAfterWarning
      ),
      parentEventId = "[root build id] > [Task :app:compileDebugAndroidTestJavaWithJavac]"
    )
    assertThat(parser.parseLines(reader.readLine()!!, reader)).isEqualTo(JavaLanguageLevelDeprecationOutputParser.ParsingResult(
      kind = MessageEvent.Kind.ERROR,
      title = "Java compiler has removed support for compiling with source/target compatibility version 7.",
      suggestedToolchainVersion = 11,
      suggestedLanguageLevel = LanguageLevel.JDK_11,
      modulePath = ":app"
    ))
    assertThat(reader.readLine()).isEqualTo(lineAfterWarning)
  }
}