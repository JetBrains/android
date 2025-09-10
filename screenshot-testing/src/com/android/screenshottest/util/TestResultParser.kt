/*
 * Copyright (C) 2025 The Android Open Source Project
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
package com.android.screenshottest.util

import com.android.screenshottest.ScreenshotTestBuildSystemAdapter
import com.google.common.annotations.VisibleForTesting
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.module.Module
import java.io.File
import java.io.InputStream
import javax.xml.parsers.DocumentBuilder
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element

/**
 * Parses JUnit XML test results to find generated screenshot image paths.
 */
object TestResultParser {
  private val LOG = Logger.getInstance(TestResultParser::class.java)

  /**
   * Parses the JUnit XML results to find the absolute paths of all generated screenshot images.
   *
   * @return A map where the key is a unique test identifier (e.g., "MyTest.method[param]")
   *   and the value is the path to the new image.
   */
  fun parse(module: Module): Map<String, String> {
    val imagePaths = mutableMapOf<String, String>()
    try {
      val projectSystem = ScreenshotTestBuildSystemAdapter.EP_NAME.extensionList.firstOrNull() ?: return emptyMap()
      val taskName = projectSystem.getScreenshotTestTaskName(module, "validate") ?: return emptyMap()
      val modulePathStr = projectSystem.getLinkedExternalProjectPath(module) ?: return emptyMap()

      val testResultsDir = File(modulePathStr)
        .resolve("build/test-results/$taskName")

      if (!testResultsDir.exists() || !testResultsDir.isDirectory) {
        LOG.warn("Test results directory not found: ${testResultsDir.path}")
        return emptyMap()
      }

      // Create the builder once for efficiency and reuse it for all files.
      val dbFactory = DocumentBuilderFactory.newInstance()
      val dBuilder = dbFactory.newDocumentBuilder()

      testResultsDir.walk().filter { it.isFile && it.extension == "xml" }.forEach { file ->
        try {
          file.inputStream().use { inputStream ->
            imagePaths.putAll(parseXmlStream(inputStream, dBuilder))
          }
        } catch (e: Exception) {
          LOG.warn("Could not parse test result file: ${file.path}", e)
        }
      }
    } catch (e: Exception) {
      LOG.error("Error parsing screenshot test results", e)
    }
    return imagePaths
  }

  /**
   * Parses a single JUnit XML stream. This is an internal function for testability.
   */
  @VisibleForTesting
  fun parseXmlStream(inputStream: InputStream, dBuilder: DocumentBuilder): Map<String, String> {
    val imagePaths = mutableMapOf<String, String>()
    val doc = dBuilder.parse(inputStream)
    doc.documentElement.normalize()
    val testcases = doc.getElementsByTagName("testcase")
    for (i in 0 until testcases.length) {
      val testcaseNode = testcases.item(i) as Element
      val className = testcaseNode.getAttribute("classname")
      val simpleClassName = className.substringAfterLast('.')
      val testName = testcaseNode.getAttribute("name")
      val testId = "$simpleClassName.$testName"

      val propertiesNodeList = testcaseNode.getElementsByTagName("properties")
      if (propertiesNodeList.length > 0) {
        val propertiesNode = propertiesNodeList.item(0) as Element
        val propertyNodeList = propertiesNode.getElementsByTagName("property")
        for (j in 0 until propertyNodeList.length) {
          val propertyNode = propertyNodeList.item(j) as Element
          if (propertyNode.getAttribute("name") == "PreviewScreenshot.newImagePath") {
            val newImagePath = propertyNode.getAttribute("value")
            if (!newImagePath.isNullOrEmpty()) {
              imagePaths[testId] = newImagePath
              LOG.info("Found image for test '$testId': $newImagePath")
            }
            break
          }
        }
      }
    }
    return imagePaths
  }
}