/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.uibuilder.property;

import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.ToggleAction;
import org.jetbrains.annotations.NotNull;

public class ToggleDownloadableFontsAction extends ToggleAction {
  public static final String ENABLE_DOWNLOADABLE_FONTS = "DownloadableFonts";

  private NlPropertiesManager myPropertiesManager;

  public ToggleDownloadableFontsAction(@NotNull NlPropertiesManager propertiesManager) {
    super("Downloadable Fonts Enabled");
    myPropertiesManager = propertiesManager;
  }

  @Override
  public boolean isSelected(@NotNull AnActionEvent event) {
    PropertiesComponent properties = PropertiesComponent.getInstance();
    return properties.getBoolean(ENABLE_DOWNLOADABLE_FONTS);
  }

  @Override
  public void setSelected(@NotNull AnActionEvent event, boolean state) {
    PropertiesComponent properties = PropertiesComponent.getInstance();
    properties.setValue(ENABLE_DOWNLOADABLE_FONTS, state);
    myPropertiesManager.updateSelection();
  }
}
