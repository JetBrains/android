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
package com.android.tools.idea.gradle.structure.configurables;

import com.android.tools.idea.gradle.structure.model.PsModule;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.ui.NamedConfigurable;
import com.intellij.ui.navigation.Place;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public abstract class BaseNamedConfigurable<T extends PsModule> extends NamedConfigurable<T>
  implements SearchableConfigurable, Place.Navigator {

  @NotNull private final T myModuleModel;

  private String myDisplayName;

  protected BaseNamedConfigurable(@NotNull T moduleModel) {
    myModuleModel = moduleModel;
    myDisplayName = moduleModel.getName();
  }

  @Override
  @Nullable
  public Icon getIcon(boolean expanded) {
    return myModuleModel.getModuleIcon();
  }

  @Override
  @Nls
  public String getDisplayName() {
    return myDisplayName;
  }

  @Override
  public void setDisplayName(String name) {
    myDisplayName = name;
  }

  @Override
  public T getEditableObject() {
    return myModuleModel;
  }

  @Override
  public String getBannerSlogan() {
    return "Module '" + myDisplayName + "'";
  }

  @Nullable
  @Override
  public String getHelpTopic() {
    return null;
  }

  @Nullable
  @Override
  public Runnable enableSearch(String option) {
    return null;
  }
}
