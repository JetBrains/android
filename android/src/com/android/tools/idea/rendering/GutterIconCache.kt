/*
 * Copyright (C) 2013 The Android Open Source Project
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

import com.android.ide.common.rendering.api.RenderResources
import com.google.common.collect.Maps
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import org.jetbrains.android.facet.AndroidFacet
import org.jetbrains.annotations.TestOnly
import javax.swing.Icon
import kotlin.properties.Delegates.observable

class GutterIconCache {
  private val thumbnailCache: MutableMap<String, TimestampedIcon> = Maps.newConcurrentMap()
  private var highDpiDisplay by observable(false) { _, oldValue, newValue ->
    if (oldValue != newValue) thumbnailCache.clear()
  }

  @TestOnly
  fun isIconUpToDate(file: VirtualFile) = thumbnailCache[file.path]?.isAsNewAs(file) ?: false

  fun getIcon(file: VirtualFile, resolver: RenderResources?, facet: AndroidFacet): Icon? {
    highDpiDisplay = UIUtil.isRetina()
    val path = file.path
    thumbnailCache[path]?.takeIf { it.isAsNewAs(file) }?.let { return it.icon }

    return GutterIconFactory.createIcon(file, resolver, MAX_WIDTH, MAX_HEIGHT, facet).also {
      thumbnailCache[path] = TimestampedIcon(it, file.modificationStamp)
    }
  }

  data class TimestampedIcon(val icon: Icon?, val timestamp: Long) {
    fun isAsNewAs(file: VirtualFile) =
      // Entry is valid if image resource has not been modified since the entry was cached
      timestamp == file.modificationStamp && !FileDocumentManager.getInstance().isFileModified(file)

  }

  companion object {
    private val MAX_WIDTH = JBUI.scale(16)
    private val MAX_HEIGHT = JBUI.scale(16)
    @JvmField
    val INSTANCE = GutterIconCache()
  }
}
