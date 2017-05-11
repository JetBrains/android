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
package com.android.tools.idea.run.editor;

import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class LaunchConfigurableWrapper implements Configurable, Configurable.NoScroll, Configurable.NoMargin {
  private final Project myProject;
  private final LaunchOptionConfigurableContext myContext;
  private final LaunchOption myOption;
  private LaunchOptionConfigurable myConfigurable;

  public LaunchConfigurableWrapper(@NotNull Project project,
                                   @NotNull LaunchOptionConfigurableContext context,
                                   @NotNull LaunchOption option) {
    myProject = project;
    myContext = context;
    myOption = option;
  }

  @Nls
  @Override
  public String getDisplayName() {
    return myOption.getDisplayName();
  }

  @Nullable
  @Override
  public JComponent createComponent() {
    return getConfigurable().createComponent();
  }

  public void resetFrom(@NotNull LaunchOptionState state) {
    getConfigurable().resetFrom(state);
  }

  public void applyTo(@NotNull LaunchOptionState state) {
    getConfigurable().applyTo(state);
  }

  private LaunchOptionConfigurable getConfigurable() {
    if (myConfigurable == null) {
      myConfigurable = myOption.createConfigurable(myProject, myContext);
    }
    return myConfigurable;
  }

  // The following methods of the Configurable interface are not used in this context
  @Override
  public boolean isModified() {
    return false;
  }

  @Override
  public void apply() throws ConfigurationException {
  }
}
