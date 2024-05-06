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

package com.android.tools.idea.fileTypes;

import com.android.SdkConstants;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.PlatformIcons;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public final class AndroidNinePatchFileType implements FileType {
  public static final String EXTENSION = SdkConstants.DOT_9PNG.substring(1);
  public static final AndroidNinePatchFileType INSTANCE = new AndroidNinePatchFileType();

  private AndroidNinePatchFileType() {
  }

  @NotNull
  @Override
  public String getName() {
    return "Android 9-Patch";
  }

  @NotNull
  @Override
  public String getDescription() {
    return AndroidBundle.message("android.9patch.file.type.description");
  }

  @NotNull
  @Override
  public String getDefaultExtension() {
    return EXTENSION;
  }

  @Override
  public Icon getIcon() {
    return PlatformIcons.FILE_ICON;
  }

  @Override
  public boolean isBinary() {
    return true;
  }
}
