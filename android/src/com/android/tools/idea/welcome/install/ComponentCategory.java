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
package com.android.tools.idea.welcome.install;

import com.android.sdklib.repositoryv2.AndroidSdkHandler;
import com.android.tools.idea.welcome.wizard.ProgressStep;
import com.android.tools.idea.wizard.dynamic.DynamicWizardStep;
import com.android.tools.idea.wizard.dynamic.ScopedStateStore;
import com.google.common.collect.ImmutableList;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.Collection;
import java.util.Set;

/**
 * A non-leaf tree node. It is not possible to install it.
 */
public class ComponentCategory extends ComponentTreeNode {
  @NotNull private final String myName;
  @NotNull private final Collection<ComponentTreeNode> myComponents;

  public ComponentCategory(@NotNull String name, @NotNull String description, @NotNull ComponentTreeNode... components) {
    this(name, description, Arrays.asList(components));
  }

  @Override
  public void init(@NotNull ProgressStep progressStep) {
    for (ComponentTreeNode component : myComponents) {
      component.init(progressStep);
    }
  }

  public ComponentCategory(@NotNull String name, @NotNull String description, @NotNull Collection<ComponentTreeNode> components) {
    super(description);
    myName = name;
    myComponents = components;
  }

  @Override
  public String getLabel() {
    return myName;
  }

  @Override
  public Collection<InstallableComponent> getChildrenToInstall() {
    ImmutableList.Builder<InstallableComponent> builder = ImmutableList.builder();
    for (ComponentTreeNode component : myComponents) {
      builder.addAll(component.getChildrenToInstall());
    }
    return builder.build();
  }

  @Override
  public void updateState(@NotNull AndroidSdkHandler sdkHandler) {
    for (ComponentTreeNode component : myComponents) {
      component.updateState(sdkHandler);
    }
  }

  @NotNull
  @Override
  public Collection<DynamicWizardStep> createSteps() {
    ImmutableList.Builder<DynamicWizardStep> builder = ImmutableList.builder();
    for (ComponentTreeNode component : myComponents) {
      builder.addAll(component.createSteps());
    }
    return builder.build();
  }

  @Override
  public boolean isChecked() {
    for (ComponentTreeNode component : myComponents) {
      if (!component.isChecked()) {
        return false;
      }
    }
    return true;
  }

  @Override
  public boolean componentStateChanged(@NotNull Set<ScopedStateStore.Key> modified) {
    for (ComponentTreeNode component : myComponents) {
      if (component.componentStateChanged(modified)) {
        return true;
      }
    }
    return false;
  }

  @Override
  public Collection<ComponentTreeNode> getImmediateChildren() {
    return myComponents;
  }

  @Override
  public boolean isOptional() {
    for (ComponentTreeNode component : myComponents) {
      if (component.isOptional()) {
        return true;
      }
    }
    return false;
  }

  @Override
  public void toggle(boolean isSelected) {
    for (ComponentTreeNode component : myComponents) {
      component.toggle(isSelected);
    }
  }
}
