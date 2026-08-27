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
package com.android.tools.idea.gradle.project.sync.snapshots

import com.android.SdkConstants
import com.android.tools.idea.gradle.project.sync.model.GradleRoot
import com.android.tools.idea.gradle.project.sync.utils.ProjectIdeaConfigFilesUtils
import com.android.tools.idea.sdk.IdeSdks
import com.android.tools.idea.testing.FileSubject
import com.android.utils.FileUtils
import com.google.common.truth.Truth
import com.intellij.openapi.util.io.FileUtil
import java.io.File
import java.nio.file.Files
import org.jetbrains.android.AndroidTestBase

internal fun truncateForV2(settingsFile: File) {
  val patchedText = settingsFile.readLines().takeWhile { !it.contains("//-v2:truncate-from-here") }.joinToString("\n")
  Truth.assertThat(patchedText.trim()).isNotEqualTo(settingsFile.readText().trim())
  settingsFile.writeText(patchedText)
}

internal fun moveGradleRootUnderGradleProjectDirectory(root: File, makeSecondCopy: Boolean = false) {
  val testJdkName = IdeSdks.getInstance().jdk?.name ?: error("No JDK in test")
  val newRoot = root.resolve(if (makeSecondCopy) "gradle_project_1" else "gradle_project")
  val newRoot2 = root.resolve("gradle_project_2")
  val tempRoot = File(root.path + "_tmp")
  val ideaDirectory = root.resolve(".idea")
  val gradleXml = ideaDirectory.resolve("gradle.xml")
  val miscXml = ideaDirectory.resolve("misc.xml")
  Files.move(root.toPath(), tempRoot.toPath())
  Files.createDirectory(root.toPath())
  Files.move(tempRoot.toPath(), newRoot.toPath())
  if (makeSecondCopy) {
    FileUtils.copyDirectory(newRoot, newRoot2)
    newRoot2.resolve("settings.gradle").replaceContent {
      "rootProject.name = 'gradle_project_name'\n$it"
    } // Give it a name not matching the directory name.
  }
  Files.createDirectory(ideaDirectory.toPath())

  val gradleRoots = mutableListOf(GradleRoot(newRoot.name))
  if (makeSecondCopy) gradleRoots.add(GradleRoot(newRoot2.name))
  gradleXml.writeText(ProjectIdeaConfigFilesUtils.buildGradleXmlConfig(gradleRoots))
  miscXml.writeText(ProjectIdeaConfigFilesUtils.buildMiscXmlConfig(testJdkName))
}

internal fun cloneProjectRootIntoMultipleGradleRoots(
  projectRoot: File,
  gradleRoots: List<GradleRoot>,
  configGradleRoot: (File, GradleRoot) -> Unit,
  configProjectRoot: () -> Unit,
) {
  val tempDir = File(projectRoot.path + "_tmp")
  Files.move(projectRoot.toPath(), tempDir.toPath())
  gradleRoots.forEach {
    val gradleRootFile = projectRoot.resolve(it.name)
    FileUtil.createDirectory(gradleRootFile)
    FileUtils.copyDirectory(tempDir, gradleRootFile)
    configGradleRoot(gradleRootFile, it)
  }
  configProjectRoot()
  tempDir.delete()
}

