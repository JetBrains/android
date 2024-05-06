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
package com.android.tools.idea.gradle.util

import com.android.ide.common.build.GenericBuiltArtifactsLoader
import com.android.tools.idea.log.LogWrapper
import com.android.utils.FileUtils.writeToFile
import com.intellij.openapi.diagnostic.Logger
import com.intellij.testFramework.rules.TempDirectory
import junit.framework.TestCase.assertEquals
import org.intellij.lang.annotations.Language
import org.junit.Rule
import org.junit.Test
import java.io.File

/**
 * Tests for GradleBuildOutputUtil.kt
 */
class GradleBuildOutputUtilTest {
  @get:Rule
  val tempDir = TempDirectory()

  @Language("JSON")
  val singleAPKOutputFileText = """
{
  "version": 1,
  "artifactType": {
    "type": "APK",
    "kind": "Directory"
  },
  "applicationId": "com.example.myapplication",
  "variantName": "debug",
  "elements": [
    {
      "type": "SINGLE",
      "filters": [],
      "properties": [],
      "versionCode": 1,
      "versionName": "1",
      "outputFile": "app-debug.apk"
    }
  ]
}"""

  @Language("JSON")
  val multiAPKsOutputFileText = """
{
  "version": 1,
  "artifactType": {
    "type": "APK",
    "kind": "RegularFile"
  },
  "applicationId": "com.example.myapplication",
  "variantName": "debug",
  "elements": [
    {
      "type": "ONE_OF_MANY",
      "filters": [
        {
          "filterType": "ABI",
          "value": "x86"
        }
      ],
      "properties": [],
      "versionCode": 1,
      "versionName": "1",
      "outputFile": "app-x86-debug.apk"
    },
    {
      "type": "ONE_OF_MANY",
      "filters": [
        {
          "filterType": "ABI",
          "value": "x86"
        },
        {
          "filterType": "DENSITY",
          "value": "hdpi"
        }
      ],
      "properties": [],
      "versionCode": 1,
      "versionName": "1",
      "outputFile": "app-hdpiX86-debug.apk"
    }
  ]
}"""

  @Test
  fun getPathFromOutputListingFileWithSingleApk() {
    val outputFile = tempDir.newFile("output.json")
    writeToFile(outputFile, singleAPKOutputFileText)
    val expectedFile = File(tempDir.root, "app-debug.apk")
    assertEquals(listOf(expectedFile), getOutputFilesFromListingFile(outputFile.path))
  }

  @Test
  fun getPathFromOutputListingFileWithMultiApks() {
    val outputFile = tempDir.newFile("output.json")
    writeToFile(outputFile, multiAPKsOutputFileText)
    val expectedFile1 = File(tempDir.root, "app-x86-debug.apk")
    val expectedFile2 = File(tempDir.root, "app-hdpiX86-debug.apk")
    assertEquals(listOf(expectedFile1, expectedFile2), getOutputFilesFromListingFile(outputFile.path))
  }

  @Test
  fun getApplicationIdFromOutputListingFile() {
    val outputFile = tempDir.newFile("output.json")
    writeToFile(outputFile, multiAPKsOutputFileText)

    val logger = LogWrapper(Logger.getInstance(GradleBuildOutputUtilTest::class.java))
    val artifact = GenericBuiltArtifactsLoader.loadFromFile(File(outputFile.path), logger)!!
    assertEquals("com.example.myapplication", artifact.applicationId)
    assertEquals(null, artifact.minSdkVersionForDexing)
    assertEquals(null, artifact.baselineProfiles)
  }
}