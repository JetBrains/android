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
package com.android.tools.idea.rendering;

import com.google.common.annotations.VisibleForTesting;
import com.android.ide.common.rendering.api.RenderResources;
import com.google.common.collect.Maps;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import icons.StudioIcons;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Map;

public class GutterIconCache {
  private static final Logger LOG = Logger.getInstance(GutterIconCache.class);
  private static final Icon NONE = StudioIcons.Common.ANDROID_HEAD; // placeholder

  @VisibleForTesting static final int MAX_WIDTH = JBUI.scale(16);
  @VisibleForTesting static final int MAX_HEIGHT = JBUI.scale(16);

  private static final GutterIconCache ourInstance = new GutterIconCache();

  private Map<String, Icon> myThumbnailCache = Maps.newHashMap();

  /**
   * Stores timestamps for the last modification time of image files using the
   * path as a key.
   */
  private Map<String, Long> myModificationStampCache = Maps.newHashMap();
  private boolean myRetina;

  public GutterIconCache() {
  }

  @NotNull
  public static GutterIconCache getInstance() {
    return ourInstance;
  }

  @VisibleForTesting
  boolean isIconUpToDate(@NotNull VirtualFile file) {
    String path = file.getPath();
    if (myModificationStampCache.containsKey(path)) {
      // Entry is valid if image resource has not been modified since the entry was cached
      return myModificationStampCache.get(path) == file.getModificationStamp() && !FileDocumentManager.getInstance().isFileModified(file);
    }

    return false;
  }

  @Nullable
  public Icon getIcon(@NotNull VirtualFile file, @Nullable RenderResources resolver, @NotNull AndroidFacet facet) {
    boolean isRetina = UIUtil.isRetina();
    if (myRetina != isRetina) {
      myRetina = isRetina;
      myThumbnailCache.clear();
    }
    String path = file.getPath();
    Icon myIcon = myThumbnailCache.get(path);
    if (myIcon == null || !isIconUpToDate(file)) {
      myIcon = GutterIconFactory.createIcon(file, resolver, MAX_WIDTH, MAX_HEIGHT, facet);

      if (myIcon == null) {
        myIcon = NONE;
      }

      myThumbnailCache.put(path, myIcon);

      // Record timestamp of image resource at the time of caching
      myModificationStampCache.put(path, file.getModificationStamp());
    }

    return myIcon != NONE ? myIcon : null;
  }

}
