/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.android.tools.idea.lang.proguard;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.fileTypes.LanguageFileType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class ProguardFileType extends LanguageFileType {
  public static final ProguardFileType INSTANCE = new ProguardFileType();

  /** Default extension for ProGuard files (without dot) */
  public static final String EXT_PRO = "pro";

  /** Default extension for ProGuard files (with dot) */
  public static final String DOT_PRO = ".pro";

  private ProguardFileType() {
    super(ProguardLanguage.INSTANCE);
  }

  @NotNull
  @Override
  public String getName() {
    return "ProGuard File";
  }

  @NotNull
  @Override
  public String getDescription() {
    return "ProGuard Rules Language File";
  }

  @NotNull
  @Override
  public String getDefaultExtension() {
    return EXT_PRO;
  }

  @Nullable
  @Override
  public Icon getIcon() {
    return AllIcons.FileTypes.Text;
  }
}
