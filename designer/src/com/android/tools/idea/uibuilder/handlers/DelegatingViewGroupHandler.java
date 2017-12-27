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

import com.android.tools.idea.common.model.AndroidCoordinate;
import com.android.tools.idea.common.model.NlComponent;
import com.android.tools.idea.common.model.NlModel;
import com.android.tools.idea.uibuilder.api.*;
import com.android.tools.idea.uibuilder.api.actions.ViewAction;
import com.android.tools.idea.uibuilder.model.*;
import com.android.tools.idea.common.scene.ComponentProvider;
import com.android.tools.idea.common.scene.SceneComponent;
import com.android.tools.idea.common.scene.target.Target;
import com.android.tools.idea.common.surface.Interaction;
import com.android.tools.idea.uibuilder.surface.ScreenView;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.dnd.DropTargetDropEvent;
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
  public Interaction createInteraction(@NotNull ScreenView screenView, @NotNull NlComponent layout) {
    return myHandler.createInteraction(screenView, layout);
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
  public ResizeHandler createResizeHandler(@NotNull ViewEditor editor,
                                           @NotNull NlComponent component,
                                           @Nullable SegmentType horizontalEdgeType,
                                           @Nullable SegmentType verticalEdgeType) {
    return myHandler.createResizeHandler(editor, component, horizontalEdgeType, verticalEdgeType);
  }


  @Override
  @Nullable
  public ScrollHandler createScrollHandler(@NotNull ViewEditor editor, @NotNull NlComponent component) {
    return myHandler.createScrollHandler(editor, component);
  }

  @Override
  public void onChildInserted(@NotNull ViewEditor editor,
                              @NotNull NlComponent layout,
                              @NotNull NlComponent newChild,
                              @NotNull InsertType insertType) {
    myHandler.onChildInserted(editor, layout, newChild, insertType);
  }

  @Override
  public boolean handlesPainting() {
    return myHandler.handlesPainting();
  }

  @Override
  public boolean drawGroup(@NotNull Graphics2D gc, @NotNull ScreenView screenView, @NotNull NlComponent component) {
    return myHandler.drawGroup(gc, screenView, component);
  }

  @Override
  public void clearAttributes(@NotNull NlComponent component) {
    myHandler.clearAttributes(component);
  }

  @Override
  public ComponentProvider getComponentProvider(@NotNull SceneComponent component) {
    return myHandler.getComponentProvider(component);
  }

  @Override
  public void performDrop(@NotNull NlModel model,
                          @NotNull DropTargetDropEvent event,
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
  public boolean onCreate(@NotNull ViewEditor editor,
                          @Nullable NlComponent parent,
                          @NotNull NlComponent newChild,
                          @NotNull InsertType insertType) {
    return myHandler.onCreate(editor, parent, newChild, insertType);
  }

  @Override
  public boolean paintConstraints(@NotNull ScreenView screenView, @NotNull Graphics2D graphics, @NotNull NlComponent component) {
    return myHandler.paintConstraints(screenView, graphics, component);
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
  public void addPopupMenuActions(@NotNull List<ViewAction> actions) {
    myHandler.addPopupMenuActions(actions);
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
  public Icon getLargeIcon(@NotNull String tagName) {
    return myHandler.getLargeIcon(tagName);
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
  public List<Target> createTargets(@NotNull SceneComponent sceneComponent, boolean isParent) {
    return myHandler.createTargets(sceneComponent, isParent);
  }

  @NotNull
  public ViewHandler getDelegateHandler() {
    return myHandler;
  }
}
