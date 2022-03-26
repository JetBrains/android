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

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File
import java.io.InputStream
import java.util.zip.ZipInputStream

class BaselineProfilePrettyPrinterTest {
  @Test
  fun testPrettyPrint() {
    val path = "assets/dexopt/baseline.prof"
    val text = BaselineProfilePrettyPrinter.prettyPrint(getApkBytes(), File(path).toPath(), getApkBytes(path))

    val expected = """
      HSPLandroidx/startup/AppInitializer;-><clinit>()V
      HSPLandroidx/startup/AppInitializer;-><init>(Landroid/content/Context;)V
      HSPLandroidx/startup/AppInitializer;->discoverAndInitialize()V
      HSPLandroidx/startup/AppInitializer;->discoverAndInitialize(Landroid/os/Bundle;)V
      HSPLandroidx/startup/AppInitializer;->doInitialize(Ljava/lang/Class;)Ljava/lang/Object;
      HSPLandroidx/startup/AppInitializer;->doInitialize(Ljava/lang/Class;Ljava/util/Set;)Ljava/lang/Object;
      HSPLandroidx/startup/AppInitializer;->getInstance(Landroid/content/Context;)Landroidx/startup/AppInitializer;
      HSPLandroidx/startup/AppInitializer;->initializeComponent(Ljava/lang/Class;)Ljava/lang/Object;
      HSPLandroidx/startup/AppInitializer;->isEagerlyInitialized(Ljava/lang/Class;)Z
      HSPLandroidx/startup/AppInitializer;->setDelegate(Landroidx/startup/AppInitializer;)V
      Landroidx/startup/AppInitializer;
      """.trimIndent()
    assertEquals(expected, text.replace("\r\n", "\n").trim())
  }

  private fun getApkInputStream(): InputStream {
    //return BaselineProfilePrettyPrinterTest::class.java.getResourceAsStream("/app-release.apk")
    return BaselineProfilePrettyPrinterTest::class.java.getResourceAsStream("/app-release-unsigned.apk")
           ?: error("Could not find test data")
  }

  private fun getApkBytes(): ByteArray {
    val bytes: ByteArray = getApkInputStream().use { file ->
      file.readBytes()
    }
    return bytes
  }

  private fun getApkBytes(path: String): ByteArray {
    var contents: ByteArray? = null
    getApkInputStream().use { file ->
      ZipInputStream(file).use { zip ->
        var entry = zip.nextEntry
        while (entry != null) {
          if (entry.name == path) {
            contents = zip.readBytes()
            break
          }
          zip.closeEntry()
          entry = zip.nextEntry
        }
      }
    }
    return contents ?: error("Invalid app bundle file, entry \"$path\" not found")
  }
}