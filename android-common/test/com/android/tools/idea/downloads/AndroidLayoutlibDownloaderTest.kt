// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.android.download

import com.android.tools.idea.downloads.AndroidLayoutlibDownloader
import com.intellij.testFramework.ApplicationRule
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertTrue

class AndroidLayoutlibDownloaderTest {
  @get:Rule
  val appRule = ApplicationRule()

  @Test
  fun downloadWorks() {
    val downloader = AndroidLayoutlibDownloader.getInstance()
    if (downloader.pluginDir.exists()) {
      downloader.pluginDir.deleteRecursively()
    }

    assertTrue(downloader.makeSureComponentIsInPlace(), "download failed")
    assertTrue(downloader.pluginDir.exists(), "must exist: ${downloader.pluginDir}")

    val targetDir = downloader.getHostDir("plugins/android/resources/layoutlib/")
    val buildProp = targetDir.resolve("build.prop")
    assertTrue(buildProp.exists(), "must exist: $buildProp")
  }
}