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

import com.android.annotations.VisibleForTesting;
import com.android.ide.common.resources.ResourceResolver;
import com.google.common.collect.Maps;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import icons.AndroidIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

import java.util.Map;

public class GutterIconCache {
  private static final Logger LOG = Logger.getInstance(GutterIconCache.class);
  private static final Icon NONE = AndroidIcons.Android; // placeholder

  @VisibleForTesting static final int MAX_WIDTH = JBUI.scale(16);
  @VisibleForTesting static final int MAX_HEIGHT = JBUI.scale(16);

  private static final GutterIconCache ourInstance = new GutterIconCache();

  private Map<String, Icon> myThumbnailCache = Maps.newHashMap();

  /**
   * Stores timestamps for the last modification time of image files using the
   * path as a key.
   */
  private Map<String, Long> myTimestampCache = Maps.newHashMap();
  private boolean myRetina;

  public GutterIconCache() {
  }

  @NotNull
  public static GutterIconCache getInstance() {
    return ourInstance;
  }

  @VisibleForTesting
  boolean isIconUpToDate(@NotNull String path) {
    if (myTimestampCache.containsKey(path)) {
      // Entry is valid if image resource has not been modified since the entry was cached
      VirtualFile file = LocalFileSystem.getInstance().findFileByPath(path);
      if (file != null) {
        return myTimestampCache.get(path) == file.getTimeStamp()
               && !FileDocumentManager.getInstance().isFileModified(file);
      }
    }

    return false;
  }

  @Nullable
  public Icon getIcon(@NotNull String path, @Nullable ResourceResolver resolver) {
    boolean isRetina = UIUtil.isRetina();
    if (myRetina != isRetina) {
      myRetina = isRetina;
      myThumbnailCache.clear();
    }
    Icon myIcon = myThumbnailCache.get(path);
    if (myIcon == null || !isIconUpToDate(path)) {
      myIcon = GutterIconFactory.createIcon(path, resolver, MAX_WIDTH, MAX_HEIGHT);

      if (myIcon == null) {
        myIcon = NONE;
      }

      myThumbnailCache.put(path, myIcon);

      // Record timestamp of image resource at the time of caching
      VirtualFile file = LocalFileSystem.getInstance().findFileByPath(path);
      if (file != null) {
        myTimestampCache.put(path, file.getTimeStamp());
      }
    }

    return myIcon != NONE ? myIcon : null;
  }

}
