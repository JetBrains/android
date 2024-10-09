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

import com.intellij.build.events.MessageEvent
import org.junit.Test

class JavaLanguageLevelDeprecationOutputParserTest : BuildOutputParserTest() {

  @Test
  fun testWarning() {
    parseOutput(
      parentEventId = "[root build id] > [Task :app:compileDebugAndroidTestJavaWithJavac]",
      gradleOutput = """
        warning: [options] source value 8 is obsolete and will be removed in a future release
        warning: [options] target value 8 is obsolete and will be removed in a future release
        warning: [options] To suppress warnings about obsolete options, use -Xlint:-options.
      """.trimIndent(),
      expectedEvents = listOf(ExpectedEvent(
        message = "Java compiler has deprecated support for compiling with source/target compatibility version 8.",
        isFileMessageEvent = false,
        isBuildIssueEvent = true,
        isDuplicateMessageAware = false,
        group = "Build Issues",
        kind= MessageEvent.Kind.WARNING,
        parentId = "[root build id] > [Task :app:compileDebugAndroidTestJavaWithJavac]",
        description = """
        Java compiler has deprecated support for compiling with source/target compatibility version 8.

        <a href="set.java.toolchain.17">Set Java Toolchain to 17</a>
        <a href="set.java.level.JDK_11.all">Change Java language level and jvmTarget to 11 in all modules if using a lower level.</a>
        <a href="PickLanguageLevelInPSD">Pick a different compatibility level...</a>
        <a href="open.gradle.jdk.settings">Pick a different JDK to run Gradle...</a>
        <a href="OpenBuildJdkInfoLinkQuickFix">More information...</a>
        """.trimIndent()))
    )
  }

  @Test
  fun testWarningWithOtherExtraWarning() {
    parseOutput(
      parentEventId = "[root build id] > [Task :app:compileDebugAndroidTestJavaWithJavac]",
      gradleOutput = """
        warning: [options] source value 8 is obsolete and will be removed in a future release
        warning: [options] target value 8 is obsolete and will be removed in a future release
        warning: [options] To suppress warnings about obsolete options, use -Xlint:-options.
        warning: someOtherWarning
      """.trimIndent(),
      expectedEvents = listOf(
        ExpectedEvent(
          message = "Java compiler has deprecated support for compiling with source/target compatibility version 8.",
          isFileMessageEvent = false,
          isBuildIssueEvent = true,
          isDuplicateMessageAware = false,
          group = "Build Issues",
          kind= MessageEvent.Kind.WARNING,
          parentId = "[root build id] > [Task :app:compileDebugAndroidTestJavaWithJavac]",
          description = """
          Java compiler has deprecated support for compiling with source/target compatibility version 8.

          <a href="set.java.toolchain.17">Set Java Toolchain to 17</a>
          <a href="set.java.level.JDK_11.all">Change Java language level and jvmTarget to 11 in all modules if using a lower level.</a>
          <a href="PickLanguageLevelInPSD">Pick a different compatibility level...</a>
          <a href="open.gradle.jdk.settings">Pick a different JDK to run Gradle...</a>
          <a href="OpenBuildJdkInfoLinkQuickFix">More information...</a>
          """.trimIndent()
        ),
        ExpectedEvent(
          message = "someOtherWarning",
          isFileMessageEvent = false,
          isBuildIssueEvent = false,
          isDuplicateMessageAware = false,
          group = "Android Gradle Plugin",
          kind= MessageEvent.Kind.WARNING,
          parentId = "[root build id] > [Task :app:compileDebugAndroidTestJavaWithJavac]",
          description = """
          someOtherWarning
          """.trimIndent()
        ))
    )
  }

  @Test
  fun testWarningWithDifferentVersions() {
    // This seems to be unreal for now but maybe they will deprecate several versions at once in the future.
    parseOutput(
      parentEventId = "[root build id] > [Task :app:compileDebugAndroidTestJavaWithJavac]",
      gradleOutput = """
        warning: [options] source value 7 is obsolete and will be removed in a future release
        warning: [options] target value 8 is obsolete and will be removed in a future release
        warning: [options] To suppress warnings about obsolete options, use -Xlint:-options.
        line after the warning
      """.trimIndent(),
      expectedEvents = listOf(ExpectedEvent(
        message = "Java compiler has deprecated support for compiling with source/target compatibility version 7.",
        isFileMessageEvent = false,
        isBuildIssueEvent = true,
        isDuplicateMessageAware = false,
        group = "Build Issues",
        kind= MessageEvent.Kind.WARNING,
        parentId = "[root build id] > [Task :app:compileDebugAndroidTestJavaWithJavac]",
        description = """
        Java compiler has deprecated support for compiling with source/target compatibility version 7.

        <a href="set.java.toolchain.11">Set Java Toolchain to 11</a>
        <a href="set.java.level.JDK_11.all">Change Java language level and jvmTarget to 11 in all modules if using a lower level.</a>
        <a href="PickLanguageLevelInPSD">Pick a different compatibility level...</a>
        <a href="open.gradle.jdk.settings">Pick a different JDK to run Gradle...</a>
        <a href="OpenBuildJdkInfoLinkQuickFix">More information...</a>
        """.trimIndent()))
    )
  }

