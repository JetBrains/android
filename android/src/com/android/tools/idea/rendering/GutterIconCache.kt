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
import com.google.common.annotations.VisibleForTesting
import com.google.common.collect.Maps
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import icons.StudioIcons
import org.jetbrains.android.facet.AndroidFacet
import javax.swing.Icon

class GutterIconCache {
  private val myThumbnailCache: MutableMap<String, Icon?> = Maps.newHashMap()

  /**
   * Stores timestamps for the last modification time of image files using the
   * path as a key.
   */
  private val myModificationStampCache: MutableMap<String, Long> = Maps.newHashMap()
  private var myRetina = false
  @VisibleForTesting
  fun isIconUpToDate(file: VirtualFile): Boolean {
    val path = file.path
    return if (myModificationStampCache.containsKey(path)) {
      // Entry is valid if image resource has not been modified since the entry was cached
      myModificationStampCache[path] == file.modificationStamp && !FileDocumentManager.getInstance()
        .isFileModified(file)
    } else false
  }

  fun getIcon(file: VirtualFile, resolver: RenderResources?, facet: AndroidFacet): Icon? {
    val isRetina = UIUtil.isRetina()
    if (myRetina != isRetina) {
      myRetina = isRetina
      myThumbnailCache.clear()
    }
    val path = file.path
    var myIcon = myThumbnailCache[path]
    if (myIcon == null || !isIconUpToDate(file)) {
      myIcon = GutterIconFactory.createIcon(file, resolver, MAX_WIDTH, MAX_HEIGHT, facet)
      if (myIcon == null) {
        myIcon = NONE
      }
      myThumbnailCache[path] = myIcon

      // Record timestamp of image resource at the time of caching
      myModificationStampCache[path] = file.modificationStamp
    }
    return if (myIcon !== NONE) myIcon else null
  }

  companion object {
    private val NONE = StudioIcons.Common.ANDROID_HEAD // placeholder
    private val MAX_WIDTH = JBUI.scale(16)
    private val MAX_HEIGHT = JBUI.scale(16)
    val instance = GutterIconCache()
  }
}
