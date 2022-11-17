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
import com.android.tools.idea.gradle.project.sync.utils.ProjectJdkUtils
import com.android.tools.idea.sdk.IdeSdks
import com.android.tools.idea.testing.AgpVersionSoftwareEnvironmentDescriptor
import com.android.tools.idea.testing.FileSubject
import com.android.utils.FileUtils
import com.google.common.truth.Truth
import com.intellij.openapi.util.io.FileUtil
import org.jetbrains.android.AndroidTestBase
import java.io.File
import java.nio.file.Files

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
    newRoot2
      .resolve("settings.gradle")
      .replaceContent { "rootProject.name = 'gradle_project_name'\n$it" } // Give it a name not matching the directory name.
  }
  Files.createDirectory(ideaDirectory.toPath())

  val gradleRoots = mutableListOf(GradleRoot(newRoot.name))
  if (makeSecondCopy)
    gradleRoots.add(GradleRoot(newRoot2.name))
  gradleXml.writeText(ProjectIdeaConfigFilesUtils.buildGradleXmlConfig(gradleRoots))
  miscXml.writeText(ProjectIdeaConfigFilesUtils.buildMiscXmlConfig(testJdkName))
}

internal fun cloneProjectRootIntoMultipleGradleRoots(projectRoot: File, gradleRoots: List<GradleRoot>) {
  val tempDir = File(projectRoot.path + "_tmp")
  Files.move(projectRoot.toPath(), tempDir.toPath())
  gradleRoots
    .map { projectRoot.resolve(it.name) }
    .forEach {
      FileUtil.createDirectory(it)
      FileUtils.copyDirectory(tempDir, it)
    }
  tempDir.delete()

  ProjectJdkUtils.setProjectIdeaGradleJdk(projectRoot, gradleRoots)
}

internal fun patchMppProject(
  projectRoot: File,
  enableHierarchicalSupport: Boolean,
  convertAppToKmp: Boolean = false,
  addJvmTo: List<String> = emptyList(),
  addIntermediateTo: List<String> = emptyList(),
  addJsModule: Boolean = false
) {
  if (enableHierarchicalSupport) {
    projectRoot.resolve("gradle.properties").replaceInContent(
      "kotlin.mpp.hierarchicalStructureSupport=false",
      "kotlin.mpp.hierarchicalStructureSupport=true"
    )
  }
  if (convertAppToKmp) {
    projectRoot.resolve("app").resolve("build.gradle").replaceInContent(
      """
        plugins {
            id 'com.android.application'
            id 'kotlin-android'
        }
      """.trimIndent(),
      """
        plugins {
            id 'com.android.application'
            id 'kotlin-multiplatform'
        }
        kotlin {
            android()
        }
     """.trimIndent(),
    )
  }
  for (module in addJvmTo) {
    projectRoot.resolve(module).resolve("build.gradle").replaceInContent(
      "android()",
      "android()\njvm()"
    )
  }
  for (module in addIntermediateTo) {
    projectRoot.resolve(module).resolve("build.gradle").replaceInContent(
      """
        |sourceSets {
      """.trimMargin(),
      """
        |sourceSets {
        |  create("jvmAndAndroid") {
        |    dependsOn(commonMain)
        |    androidMain.dependsOn(it)
        |    jvmMain.dependsOn(it)
        |  }
      """.trimMargin()
    )
  }
  if (addJsModule) {
    projectRoot.resolve("settings.gradle")
      .replaceInContent("//include ':jsModule'", "include ':jsModule'")
    // "org.jetbrains.kotlin.js" conflicts with "clean" task.
    projectRoot.resolve("build.gradle")
      .replaceInContent("task clean(type: Delete)", "task clean1(type: Delete)")
  }
}

internal fun AgpVersionSoftwareEnvironmentDescriptor.updateProjectJdk(projectRoot: File) {
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