  @Test
  fun testWarningSingleLine() {
    parseOutput(
      parentEventId = "[root build id] > [Task :app:compileDebugAndroidTestJavaWithJavac]",
      gradleOutput = """
        warning: [options] source value 8 is obsolete and will be removed in a future release
        warning: [options] To suppress warnings about obsolete options, use -Xlint:-options.
        line after the warning
      """.trimIndent(),
      expectedEvents = listOf(ExpectedEvent(
        message = "Java compiler has deprecated support for compiling with source/target compatibility version 8.",
        isFileMessageEvent = false,
        isBuildIssueEvent = true,
        isDuplicateMessageAware = false,
        group = "Build Issues",
        kind= MessageEvent.Kind.WARNING,
        parentId = "[root build id] > [Task :app:compileDebugAndroidTestJavaWithJavac]",
        description = """
        Java compiler has deprecated support for compiling with source/target compatibility version 8.

        <a href="set.java.toolchain.17">Set Java Toolchain to 17</a>
        <a href="set.java.level.JDK_11.all">Change Java language level and jvmTarget to 11 in all modules if using a lower level.</a>
        <a href="PickLanguageLevelInPSD">Pick a different compatibility level...</a>
        <a href="open.gradle.jdk.settings">Pick a different JDK to run Gradle...</a>
        <a href="OpenBuildJdkInfoLinkQuickFix">More information...</a>
        """.trimIndent()))
    )
  }

  @Test
  fun testError() {
    parseOutput(
      parentEventId = "[root build id] > [Task :app:compileDebugAndroidTestJavaWithJavac]",
      gradleOutput = """
      error: Source option 7 is no longer supported. Use 8 or later.
      error: Target option 7 is no longer supported. Use 8 or later.
      line after the warning
    """.trimIndent(),
      expectedEvents = listOf(ExpectedEvent(
      message = "Java compiler has removed support for compiling with source/target compatibility version 7.",
      isFileMessageEvent = false,
      isBuildIssueEvent = true,
      isDuplicateMessageAware = false,
      group = "Build Issues",
      kind= MessageEvent.Kind.ERROR,
      parentId = "[root build id] > [Task :app:compileDebugAndroidTestJavaWithJavac]",
      description = """
      Java compiler has removed support for compiling with source/target compatibility version 7.

      <a href="set.java.toolchain.11">Set Java Toolchain to 11</a>
      <a href="set.java.level.JDK_1_8.all">Change Java language level and jvmTarget to 8 in all modules if using a lower level.</a>
      <a href="PickLanguageLevelInPSD">Pick a different compatibility level...</a>
      <a href="open.gradle.jdk.settings">Pick a different JDK to run Gradle...</a>
      <a href="OpenBuildJdkInfoLinkQuickFix">More information...</a>
      """.trimIndent()))
    )
  }

  @Test
  fun testOnlySourceError() {
    parseOutput(
      parentEventId = "[root build id] > [Task :app:compileDebugAndroidTestJavaWithJavac]",
      gradleOutput = """
      error: Source option 7 is no longer supported. Use 8 or later.
      line after the warning
      """.trimIndent(),
      expectedEvents = listOf(ExpectedEvent(
      message = "Java compiler has removed support for compiling with source/target compatibility version 7.",
      isFileMessageEvent = false,
      isBuildIssueEvent = true,
      isDuplicateMessageAware = false,
      group = "Build Issues",
      kind= MessageEvent.Kind.ERROR,
      parentId = "[root build id] > [Task :app:compileDebugAndroidTestJavaWithJavac]",
      description = """
      Java compiler has removed support for compiling with source/target compatibility version 7.

      <a href="set.java.toolchain.11">Set Java Toolchain to 11</a>
      <a href="set.java.level.JDK_1_8.all">Change Java language level and jvmTarget to 8 in all modules if using a lower level.</a>
      <a href="PickLanguageLevelInPSD">Pick a different compatibility level...</a>
      <a href="open.gradle.jdk.settings">Pick a different JDK to run Gradle...</a>
      <a href="OpenBuildJdkInfoLinkQuickFix">More information...</a>
      """.trimIndent()))
    )
  }

