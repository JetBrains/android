/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.npw.ui;

import com.android.tools.idea.npw.template.TemplateHandle;
import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.imageio.ImageIO;
import java.awt.*;
import java.io.File;
import java.io.IOException;

import static org.jetbrains.android.util.AndroidBundle.message;

/**
 * Utility methods to load Template Images and find labels (TODO: Better desc)
 */
public class ActivityGallery {
  /**
   * Return the image associated with the current template, if it specifies one, or null otherwise.
   */
  @Nullable
  public static Image getTemplateImage(@Nullable TemplateHandle templateHandle) {
    String thumb = templateHandle == null ? null : templateHandle.getMetadata().getThumbnailPath();
    if (thumb != null && !thumb.isEmpty()) {
      try {
        File file = new File(templateHandle.getRootPath(), thumb.replace('/', File.separatorChar));
        return file.isFile() ? ImageIO.read(file) : null;
      }
      catch (IOException e) {
        Logger.getInstance(ActivityGallery.class).warn(e);
      }
    }
    return null;
  }

  @NotNull
  public static String getTemplateImageLabel(@Nullable TemplateHandle templateHandle) {
    if (templateHandle == null) {
      return message("android.wizard.gallery.item.add.no.activity");
    }
    String title = templateHandle.getMetadata().getTitle();
    return title == null ? "" : title;
  }

  @NotNull
  public static String getTemplateDescription(@Nullable TemplateHandle templateHandle) {
    if (templateHandle == null) {
      return message("android.wizard.gallery.item.add.no.activity.desc");
    }
    String description = templateHandle.getMetadata().getDescription();
    return description == null ? "" : description;
  }
}
