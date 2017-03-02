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
package com.android.tools.idea.smali;

import com.intellij.openapi.fileTypes.LanguageFileType;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class SmaliFileType extends LanguageFileType {
  private static final SmaliFileType INSTANCE = new SmaliFileType();

  @NonNls public static final String SMALI_EXTENSION = "smali";

  @NotNull
  public static SmaliFileType getInstance() {
    return INSTANCE;
  }

  private SmaliFileType() {
    super(SmaliLanguage.getInstance());
  }

  @Override
  @NotNull
  public String getName() {
    return "Smali";
  }

  @Override
  @NotNull
  public String getDescription() {
    return "Smali file";
  }

  @Override
  @NotNull
  public String getDefaultExtension() {
    return SMALI_EXTENSION;
  }

  @Override
  @Nullable
  public Icon getIcon() {
    return SmaliIcons.SmaliFile;
  }
}
