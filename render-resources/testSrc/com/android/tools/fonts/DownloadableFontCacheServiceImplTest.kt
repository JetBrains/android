/*
 * Copyright (C) 2026 The Android Open Source Project
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
package com.android.tools.fonts

import com.android.ide.common.fonts.FontFamily
import com.android.ide.common.fonts.FontProvider
import com.android.ide.common.fonts.FontSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadableFontCacheServiceImplTest {

  @Test
  fun testGetRelativeCachedMenuFile_Normal() {
    val provider = FontProvider("Google Fonts", "com.google.android.gms.fonts", "", "https://fonts.gstatic.com/s/a/directory.xml", "", "")
    val family =
      FontFamily(provider, FontSource.DOWNLOADABLE, "Aladin", "https://fonts.gstatic.com/s/aladin/v8/menu.xml", "Aladin", emptyList())

    val service = object : DownloadableFontCacheServiceImpl(FontDownloader.NOOP_FONT_DOWNLOADER, { null }) {}
    val file = service.getRelativeCachedMenuFile(family)

    assertNotNull(file)
    assertEquals("com.google.android.gms.fonts/fonts/aladin/v8/menu.xml", file)
  }

  @Test
  fun testGetRelativeCachedMenuFile_Traversal() {
    val provider = FontProvider("Google Fonts", "com.google.android.gms.fonts", "", "https://fonts.gstatic.com/s/a/directory.xml", "", "")

    // Malicious menu URL attempting to traverse
    val maliciousMenu =
      "https://fonts.gstatic.com/s/aladin/v8/../../../../../../../../../../Users/v/AppData/Roaming/Microsoft/Windows/Start Menu/Programs/Startup/x.bat"
    val family = FontFamily(provider, FontSource.DOWNLOADABLE, "Aladin", maliciousMenu, "Aladin", emptyList())

    val service = object : DownloadableFontCacheServiceImpl(FontDownloader.NOOP_FONT_DOWNLOADER, { null }) {}
    val file = service.getRelativeCachedMenuFile(family)

    assertNotNull(file)
    // It should not contain ".." or go outside the expected directory structure.
    assertTrue("Path should not contain ..: $file", !file!!.contains(".."))
    assertTrue("Path should not contain \\: $file", !file.contains("\\"))
  }

  @Test
  fun testGetRelativeCachedMenuFile_DirectTraversal() {
    val provider = FontProvider("Google Fonts", "com.google.android.gms.fonts", "", "https://fonts.gstatic.com/s/a/directory.xml", "", "")

    // Malicious menu URL with ".." in the last 3 segments
    val maliciousMenu = "https://fonts.gstatic.com/s/aladin/../x.bat"
    val family = FontFamily(provider, FontSource.DOWNLOADABLE, "Aladin", maliciousMenu, "Aladin", emptyList())

    val service = object : DownloadableFontCacheServiceImpl(FontDownloader.NOOP_FONT_DOWNLOADER, { null }) {}
    val file = service.getRelativeCachedMenuFile(family)

    assertNotNull(file)
    assertTrue("Path should not contain ..: $file", !file!!.contains(".."))
    assertEquals("com.google.android.gms.fonts/fonts/aladin/v1/x.bat", file)
  }

  @Test
  fun testGetRelativeCachedMenuFile_EndWithTraversal() {
    val provider = FontProvider("Google Fonts", "com.google.android.gms.fonts", "", "https://fonts.gstatic.com/s/a/directory.xml", "", "")

    // Malicious menu URL ending with ".."
    val maliciousMenu = "https://fonts.gstatic.com/s/aladin/v8/.."
    val family = FontFamily(provider, FontSource.DOWNLOADABLE, "Aladin", maliciousMenu, "Aladin", emptyList())

    val service = object : DownloadableFontCacheServiceImpl(FontDownloader.NOOP_FONT_DOWNLOADER, { null }) {}
    val file = service.getRelativeCachedMenuFile(family)

    assertNotNull(file)
    assertTrue("Path should not contain ..: $file", !file!!.contains(".."))
    assertEquals("com.google.android.gms.fonts/fonts/aladin/v8/font", file)
  }

  @Test
  fun testGetRelativeCachedMenuFile_BackslashTraversal() {
    val provider = FontProvider("Google Fonts", "com.google.android.gms.fonts", "", "https://fonts.gstatic.com/s/a/directory.xml", "", "")

    // Malicious menu URL with backslash traversal segment
    val maliciousMenu = "https://fonts.gstatic.com/s/aladin/v8/..\\..\\x.bat"
    val family = FontFamily(provider, FontSource.DOWNLOADABLE, "Aladin", maliciousMenu, "Aladin", emptyList())

    val service = object : DownloadableFontCacheServiceImpl(FontDownloader.NOOP_FONT_DOWNLOADER, { null }) {}
    val file = service.getRelativeCachedMenuFile(family)

    assertNotNull(file)
    assertTrue("Path should not contain ..: $file", !file!!.contains(".."))
    assertTrue("Path should not contain \\: $file", !file.contains("\\"))
    assertEquals("com.google.android.gms.fonts/fonts/aladin/v8/font", file)
  }
}
