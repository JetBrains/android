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
package com.android.tools.idea.apk.viewer

import com.android.tools.apk.analyzer.internal.ApkArchive
import com.android.tools.apk.analyzer.internal.AppBundleArchive
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File
import java.io.InputStream
import java.util.zip.ZipInputStream

class BaselineProfilePrettyPrinterTest {
  @Test
  fun prettyPrintAPK() {
    val rulesFromApp = getRulesFromApp("/app-benchmark.apk", ApkArchive.APK_BASELINE_PROFILE_PATH)
    val expectedRules = getExpectedRules()
    assertEquals(expectedRules, rulesFromApp)
  }

  @Test
  fun prettyPrintAAB() {
    val rulesFromApp = getRulesFromApp("/app-benchmark.aab", AppBundleArchive.BUNDLE_BASELINE_PROFILE_PATH)
    val expectedRules = getExpectedRules()
    assertEquals(expectedRules, rulesFromApp)
  }

  private fun getRulesFromApp(fileName: String, profilePath: String): String {
    val rules = BaselineProfilePrettyPrinter.prettyPrint(
      getApkBytes(fileName),
      File(profilePath).toPath(),
      getApkBytes(fileName, profilePath)
    )

    return rules.replace("\r\n", "\n").trim()
  }

  private fun getExpectedRules(): String {
    val stream = BaselineProfilePrettyPrinterTest::class.java.getResourceAsStream("/expected-rules.txt") ?: error(
      "Could not find test data")
    return stream.use { String(it.readBytes()) }
  }

  private fun getApkInputStream(fileName: String): InputStream {
    return BaselineProfilePrettyPrinterTest::class.java.getResourceAsStream(fileName) ?: error("Could not find test data")
  }

  private fun getApkBytes(fileName: String): ByteArray {
    val bytes: ByteArray = getApkInputStream(fileName).use { file ->
      file.readBytes()
    }
    return bytes
  }

  private fun getApkBytes(fileName: String, path: String): ByteArray {
    // this requires relative path
    val pathRelative = path.removePrefix("/")

    var contents: ByteArray? = null
    getApkInputStream(fileName).use { file ->
      ZipInputStream(file).use { zip ->
        var entry = zip.nextEntry
        while (entry != null) {
          if (entry.name == pathRelative) {
            contents = zip.readBytes()
            break
          }
          zip.closeEntry()
          entry = zip.nextEntry
        }
      }
    }
    return contents ?: error("Invalid app bundle file, entry \"$pathRelative\" not found")
  }
}