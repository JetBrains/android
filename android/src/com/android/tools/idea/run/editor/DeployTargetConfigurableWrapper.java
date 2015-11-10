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

import com.intellij.openapi.Disposable;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class DeployTargetConfigurableWrapper implements Configurable, Configurable.NoMargin, Configurable.NoScroll {
  @NotNull private final Project myProject;
  @NotNull private final Disposable myParentDisposable;
  @NotNull private final DeployTargetConfigurableContext myContext;
  @NotNull private final DeployTargetProvider myTarget;

  private DeployTargetConfigurable myConfigurable;

  public DeployTargetConfigurableWrapper(@NotNull Project project,
                                         @NotNull Disposable parent,
                                         @NotNull DeployTargetConfigurableContext context,
                                         @NotNull DeployTargetProvider target) {
    myProject = project;
    myParentDisposable = parent;
    myContext = context;
    myTarget = target;
  }

  @Nls
  @Override
  public String getDisplayName() {
    return myTarget.getDisplayName();
  }

  @Nullable
  @Override
  public String getHelpTopic() {
    return null;
  }

  @Nullable
  @Override
  public JComponent createComponent() {
    return getConfigurable().createComponent();
  }

  public void resetFrom(@NotNull DeployTargetState state, int uniqueID) {
    getConfigurable().resetFrom(state, uniqueID);
  }

  public void applyTo(@NotNull DeployTargetState state, int uniqueID) {
    getConfigurable().applyTo(state, uniqueID);
  }

  private DeployTargetConfigurable getConfigurable() {
    if (myConfigurable == null) {
      // create lazily, otherwise the UI for all the targets will be created even without the editor being opened
      myConfigurable = myTarget.createConfigurable(myProject, myParentDisposable, myContext);
    }
    return myConfigurable;
  }

  // The following methods of the Configurable interface are not used in this context.
  @Override
  public boolean isModified() {
    return false;
  }

  @Override
  public void apply() throws ConfigurationException {
  }

  @Override
  public void reset() {
  }

  @Override
  public void disposeUIResources() {
  }
}
