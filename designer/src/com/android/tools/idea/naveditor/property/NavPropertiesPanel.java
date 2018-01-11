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
package com.android.tools.idea.naveditor.property;

import com.android.tools.idea.common.model.NlComponent;
import com.android.tools.idea.common.property.PropertiesPanel;
import com.android.tools.idea.common.property.inspector.InspectorPanel;
import com.android.tools.idea.naveditor.property.inspector.NavInspectorPanel;
import com.android.tools.idea.uibuilder.property.NlPropertyItem;
import com.google.common.collect.Table;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.util.Disposer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.List;

/**
 * TODO
 */
public class NavPropertiesPanel extends PropertiesPanel<NavPropertiesManager> {

  private final NavPropertiesManager myPropertiesManager;
  private final InspectorPanel<NavPropertiesManager> myInspectorPanel;

  public NavPropertiesPanel(@NotNull NavPropertiesManager propertiesManager) {
    super(new BorderLayout());
    myPropertiesManager = propertiesManager;
    myInspectorPanel = new NavInspectorPanel(this);
    add(myInspectorPanel, BorderLayout.CENTER);
    Disposer.register(myPropertiesManager, this);
  }

  @Override
  public void activatePreferredEditor(@NotNull String propertyName, boolean afterLoad) {

  }

  @Override
  public void setItems(@NotNull List<NlComponent> components,
                       @NotNull Table<String, String, NlPropertyItem> properties) {
    myInspectorPanel.setComponent(components, properties, myPropertiesManager);
  }

  @Override
  public void performCopy(@NotNull DataContext dataContext) {

  }

  @Override
  public boolean isCopyEnabled(@NotNull DataContext dataContext) {
    return false;
  }

  @Override
  public boolean isCopyVisible(@NotNull DataContext dataContext) {
    return false;
  }

  @Override
  public void performCut(@NotNull DataContext dataContext) {

  }

  @Override
  public boolean isCutEnabled(@NotNull DataContext dataContext) {
    return false;
  }

  @Override
  public boolean isCutVisible(@NotNull DataContext dataContext) {
    return false;
  }

  @Override
  public void deleteElement(@NotNull DataContext dataContext) {

  }

  @Override
  public boolean canDeleteElement(@NotNull DataContext dataContext) {
    return false;
  }

  @Override
  public void performPaste(@NotNull DataContext dataContext) {

  }

  @Override
  public boolean isPastePossible(@NotNull DataContext dataContext) {
    return false;
  }

  @Override
  public boolean isPasteEnabled(@NotNull DataContext dataContext) {
    return false;
  }

  @Override
  public void dispose() {

  }

  @Nullable
  @Override
  public Object getData(String dataId) {
    return null;
  }
}
