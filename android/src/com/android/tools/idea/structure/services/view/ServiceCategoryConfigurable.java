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
package com.android.tools.idea.structure.services.view;

import com.android.tools.idea.structure.services.ServiceCategory;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * A {@link Configurable} that represents a Project Structure left panel entry for a
 * {@link ServiceCategory}.
 */
public final class ServiceCategoryConfigurable implements Configurable {

  private final ServiceCategory myCategory;
  private DeveloperServicesPanel myServicesPanel;

  public ServiceCategoryConfigurable(@NotNull ComboBoxModel moduleList, @NotNull ServiceCategory category) {
    myCategory = category;
    myServicesPanel = new DeveloperServicesPanel(moduleList, myCategory);
  }

  @Nls
  @Override
  public String getDisplayName() {
    return myCategory.getDisplayName();
  }

  @Nullable
  @Override
  public JComponent createComponent() {
    return myServicesPanel;
  }

  @Override
  public boolean isModified() {
    return myServicesPanel.isModified();
  }

  @Override
  public void apply() throws ConfigurationException {
    myServicesPanel.apply();
  }

  @Override
  public void reset() {
    // Do nothing - service data is already reset when AndroidProjectStructureConfigurable opens
  }

  @Override
  public void disposeUIResources() {
    myServicesPanel = null;
  }
}