internal fun patchMppProject(
  projectRoot: File,
  convertAppToKmp: Boolean = false,
  addJvmTo: List<String> = emptyList(),
  addIosTo: List<String> = emptyList(),
  addIntermediateTo: List<String> = emptyList(),
  addJsModule: Boolean = false,
) {
  if (convertAppToKmp) {
    projectRoot.resolve("app").resolve("build.gradle").replaceContent { content ->
      """
      plugins {
          id("org.jetbrains.kotlin.multiplatform")
          id("com.android.kotlin.multiplatform.library")
      }

      kotlin {
          android {
              namespace = "com.example.android.kotlin"
              compileSdk = 26
              minSdk = 15
              withHostTestBuilder {}.configure {}
              withDeviceTestBuilder {}.configure {}
          }

          sourceSets {
              named("androidMain") {
                  kotlin.srcDir("src/main/java")
              }
              named("androidHostTest") {
                  kotlin.srcDir("src/test/java")
                  dependencies {
                      implementation("junit:junit:4.12")
                  }
              }
              named("androidDeviceTest") {
                  kotlin.srcDir("src/androidTest/java")
                  dependencies {
                      implementation("com.android.support.test:runner:1.0.2")
                  }
              }
              named("commonMain") {
                  dependencies {
                      implementation(project(":module2"))
                  }
              }
          }
      }
      """
        .trimIndent()
    }
  }
  for (module in addJvmTo) {
    projectRoot.resolve(module).resolve("build.gradle").replaceContent { content ->
      if (content.contains("androidTarget()")) {
        content.replace("androidTarget()", "androidTarget()\njvm()")
      } else if (content.contains("androidLibrary {")) {
        content.replace("androidLibrary {", "jvm()\n  androidLibrary {")
      } else if (content.contains("android {")) {
        content.replace("android {", "jvm()\n  android {")
      } else {
        content
      }
    }
  }
  for (module in addIosTo) {
    projectRoot.resolve(module).resolve("build.gradle").replaceContent { content ->
      if (content.contains("androidTarget()")) {
        content.replace("androidTarget()", "androidTarget()\niosX64()\niosSimulatorArm64()\niosArm64()")
      } else if (content.contains("androidLibrary {")) {
        content.replace("androidLibrary {", "iosX64()\niosSimulatorArm64()\niosArm64()\n  androidLibrary {")
      } else if (content.contains("android {")) {
        content.replace("android {", "iosX64()\niosSimulatorArm64()\niosArm64()\n  android {")
      } else {
        content
      }
    }
  }
  if (addIosTo.isNotEmpty()) {
    val konanDir = File(FileUtil.getTempDirectory(), ".konan")
    konanDir.mkdirs()
    projectRoot.resolve("gradle.properties").appendText("\nkonan.data.dir=${konanDir.path}\nkotlin.native.version=2.4.20-RC2\n")
  }
  for (module in addIntermediateTo) {
    projectRoot.resolve(module).resolve("build.gradle").replaceContent { content ->
      content.replace(
        "sourceSets {",
        """
        sourceSets {
          create("jvmAndAndroid") {
            dependsOn(commonMain)
            androidMain.dependsOn(it)
            jvmMain.dependsOn(it)
          }
        """
          .trimIndent(),
      )
    }
  }
  if (addJsModule) {
    projectRoot.resolve("settings.gradle").replaceInContent("//include ':jsModule'", "include ':jsModule'")
    // "org.jetbrains.kotlin.js" conflicts with "clean" task.
    projectRoot.resolve("build.gradle").replaceInContent("task clean(type: Delete)", "task clean1(type: Delete)")
  }
}

internal fun updateProjectJdk(projectRoot: File) {
  val jdk = IdeSdks.getInstance().jdk ?: error("${SyncedProjectTest::class} requires a valid JDK")
  val miscXml = projectRoot.resolve(".idea").resolve("misc.xml")
  miscXml.writeText(miscXml.readText().replace("""project-jdk-name="1.8"""", """project-jdk-name="${jdk.name}""""))
}

internal fun createEmptyGradleSettingsFile(projectRootPath: File) {
  val settingsFilePath = File(projectRootPath, SdkConstants.FN_SETTINGS_GRADLE)
  Truth.assertThat(FileUtil.delete(settingsFilePath)).isTrue()
  FileUtils.writeToFile(settingsFilePath, " ")
  Truth.assertAbout(FileSubject.file()).that(settingsFilePath).isFile()
  AndroidTestBase.refreshProjectFiles()
}
