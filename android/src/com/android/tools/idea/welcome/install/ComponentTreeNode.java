/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.android.tools.idea.welcome.install;

import com.android.sdklib.repository.AndroidSdkHandler;
import com.android.tools.idea.welcome.wizard.ProgressStep;
import com.android.tools.idea.wizard.dynamic.DynamicWizardStep;
import com.android.tools.idea.wizard.dynamic.ScopedStateStore;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Set;

/**
 * Component that may be installed by the first run wizard.
 */
public abstract class ComponentTreeNode {
  private final String myDescription;

  public ComponentTreeNode(@NotNull String description) {
    myDescription = description;
  }

  public String getDescription() {
    return myDescription;
  }

  @Override
  public String toString() {
    return getLabel();
  }

  public abstract String getLabel();

  public void init(@NotNull ProgressStep progressStep) {
    // Do nothing
  }

  public abstract Collection<InstallableComponent> getChildrenToInstall();

  public abstract void updateState(@NotNull AndroidSdkHandler handler);

  @NotNull
  public abstract Collection<DynamicWizardStep> createSteps();

  public abstract boolean isChecked();

  public abstract boolean componentStateChanged(@NotNull Set<ScopedStateStore.Key> modified);

  public abstract Collection<ComponentTreeNode> getImmediateChildren();

  public abstract boolean isOptional();

  public abstract void toggle(boolean isSelected);
}
