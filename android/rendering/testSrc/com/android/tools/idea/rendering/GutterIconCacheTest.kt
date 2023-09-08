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
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.android.AndroidTestCase
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.FileTime
import javax.swing.Icon

class GutterIconCacheTest : AndroidTestCase() {
  private lateinit var sampleSvgPath: Path
  private lateinit var sampleSvgFile: VirtualFile
  private var icon: Icon? = null
  private val cache = GutterIconCache {_, _, _, -> icon }

  @Throws(Exception::class)
  override fun setUp() {
    super.setUp()
    sampleSvgPath = FileSystems.getDefault().getPath(myModule.project.basePath, "HeyImAFile.xml")
    sampleSvgFile = TestFileUtils.writeFileAndRefreshVfs(sampleSvgPath, "whose contents are immaterial")
  }

  fun testIsIconUpToDate_entryInvalidNotCached() {
    // If we've never requested an Icon for the path, there should be no valid cache entry.
    assertThat(cache.isIconUpToDate(sampleSvgFile)).isFalse()
  }

  fun testIsIconUpToDate_entryValid() {
    cache.getIcon(sampleSvgFile, null, myFacet)

    // If we haven't modified the image since creating an Icon, the cache entry is still valid
    assertThat(cache.isIconUpToDate(sampleSvgFile)).isTrue()
  }

  fun testIsIconUpToDate_entryInvalidUnsavedChanges() {
    cache.getIcon(sampleSvgFile, null, myFacet)

    // "Modify" Document by rewriting its contents
    val document = checkNotNull(FileDocumentManager.getInstance().getDocument(sampleSvgFile))
    ApplicationManager.getApplication().runWriteAction { document.setText(document.text) }

    // Modifying the image should have invalidated the cache entry.
    assertThat(cache.isIconUpToDate(sampleSvgFile)).isFalse()
  }

  @Throws(Exception::class)
  fun testIconUpToDate_entryInvalidSavedChanges() {
    cache.getIcon(sampleSvgFile, null, myFacet)

    // Modify image resource by adding an empty comment and then save
    val document = checkNotNull(FileDocumentManager.getInstance().getDocument(sampleSvgFile))
    ApplicationManager.getApplication().runWriteAction {
      document.setText(document.text + "<!-- -->")
      FileDocumentManager.getInstance().saveDocument(document)
    }

    // Modifying the image should have invalidated the cache entry.
    assertThat(cache.isIconUpToDate(sampleSvgFile)).isFalse()
  }

  @Throws(Exception::class)
  fun testIconUpToDate_entryInvalidDiskChanges() {
    cache.getIcon(sampleSvgFile, null, myFacet)
    val previousTimestamp = Files.getLastModifiedTime(sampleSvgPath)

    // "Modify" file by changing its lastModified field
    Files.setLastModifiedTime(sampleSvgPath, FileTime.fromMillis(System.currentTimeMillis() + 1000))
    sampleSvgFile.refresh(false, false)

    // Sanity check
    assertThat(previousTimestamp).isLessThan(Files.getLastModifiedTime(sampleSvgPath))

    // Modifying the image should have invalidated the cache entry.
    assertThat(cache.isIconUpToDate(sampleSvgFile)).isFalse()
  }
}
