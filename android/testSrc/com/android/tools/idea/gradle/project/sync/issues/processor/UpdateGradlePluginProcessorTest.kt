/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.gradle.project.sync.issues.processor

import com.android.tools.idea.testing.AndroidGradleTestCase
import com.android.tools.idea.testing.TestProjectPaths.NEW_SYNC_KOTLIN_TEST
import com.intellij.openapi.util.io.FileUtil.loadFile
import org.junit.Test

class UpdateGradlePluginProcessorTest : AndroidGradleTestCase() {
  @Test
  fun testPluginGetUpgradedCorrectly() {
    prepareProjectForImport(NEW_SYNC_KOTLIN_TEST)

    val gradleInfo = GradlePluginInfo("gradle", "com.android.tools.build")
    val kotlinInfo = GradlePluginInfo("kotlin-gradle-plugin", "org.jetbrains.kotlin")
    val processor = UpdateGradlePluginProcessor(project, mapOf(gradleInfo to "1.2.3", kotlinInfo to "2.3.4"))
    processor.run()

    val buildFileContents = loadFile(projectFolderPath.resolve("build.gradle"))
    assertTrue(buildFileContents.contains("com.android.tools.build:gradle:1.2.3"))
    assertTrue(buildFileContents.contains("org.jetbrains.kotlin:kotlin-gradle-plugin:${'$'}kotlin_version"))
    assertTrue(buildFileContents.contains("ext.kotlin_version = '2.3.4'"))
  }
}