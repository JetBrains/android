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
package com.android.tools.idea.fileTypes;

import com.intellij.openapi.fileTypes.ExtensionFileNameMatcher;
import com.intellij.openapi.fileTypes.FileNameMatcher;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.vfs.VirtualFile;
import icons.StudioIcons;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class FontFileType implements FileType {
  public static final FontFileType INSTANCE = new FontFileType();
  @NonNls private static final String OTF_EXTENSION = "otf";
  @NonNls private static final String TTF_EXTENSION = "ttf";

  private FontFileType() {
  }

  public static FileNameMatcher[] fileNameMatchers() {
    return new FileNameMatcher[]{
      new ExtensionFileNameMatcher(TTF_EXTENSION),
      new ExtensionFileNameMatcher(OTF_EXTENSION),
    };
  }

  @NotNull
  @Override
  public String getName() {
    return "Font";
  }

  @NotNull
  @Override
  public String getDescription() {
    return AndroidBundle.message("android.font.file.type.description");
  }

  @NotNull
  @Override
  public String getDefaultExtension() {
    return TTF_EXTENSION;
  }

  @Override
  public Icon getIcon() {
    return StudioIcons.Shell.Filetree.FONT_FILE;
  }

  @Override
  public boolean isBinary() {
    return true;
  }
}
