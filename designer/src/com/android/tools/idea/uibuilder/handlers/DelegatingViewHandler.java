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
package com.android.tools.idea.uibuilder.handlers;

import com.android.ide.common.repository.GoogleMavenArtifactId;
import com.android.tools.idea.common.api.InsertType;
import com.android.tools.idea.common.model.NlComponent;
import com.android.tools.idea.common.scene.SceneComponent;
import com.android.tools.idea.uibuilder.api.CustomPanel;
import com.android.tools.idea.uibuilder.api.ViewHandler;
import com.android.tools.idea.uibuilder.api.XmlType;
import com.android.tools.idea.uibuilder.api.actions.ViewAction;
import com.android.tools.idea.uibuilder.model.FillPolicy;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.List;
import java.util.Map;

public class DelegatingViewHandler extends ViewHandler {
  private final ViewHandler myHandler;

  public DelegatingViewHandler(@NotNull ViewHandler handler) {
    myHandler = handler;
  }

  // ViewHandler

  @Override
  public boolean acceptsParent(@NotNull NlComponent layout, @NotNull NlComponent newChild) {
    return myHandler.acceptsParent(layout, newChild);
  }

  @Override
  public boolean onCreate(@Nullable NlComponent parent,
                          @NotNull NlComponent newChild,
                          @NotNull InsertType insertType) {
    return myHandler.onCreate(parent, newChild, insertType);
  }

  @Override
  public FillPolicy getFillPolicy() {
    return myHandler.getFillPolicy();
  }

  @Override
  public void addToolbarActions(@NotNull List<ViewAction> actions) {
    myHandler.addToolbarActions(actions);
  }

  @Override
  public boolean addPopupMenuActions(@NotNull SceneComponent component, @NotNull List<ViewAction> actions) {
    return myHandler.addPopupMenuActions(component, actions);
  }

  // Properties

  @Override
  @NotNull
  public List<String> getInspectorProperties() {
    return myHandler.getInspectorProperties();
  }

  @Override
  @Nullable
  public CustomPanel getCustomPanel() {
    return myHandler.getCustomPanel();
  }

  @Override
  @NotNull
  public List<String> getLayoutInspectorProperties() {
    return myHandler.getInspectorProperties();
  }

  @Override
  @NotNull
  public Map<String, Map<String, String>> getEnumPropertyValues(@NotNull NlComponent component) {
    return myHandler.getEnumPropertyValues(component);
  }

  @Override
  @Nullable
  public String getPreferredProperty() {
    return myHandler.getPreferredProperty();
  }

  @Override
  @NotNull
  public List<String> getBaseStyles(@NotNull String tagName) {
    return myHandler.getBaseStyles(tagName);
  }

  // Component Tree

  @Override
  @NotNull
  public String getTitle(@NotNull NlComponent component) {
    return myHandler.getTitle(component);
  }

  @Override
  @NotNull
  public String getTitleAttributes(@NotNull NlComponent component) {
    return myHandler.getTitleAttributes(component);
  }

  @Override
  @NotNull
  public Icon getIcon(@NotNull NlComponent component) {
    return myHandler.getIcon(component);
  }

  // Palette

  @Override
  @NotNull
  public String getTitle(@NotNull String tagName) {
    return myHandler.getTitle(tagName);
  }

  @Override
  @NotNull
  public Icon getIcon(@NotNull String tagName) {
    return myHandler.getIcon(tagName);
  }

  @Override
  @Nullable
  public GoogleMavenArtifactId getGradleCoordinateId(@NotNull String tagName) {
    return myHandler.getGradleCoordinateId(tagName);
  }

  @Override
  @NotNull
  @Language("XML")
  public String getXml(@NotNull String tagName, @NotNull XmlType xmlType) {
    return myHandler.getXml(tagName, xmlType);
  }

  @Override
  public double getPreviewScale(@NotNull String tagName) {
    return myHandler.getPreviewScale(tagName);
  }
}
