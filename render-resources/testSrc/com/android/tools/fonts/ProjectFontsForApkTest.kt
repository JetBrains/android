/*
 * Copyright (C) 2024 The Android Open Source Project
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

import com.android.test.testutils.TestUtils
import com.android.tools.res.SingleRepoResourceRepositoryManager
import com.android.tools.res.apk.ApkResourceRepository
import com.android.tools.res.apk.TEST_DATA_DIR
import com.android.tools.res.ids.apk.ApkResourceIdManager
import com.android.tools.res.ids.resolver
import org.junit.Assert.assertEquals
import org.junit.Test

class ProjectFontsForApkTest {
  @Test
  fun testApkXmlFont() {
    val path = TestUtils.resolveWorkspacePath(TEST_DATA_DIR + "apk-all-resources.ap_")
    val idManager = ApkResourceIdManager().apply { this.loadApkResources(path.toString()) }
    val apkRes = ApkResourceRepository(path.toString()) { idManager.findById(it) }

    val manager = SingleRepoResourceRepositoryManager(apkRes)
    val fontService = object : DownloadableFontCacheServiceImpl(FontDownloader.NOOP_FONT_DOWNLOADER, { null }) { }

    val projectFonts = ProjectFonts(fontService, manager, idManager.resolver)

    val fontFamily = projectFonts.getFont("@font/aclonica")

    assertEquals("aclonica", fontFamily.name)
    assertEquals("Google Fonts", fontFamily.provider.name)
  }
}