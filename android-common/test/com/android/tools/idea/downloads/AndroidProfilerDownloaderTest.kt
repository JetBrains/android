// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.android.tools.idea.downloads

import com.intellij.testFramework.ApplicationRule
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertTrue

class AndroidProfilerDownloaderTest {
  @get:Rule
  val appRule = ApplicationRule()

  @Test
  fun downloadWorks() {
    val downloader = AndroidProfilerDownloader.getInstance()
    if (downloader.pluginDir.exists()) {
      downloader.pluginDir.deleteRecursively()
    }

    assertTrue(downloader.makeSureComponentIsInPlace(), "download failed")
    assertTrue(downloader.pluginDir.exists(), "must exist: ${downloader.pluginDir}")

    val targetDir = downloader.getHostDir("plugins/android/resources/installer")
    val targetSample = targetDir.resolve("x86_64/installer")
    assertTrue(targetSample.exists(), "must exist: $targetSample")
  }
}