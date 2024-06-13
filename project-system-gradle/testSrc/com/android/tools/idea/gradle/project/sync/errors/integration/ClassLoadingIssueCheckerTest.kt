/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.tools.idea.gradle.project.sync.errors.integration

import com.android.SdkConstants
import com.android.tools.idea.gradle.project.sync.errors.StopGradleDaemonQuickFix
import com.android.tools.idea.gradle.project.sync.quickFixes.SyncProjectRefreshingDependenciesQuickFix
import com.android.tools.idea.gradle.project.sync.snapshots.AndroidCoreTestProject
import com.android.tools.idea.gradle.project.sync.snapshots.PreparedTestProject
import com.android.tools.idea.gradle.project.sync.snapshots.TestProjectDefinition.Companion.prepareTestProject
import com.google.wireless.android.sdk.stats.AndroidStudioEvent
import com.intellij.build.issue.BuildIssue
import com.intellij.openapi.application.ApplicationManager
import org.junit.Test

class ClassLoadingIssueCheckerTest : AbstractIssueCheckerIntegrationTest() {

  @Test
  fun testCheckIssueWhenMethodNotFound() {
    val preparedProject = projectRule.prepareTestProject(AndroidCoreTestProject.SIMPLE_APPLICATION)

    preparedProject.setUpCustomPluginInBuildSrcWithCodeInApply( "new org.example.LibClass().method();")

    /*
    Additional setup required to trigger `java.lang.NoSuchMethodError`.
    This error is a result of incompatible classes used during compilation and in runtime, so we need to emulate this.
    Create two libs as subprojects, both containing same class of different versions.
    Plugin depends on lib1 in compilation and calls a method from it.
    In runtime it depends on lib2 where this method is absent and error is generated on execution.
     */
    preparedProject.root.resolve("buildSrc").let { buildSrc ->
      buildSrc.resolve("lib1/src/main/java/org/example/").mkdirs()
      buildSrc.resolve("lib2/src/main/java/org/example/").mkdirs()
      buildSrc.resolve("lib1/build.gradle").writeText("apply plugin: 'java'")
      buildSrc.resolve("lib1/src/main/java/org/example/LibClass.java").writeText("""
        package org.example;

        public class LibClass {
            public void method() {
              System.out.println("Hello from buildSrc LibClass!");
            }
        }
      """.trimIndent())

      buildSrc.resolve("lib2/build.gradle").writeText("apply plugin: 'java'")
      buildSrc.resolve("lib2/src/main/java/org/example/LibClass.java").writeText("""
        package org.example;

        public class LibClass {}
      """.trimIndent())
      buildSrc.resolve(SdkConstants.FN_SETTINGS_GRADLE).writeText("include ':lib1', ':lib2'")
      buildSrc.resolve(SdkConstants.FN_BUILD_GRADLE).appendText("""
        // Line needed to ensure line break
        dependencies {
            compileOnly project(':lib1')
            runtimeOnly project(':lib2')
        }
      """.trimIndent())
    }

    runSyncAndCheckBuildIssueFailure(preparedProject, ::verifyBuildIssue, AndroidStudioEvent.GradleSyncFailure.METHOD_NOT_FOUND)
  }

  //TODO(b/292231180): also add a separate test for 'cannot cast' in groovy code, it produces a different error and is not caught by any checker.
  @Test
  fun testCheckIssueWhenClassCannotBeCast() {
    val preparedProject = projectRule.prepareTestProject(AndroidCoreTestProject.SIMPLE_APPLICATION)

    preparedProject.setUpCustomPluginInBuildSrcWithCodeInApply( "Date a = (Date) (Object)\"123\";")

    runSyncAndCheckBuildIssueFailure(preparedProject, ::verifyBuildIssue, AndroidStudioEvent.GradleSyncFailure.CANNOT_BE_CAST_TO)
  }

  @Test
  fun testCheckIssueWhenClassNotFound() {
    val preparedProject = projectRule.prepareTestProject(AndroidCoreTestProject.SIMPLE_APPLICATION)

    preparedProject.setUpCustomPluginInBuildSrcWithCodeInApply(
      "try { Class.forName(\"not.existing.MyClass\"); } catch (ClassNotFoundException e) { throw new RuntimeException(e); }"
    )

    runSyncAndCheckBuildIssueFailure(preparedProject, ::verifyBuildIssue, AndroidStudioEvent.GradleSyncFailure.CLASS_NOT_FOUND)
  }

  private fun verifyBuildIssue(buildIssue: BuildIssue) {
    val message = buildIssue.description
    expect.that(message).contains("Gradle's dependency cache may be corrupt")
    expect.that(message).contains("Re-download dependencies and sync project")
    expect.that(message)
      .contains("In the case of corrupt Gradle processes, you can also try closing the IDE and then killing all Java processes.")

    val restartCapable = ApplicationManager.getApplication().isRestartCapable
    val quickFixText = if (restartCapable) "Stop Gradle build processes (requires restart)" else "Open Gradle Daemon documentation"
    expect.that(message).contains(quickFixText)

    // Verify QuickFixes.
    val quickFixes = buildIssue.quickFixes
    expect.that(quickFixes).hasSize(2)
    expect.that(quickFixes[0]).isInstanceOf(SyncProjectRefreshingDependenciesQuickFix::class.java)
    expect.that(quickFixes[1]).isInstanceOf(StopGradleDaemonQuickFix::class.java)
  }

  private fun PreparedTestProject.setUpCustomPluginInBuildSrcWithCodeInApply(applyCodeLine: String) {
    root.resolve("buildSrc").let { buildSrc ->
      buildSrc.mkdir()
      buildSrc.resolve(SdkConstants.FN_SETTINGS_GRADLE).writeText("")
      buildSrc.resolve(SdkConstants.FN_BUILD_GRADLE).writeText("""
        plugins {
            id 'java-gradle-plugin'
        }

        repositories {
            mavenCentral()
        }

        gradlePlugin {
            plugins {
                simplePlugin {
                    id = 'org.example.testPlugin'
                    implementationClass = 'org.example.TestPlugin'
                }
            }
        }
      """.trimIndent())
      buildSrc.resolve("src/main/java/org/example").mkdirs()
      buildSrc.resolve("src/main/java/org/example/TestPlugin.java").writeText("""
        package org.example;

        import java.util.Date;
        import org.gradle.api.Plugin;
        import org.gradle.api.Project;

        public class TestPlugin implements Plugin<Project> {
          public void apply(Project project) {
            $applyCodeLine
          }

        }
      """.trimIndent())
    }

    val buildFile = root.resolve("app").resolve(SdkConstants.FN_BUILD_GRADLE)
    buildFile.appendText("\napply plugin: 'org.example.testPlugin'")
  }
}