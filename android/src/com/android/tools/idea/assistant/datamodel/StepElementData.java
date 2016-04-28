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

import com.intellij.openapi.fileTypes.FileType;

/**
 * Tutorial step elements. Rendered in a vertical collection under a single step. Only one of {@code getCode}, {@code getSection} or
 * {@code getAction} should be non-null. If more than one are non-null, there is no guarantee about behavior.
 */
public interface StepElementData {
  StepElementType getType();

  /**
   * Code sample. {@see getCodeType} for formatting.
   */
  String getCode();

  /**
   * Optional, used when code is set to determine code formatting.
   * Suggest using {@see StdFileTypes}.
   */
  FileType getCodeType();

  /**
   * Textual (rendered as simple html) content.
   */
  String getSection();

  /**
   * Definition of an action to perform, rendered as a button.
   */
  ActionData getAction();
}
