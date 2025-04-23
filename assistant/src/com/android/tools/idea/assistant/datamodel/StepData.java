/*
 * Copyright (C) 2018 The Android Open Source Project
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

import java.util.List;
import javax.swing.border.Border;
import org.jetbrains.annotations.NotNull;

/**
 * A single step in a tutorial. All tutorials are made of one or more steps
 * which may contain one or more of a variety of presentation objects.
 */
public interface StepData {

  /**
   * Gets the individual elements (such as text blocks, buttons, code samples) for
   * the given step.
   */
  @NotNull
  List<? extends StepElementData> getStepElements();

  /**
   * Gets a textual label for the step. Should represent a summary of the items from {@code #getStepElements()}
   */
  @NotNull
  String getLabel();

  /**
   * Gets the border for the step, can be used to create separators
   */
  @NotNull
  Border getBorder();
}
