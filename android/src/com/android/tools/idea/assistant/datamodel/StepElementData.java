/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.assistant.datamodel;

import com.android.tools.idea.assistant.DefaultTutorialBundle;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.StdFileTypes;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Tutorial step elements. Rendered in a vertical collection under a single step. Only one of {@link #getCode()},
 * {@link #getSection()} or {@link #getAction()} should be non-null and it must match the value from {@code #getType}.
 */
public interface StepElementData {

  /**
   * Gets an indication of what type of element this is. Returned type indicates which getter to
   * call to retrieve data contents.
   */
  @NotNull
  StepElementType getType();

  /**
   * Code sample. See {@link #getCodeType()} for formatting.
   */
  @Nullable
  String getCode();

  /**
   * Optional, used when code is set to determine code formatting.
   * Suggest using {@link StdFileTypes}.
   */
  @Nullable
  FileType getCodeType();

  /**
   * Textual (rendered as simple html) content.
   */
  @Nullable
  String getSection();

  /**
   * Definition of an action to perform, rendered as a button.
   */
  @Nullable
  ActionData getAction();

  /**
   * Returns Image defination
   */
  @Nullable
  DefaultTutorialBundle.Image getImage();

}
