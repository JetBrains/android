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

import com.android.tools.adtui.common.SwingCoordinate;
import com.android.tools.idea.common.api.DragType;
import com.android.tools.idea.common.api.InsertType;
import com.android.tools.idea.common.model.AndroidCoordinate;
import com.android.tools.idea.common.model.NlAttributesHolder;
import com.android.tools.idea.common.model.NlComponent;
import com.android.tools.idea.common.model.NlModel;
import com.android.tools.idea.common.scene.Placeholder;
import com.android.tools.idea.common.surface.DesignSurface;
import com.android.tools.idea.uibuilder.api.*;
import com.android.tools.idea.uibuilder.api.actions.ViewAction;
import com.android.tools.idea.uibuilder.model.*;
import com.android.tools.idea.common.scene.ComponentProvider;
import com.android.tools.idea.common.scene.SceneComponent;
import com.android.tools.idea.common.scene.target.Target;
import com.android.tools.idea.common.surface.Interaction;
import com.android.tools.idea.uibuilder.surface.AccessoryPanel;
import com.android.tools.idea.uibuilder.surface.ScreenView;
import java.awt.Graphics2D;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Collection;
import java.util.List;
import java.util.Map;

public class DelegatingViewGroupHandler extends ViewGroupHandler {
  private final ViewGroupHandler myHandler;

  public DelegatingViewGroupHandler(@NotNull ViewGroupHandler handler) {
    myHandler = handler;
  }

  // ViewGroupHandler

  @Override
  @Nullable
  public CustomPanel getLayoutCustomPanel() {
    return myHandler.getLayoutCustomPanel();
  }

  @Override
  public boolean acceptsChild(@NotNull NlComponent layout, @NotNull NlComponent newChild) {
    return myHandler.acceptsChild(layout, newChild);
  }

  @Override
  public boolean acceptsChild(@NotNull SceneComponent parent,
                              @NotNull NlComponent newChild,
                              @AndroidCoordinate int x,
                              @AndroidCoordinate int y) {
    return myHandler.acceptsChild(parent, newChild, x, y);
  }

  @Override
  public boolean deleteChildren(@NotNull NlComponent parent, @NotNull Collection<NlComponent> deleted) {
    return myHandler.deleteChildren(parent, deleted);
  }

  @Override
  @Nullable
  public Interaction createInteraction(@NotNull ScreenView screenView,
                                       @SwingCoordinate int x,
                                       @SwingCoordinate int y,
                                       @NotNull NlComponent component) {
    return myHandler.createInteraction(screenView, x, y, component);
  }

  @Override
  @Nullable
  public DragHandler createDragHandler(@NotNull ViewEditor editor,
                                       @NotNull SceneComponent layout,
                                       @NotNull List<NlComponent> components,
                                       @NotNull DragType type) {
    return myHandler.createDragHandler(editor, layout, components, type);
  }

  @Override
  @Nullable
  public ScrollHandler createScrollHandler(@NotNull ViewEditor editor, @NotNull NlComponent component) {
    return myHandler.createScrollHandler(editor, component);
  }

  @Override
  public void onChildInserted(@NotNull NlComponent layout,
                              @NotNull NlComponent newChild,
                              @NotNull InsertType insertType) {
    myHandler.onChildInserted(layout, newChild, insertType);
  }

  @Override
  public boolean handlesPainting() {
    return myHandler.handlesPainting();
  }

  @Override
  public void clearAttributes(@NotNull List<NlComponent> components) {
    myHandler.clearAttributes(components);
  }

  @Override
  public ComponentProvider getComponentProvider(@NotNull SceneComponent component) {
    return myHandler.getComponentProvider(component);
  }

  @Override
  public void performDrop(@NotNull NlModel model,
                          @NotNull NlDropEvent event,
                          @NotNull NlComponent receiver,
                          @NotNull List<NlComponent> dragged,
                          @Nullable NlComponent before, @NotNull InsertType type) {
    myHandler.performDrop(model, event, receiver, dragged, before, type);
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

  // Properties

  @Override
  @NotNull
  public List<String> getInspectorProperties() {
    return myHandler.getInspectorProperties();
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
  @NotNull
  public String getGradleCoordinateId(@NotNull String tagName) {
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

  @Override
  @NotNull
  public List<Target> createTargets(@NotNull SceneComponent sceneComponent) {
    return myHandler.createTargets(sceneComponent);
  }

  @NotNull
  @Override
  public List<Target> createChildTargets(@NotNull SceneComponent parentComponent, @NotNull SceneComponent childComponent) {
    return myHandler.createChildTargets(parentComponent, childComponent);
  }

  @Override
  public List<Placeholder> getPlaceholders(@NotNull SceneComponent component, @NotNull List<SceneComponent> draggedComponents) {
    return myHandler.getPlaceholders(component, draggedComponents);
  }

  @NotNull
  public ViewGroupHandler getDelegateHandler() {
    return myHandler;
  }

  @Nullable
  @Override
  public AccessoryPanelInterface createAccessoryPanel(@NotNull DesignSurface<?> surface,
                                                      @NotNull AccessoryPanel.Type type,
                                                      @NotNull NlComponent parent,
                                                      @NotNull AccessoryPanelVisibility callback) {
    return myHandler.createAccessoryPanel(surface, type, parent, callback);
  }

  @Override
  public boolean needsAccessoryPanel(@NotNull AccessoryPanel.Type type) {
    return myHandler.needsAccessoryPanel(type);
  }

  @Override
  public void onChildRemoved(@NotNull NlComponent layout,
                             @NotNull NlComponent newChild,
                             @NotNull InsertType insertType) {
    myHandler.onChildRemoved(layout, newChild, insertType);
  }

  @Override
  public boolean drawGroup(@NotNull Graphics2D gc, @NotNull ScreenView screenView, @NotNull NlComponent component) {
    return myHandler.drawGroup(gc, screenView, component);
  }

  @Override
  public void cleanUpAttributes(@NotNull NlComponent component, @NotNull NlAttributesHolder attributes) {
    myHandler.cleanUpAttributes(component, attributes);
  }

  @Override
  public int getComponentTreeChildCount(@NotNull Object component) {
    return myHandler.getComponentTreeChildCount(component);
  }

  @Override
  public List<?> getComponentTreeChildren(@NotNull Object component) {
    return myHandler.getComponentTreeChildren(component);
  }

  @Override
  public Object getComponentTreeChild(@NotNull Object component, int i) {
    return myHandler.getComponentTreeChild(component, i);
  }

  @Override
  public void onActivateInComponentTree(@NotNull NlComponent component) {
    myHandler.onActivateInComponentTree(component);
  }

  @Override
  public void onActivateInDesignSurface(@NotNull NlComponent component, @AndroidCoordinate int x, @AndroidCoordinate int y) {
    myHandler.onActivateInDesignSurface(component, x, y);
  }

  @Override
  public List<ViewAction> getPropertyActions(@NotNull List<NlComponent> components) {
    return myHandler.getPropertyActions(components);
  }

  @NotNull
  @Override
  public String generateBaseId(@NotNull NlComponent component) {
    return myHandler.generateBaseId(component);
  }

  @NotNull
  @Override
  public Map<String, String> getPrefixToNamespaceMap() {
    return myHandler.getPrefixToNamespaceMap();
  }

  @Nullable
  @Override
  public CustomPanel getCustomPanel() {
    return myHandler.getCustomPanel();
  }
}