  @Test
  fun testErrorWithDifferentVersions() {
    parseOutput(
      parentEventId = "[root build id] > [Task :app:compileDebugAndroidTestJavaWithJavac]",
      gradleOutput = """
      error: Source option 7 is no longer supported. Use 11 or later.
      error: Target option 8 is no longer supported. Use 11 or later.
      line after the warning
    """.trimIndent(),
      expectedEvents = listOf(ExpectedEvent(
      message = "Java compiler has removed support for compiling with source/target compatibility version 7.",
      isFileMessageEvent = false,
      isBuildIssueEvent = true,
      isDuplicateMessageAware = false,
      group = "Build Issues",
      kind= MessageEvent.Kind.ERROR,
      parentId = "[root build id] > [Task :app:compileDebugAndroidTestJavaWithJavac]",
      description = """
      Java compiler has removed support for compiling with source/target compatibility version 7.

      <a href="set.java.toolchain.11">Set Java Toolchain to 11</a>
      <a href="set.java.level.JDK_11.all">Change Java language level and jvmTarget to 11 in all modules if using a lower level.</a>
      <a href="PickLanguageLevelInPSD">Pick a different compatibility level...</a>
      <a href="open.gradle.jdk.settings">Pick a different JDK to run Gradle...</a>
      <a href="OpenBuildJdkInfoLinkQuickFix">More information...</a>
      """.trimIndent()))
    )
  }

  @Test
  fun testErrorWithDifferentOlderVersions() {
    parseOutput(
      parentEventId = "[root build id] > [Task :app:compileDebugAndroidTestJavaWithJavac]",
      gradleOutput = """
      error: Source option 5 is no longer supported. Use 11 or later.
      error: Target option 6 is no longer supported. Use 11 or later.
      line after the warning
    """.trimIndent(),
      expectedEvents = listOf(ExpectedEvent(
      message = "Java compiler has removed support for compiling with source/target compatibility version 5.",
      isFileMessageEvent = false,
      isBuildIssueEvent = true,
      isDuplicateMessageAware = false,
      group = "Build Issues",
      kind= MessageEvent.Kind.ERROR,
      parentId = "[root build id] > [Task :app:compileDebugAndroidTestJavaWithJavac]",
      description = """
      Java compiler has removed support for compiling with source/target compatibility version 5.

      <a href="set.java.level.JDK_11.all">Change Java language level and jvmTarget to 11 in all modules if using a lower level.</a>
      <a href="PickLanguageLevelInPSD">Pick a different compatibility level...</a>
      <a href="open.gradle.jdk.settings">Pick a different JDK to run Gradle...</a>
      <a href="OpenBuildJdkInfoLinkQuickFix">More information...</a>
      """.trimIndent()))
    )
  }

  @Test
  fun testMixedErrorAndWarning() {
    parseOutput(
      parentEventId = "[root build id] > [Task :app:compileDebugAndroidTestJavaWithJavac]",
      gradleOutput = """
        error: Source option 7 is no longer supported. Use 8 or later.
        warning: [options] target value 8 is obsolete and will be removed in a future release
        warning: [options] To suppress warnings about obsolete options, use -Xlint:-options.
        line after the warning
      """.trimIndent(),
      expectedEvents = listOf(ExpectedEvent(
        message = "Java compiler has removed support for compiling with source/target compatibility version 7.",
        isFileMessageEvent = false,
        isBuildIssueEvent = true,
        isDuplicateMessageAware = false,
        group = "Build Issues",
        kind= MessageEvent.Kind.ERROR,
        parentId = "[root build id] > [Task :app:compileDebugAndroidTestJavaWithJavac]",
        description = """
        Java compiler has removed support for compiling with source/target compatibility version 7.

        <a href="set.java.toolchain.11">Set Java Toolchain to 11</a>
        <a href="set.java.level.JDK_11.all">Change Java language level and jvmTarget to 11 in all modules if using a lower level.</a>
        <a href="PickLanguageLevelInPSD">Pick a different compatibility level...</a>
        <a href="open.gradle.jdk.settings">Pick a different JDK to run Gradle...</a>
        <a href="OpenBuildJdkInfoLinkQuickFix">More information...</a>
        """.trimIndent()))
    )
  }
}