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

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.List;

/**
 * A tutorial consists of a relatively small number of instructions to
 * achieve a targeted task. It is treated as a single view hanging off of a
 * parent feature.
 */
public interface TutorialData {
  /**
   * Returns a descriptive label for the tutorial. Used in conjunction with
   * {@code #getDescription} to represent the tutorial's purpose.
   */
  @NotNull
  String getLabel();

  /**
   * Returns a short description of the tutorial, rendered as markup. Note that
   * markup should be restricted to adding links and extremely basic formatting
   * such as bolding.
   */
  @Nullable
  String getDescription();

  /**
   * Returns a url for more information on the tutorial.
   */
  @Nullable
  String getRemoteLink();

  /**
   * Returns the text to linkify with the result of {@code #getRemoteLink}.
   */
  @Nullable
  String getRemoteLinkLabel();

  /**
   * Returns a unique key for the tutorial. Used for mapping and logging purposes
   * and is not user visible.
   */
  @NotNull
  String getKey();

  /**
   * Returns an icon for use in the tutorial. Displayed next to the label if non-null.
   */
  @Nullable
  Icon getIcon();

  /**
   * Returns the set of steps in the tutorial.
   */
  @NotNull
  List<? extends StepData> getSteps();
}
