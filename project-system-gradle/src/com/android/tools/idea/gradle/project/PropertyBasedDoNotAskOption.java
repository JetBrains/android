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
package com.android.tools.idea.gradle.project;

import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DoNotAskOption;
import com.intellij.ui.UIBundle;
import org.jetbrains.annotations.NotNull;

/**
 * Implementation of "Do not show this dialog in the future" option. This option is displayed as a checkbox in a {@code Messages} dialog.
 * The state of such checkbox is stored in the IDE's {@code PropertiesComponent} under the name passed in the constructor.
 */
public class PropertyBasedDoNotAskOption implements DoNotAskOption {
  @NotNull private final Project myProject;
  /**
   * The name of the property storing the value of the "Do not show this dialog in the future" option.
   */
  @NotNull private final String myProperty;

  public PropertyBasedDoNotAskOption(@NotNull Project project, @NotNull String property) {
    myProject = project;
    myProperty = property;
  }

  @Override
  public boolean isToBeShown() {
    // Read the stored value. If none is found, return "true" to display the checkbox the first time.
    return PropertiesComponent.getInstance(myProject).getBoolean(myProperty, true);
  }

  @Override
  public void setToBeShown(boolean toBeShown, int exitCode) {
    // Stores the state of the checkbox into the property.
    PropertiesComponent.getInstance(myProject).setValue(myProperty, String.valueOf(toBeShown));
  }

  @Override
  public boolean canBeHidden() {
    // By returning "true", the Messages dialog can hide the checkbox if the user previously set the checkbox as "selected".
    return true;
  }

  @Override
  public boolean shouldSaveOptionsOnCancel() {
    // We always want to save the value of the checkbox, regardless of the button pressed in the Messages dialog.
    return true;
  }

  @Override
  @NotNull
  public String getDoNotShowMessage() {
    // This is the text to set in the checkbox.
    return UIBundle.message("dialog.options.do.not.show");
  }
}
