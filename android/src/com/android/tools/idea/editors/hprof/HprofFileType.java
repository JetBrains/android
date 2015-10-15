/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.tools.idea.editors.hprof;

import com.android.SdkConstants;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.vfs.VirtualFile;
import icons.AndroidIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class HprofFileType implements FileType {
  public static final HprofFileType INSTANCE = new HprofFileType();

  @NotNull
  @Override
  public String getName() {
    return "Hprof";
  }

  @NotNull
  @Override
  public String getDescription() {
    return "Heap Profiler (Hprof) files";
  }

  @NotNull
  @Override
  public String getDefaultExtension() {
    return SdkConstants.EXT_HPROF;
  }

  @Nullable
  @Override
  public Icon getIcon() {
    return AndroidIcons.Ddms.DumpHprof;
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
