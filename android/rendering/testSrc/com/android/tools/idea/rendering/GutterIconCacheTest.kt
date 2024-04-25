/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.rendering

import com.android.tools.idea.io.TestFileUtils
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.util.androidFacet
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.editor.Document
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.vfs.VirtualFile
import icons.StudioIcons.Common.ANDROID_HEAD
import icons.StudioIcons.Common.WARNING
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import java.nio.file.FileSystems
import java.nio.file.Path
import javax.swing.Icon

@RunWith(JUnit4::class)
class GutterIconCacheTest {
  @get:Rule val projectRule = AndroidProjectRule.inMemory()

  private val facet by lazy { checkNotNull(projectRule.module.androidFacet) }

  private lateinit var cache: GutterIconCache
  private lateinit var sampleSvgPath: Path
  private lateinit var sampleSvgFile: VirtualFile
  private var icon: Icon? = null
  private var highDpiDisplay = false

  @Before
  fun setUp() {
    cache = GutterIconCache(projectRule.project, ::highDpiDisplay) { _, _, _ -> icon }
    val basePath = checkNotNull(projectRule.project.basePath) { "Need non-null base path!" }
    sampleSvgPath = FileSystems.getDefault().getPath(basePath, "HeyImAFile.xml")
    sampleSvgFile = TestFileUtils.writeFileAndRefreshVfs(sampleSvgPath, "whose contents are immaterial")
  }

  @Test
  fun cacheEmptyToStart() {
    assertThat(cache.getIconIfCached(sampleSvgFile)).isNull()
  }

  @Test
  fun cachedNullValue() {
    assertThat(cache.getIcon(sampleSvgFile, null, facet)).isNull()

    icon = ANDROID_HEAD

    assertThat(cache.getIconIfCached(sampleSvgFile)).isNull()
    // Should return cached value.
    assertThat(cache.getIcon(sampleSvgFile, null, facet)).isNull()
  }

  @Test
  fun cachedNonNullValue() {
    icon = ANDROID_HEAD

    assertThat(cache.getIcon(sampleSvgFile, null, facet)).isEqualTo(ANDROID_HEAD)

    icon = null

    assertThat(cache.getIconIfCached(sampleSvgFile)).isEqualTo(ANDROID_HEAD)
    // Should return cached value.
    assertThat(cache.getIcon(sampleSvgFile, null, facet)).isEqualTo(ANDROID_HEAD)
  }

  @Test
  fun cachedValueIgnoredOnFileChange() {
    icon = ANDROID_HEAD

    assertThat(cache.getIcon(sampleSvgFile, null, facet)).isEqualTo(ANDROID_HEAD)

    // "Modify" Document by rewriting its contents
    val document = ReadAction.compute<Document, Throwable> {  checkNotNull(FileDocumentManager.getInstance().getDocument(sampleSvgFile))}
    with(ApplicationManager.getApplication()) {
      invokeAndWait { runWriteAction { document.setText(document.text) } }
    }

    assertThat(cache.getIconIfCached(sampleSvgFile)).isNull()

    icon = WARNING
    assertThat(cache.getIcon(sampleSvgFile, null, facet)).isEqualTo(WARNING)
  }

  @Test
  fun cacheClearedOnHiDpiChange() {
    icon = ANDROID_HEAD

    assertThat(cache.getIcon(sampleSvgFile, null, facet)).isEqualTo(ANDROID_HEAD)

    highDpiDisplay = true

    assertThat(cache.getIconIfCached(sampleSvgFile)).isNull()

    icon = WARNING
    assertThat(cache.getIcon(sampleSvgFile, null, facet)).isEqualTo(WARNING)

    highDpiDisplay = false

    assertThat(cache.getIconIfCached(sampleSvgFile)).isNull()

    icon = ANDROID_HEAD
    assertThat(cache.getIcon(sampleSvgFile, null, facet)).isEqualTo(ANDROID_HEAD)
  }
}
