/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.apk;

import static com.android.SdkConstants.EXT_AAR;
import static com.android.SdkConstants.EXT_APP_BUNDLE;

import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.vfs.VirtualFile;
import icons.StudioIcons;
import javax.swing.Icon;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class BundleFileType implements FileType {
  public static final BundleFileType INSTANCE = new BundleFileType();

  private BundleFileType() {
  }

  @NotNull
  @Override
  public String getName() {
    return "AAB";
  }

  @NotNull
  @Override
  public String getDescription() {
    return "Android App Bundle";
  }

  @NotNull
  @Override
  public String getDefaultExtension() {
    return EXT_APP_BUNDLE;
  }

  @Nullable
  @Override
  public Icon getIcon() {
    return StudioIcons.Shell.Filetree.ANDROID_FILE;
  }

  @Override
  public boolean isBinary() {
    return true;
  }

  @Override
  public boolean isReadOnly() {
    return true;
  }

  @Nullable
  @Override
  public String getCharset(@NotNull VirtualFile file, @NotNull byte[] content) {
    return null;
  }
}
