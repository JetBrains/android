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
package com.android.tools.idea.gradle.editor.value;

import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * {@link GradleEditorEntityValueManager} implementation which accepts only numeric values.
 */
public class IntegerValueManager implements GradleEditorEntityValueManager {

  @Nullable
  @Override
  public String validate(@NotNull String newValue, boolean strict) {
    for (int i = 0; i < newValue.length(); i++) {
      if (newValue.charAt(i) < '0' || newValue.charAt(i) > '9') {
        return AndroidBundle.message("android.gradle.editor.validation.numeric.only");
      }
    }
    return null;
  }

  @Override
  public boolean isAvailableVersionsHintReady() {
    return true;
  }

  @Nullable
  @Override
  public List<String> hintAvailableVersions() {
    return null;
  }
}
