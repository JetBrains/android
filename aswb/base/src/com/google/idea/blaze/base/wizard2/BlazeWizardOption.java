/*
 * Copyright 2016 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.idea.blaze.base.wizard2;

import com.google.idea.blaze.base.ui.UiUtil;
import com.intellij.openapi.options.ConfigurationException;
import javax.annotation.Nullable;
import javax.swing.JComponent;

/** Base class for options in the project import wizard. */
public interface BlazeWizardOption {
  int VERTICAL_LAYOUT_GAP = 10;
  int HORIZONTAL_LAYOUT_GAP = 10;
  int PREFERRED_COMPONENT_WIDTH = 700;
  int MINIMUM_FIELD_WIDTH = 120;

  /** Used during serialization to remember which option was selected. */
  String getOptionName();

  /** A brief description of this option. */
  String getDescription();

  void validateAndUpdateBuilder(BlazeNewProjectBuilder builder) throws ConfigurationException;

  /** Serializes any relevant user-facing options. */
  void commit() throws BlazeProjectCommitException;

  /** @return a ui component for this option. */
  @Nullable
  JComponent getUiComponent();

  default void optionSelected() {
    UiUtil.setEnabledRecursive(getUiComponent(), true);
  }

  default void optionDeselected() {
    UiUtil.setEnabledRecursive(getUiComponent(), false);
  }
}
