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
package com.android.tools.idea.lang.databinding;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.fileTypes.LanguageFileType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * A file type for testing data-binding.
 */
public class DbFileType extends LanguageFileType {

  public static final DbFileType INSTANCE = new DbFileType();
  public static final String EXT = "android_data_binding";

  /**
   * Creates a language file type for the specified language.
   */
  private   DbFileType() {
    super(DbLanguage.INSTANCE);
  }

  @NotNull
  @Override
  public String getName() {
    return "AndroidDataBinding";
  }

  @NotNull
  @Override
  public String getDescription() {
    return "Android data binding expression";
  }

  @NotNull
  @Override
  public String getDefaultExtension() {
    return EXT;
  }

  @Nullable
  @Override
  public Icon getIcon() {
    return AllIcons.FileTypes.Custom;
  }
}
