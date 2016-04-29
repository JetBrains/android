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
package com.android.tools.idea.profiling.capture;

import com.android.utils.SdkUtils;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.text.SimpleDateFormat;
import java.util.Date;

public abstract class FileCaptureType extends CaptureType {
  @NotNull private String myName;
  @NotNull private Icon myIcon;
  @NotNull private String myFileNameExtension;

  protected FileCaptureType(@NotNull String name, @NotNull Icon icon, @NotNull String fileNameExtension) {
    myName = name;
    myIcon = icon;
    myFileNameExtension = fileNameExtension;
  }

  @NotNull
  @Override
  public String getName() {
    return myName;
  }

  @NotNull
  @Override
  public Icon getIcon() {
    return myIcon;
  }

  @NotNull
  @Override
  public String getCaptureExtension() {
    return myFileNameExtension;
  }

  @Override
  public boolean isValidCapture(@NotNull VirtualFile file) {
    return isValidCapture(file.getPath());
  }

  public boolean isValidCapture(@NotNull String filePath) {
    return SdkUtils.endsWithIgnoreCase(filePath, myFileNameExtension);
  }

  @NotNull
  @Override
  protected Capture createCapture(@NotNull VirtualFile file) {
    return new Capture(file, this);
  }
}
