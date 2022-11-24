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

import com.android.SdkConstants
import com.android.tools.apk.analyzer.internal.ApkArchive
import com.android.tools.apk.analyzer.internal.AppBundleArchive
import com.intellij.testFramework.BinaryLightVirtualFile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.io.InputStream
import java.nio.file.Path
import java.util.zip.ZipInputStream

class BaselineProfilePrettyPrinterTest {
  @Test
  fun prettyPrintAPK() {
    val rulesFromApp = getRulesFromApp("/app-benchmark.apk", ApkArchive.APK_BASELINE_PROFILE_PATH)
    val expectedRules = getExpectedRules()
    assertEquals(expectedRules, rulesFromApp)
  }

  @Test
  fun prettyPrintTruncated() {
    // Regression test for https://issuetracker.google.com/260118216

    val basefile = BinaryLightVirtualFile("app-benchmark.apk", getApkBytes("/app-benchmark.apk"))
    val prof = getApkBytes("/app-benchmark.apk", ApkArchive.APK_BASELINE_PROFILE_PATH)
    // Full text is 779 chars pretty printed. We're passing in a much smaller limit than the normal one
    // to simulate contents too large in the test without having a massive file.
    val text = ApkEditor.getPrettyPrintedBaseline(basefile, prof, Path.of(SdkConstants.FN_BINARY_ART_PROFILE), 600)
        ?.replace("\r\n", "\n") ?: ""

    // The middle of the text contains a file which varies from run to run (it's a temp file copy of the
    // full contents), so just assert the contents before and after the path:

    assertTrue(text.startsWith(
      """
      The contents of this baseline file is too large to show by default.
      You can increase the maximum buffer size by setting the property
          idea.max.content.load.filesize=779
      (or higher)

      Alternatively, the full contents have been written to the following
      temp file:
      """.trimIndent()
    ))
    assertTrue(text.endsWith(
      """
      .txt

      HSPLandroidx/profileinstaller/ProfileInstallerInitializer;-><init>()V
      HSPLandroidx/startup/InitializationProvider;-><init>()V
      HSPLandroidx/startup/Ini
      ....truncated 629 characters.
      """.trimIndent()
    ))
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