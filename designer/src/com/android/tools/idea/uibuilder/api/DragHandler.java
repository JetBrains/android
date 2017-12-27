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
package com.android.tools.idea.uibuilder.api;

import com.android.ide.common.repository.GradleCoordinate;
import com.android.tools.idea.common.model.AndroidCoordinate;
import com.android.tools.idea.common.model.AndroidDpCoordinate;
import com.android.tools.idea.common.model.NlComponent;
import com.android.tools.idea.common.model.NlModel;
import com.android.tools.idea.common.scene.SceneComponent;
import com.android.tools.idea.gradle.dependencies.GradleDependencyManager;
import com.android.tools.idea.gradle.dsl.model.GradleBuildModel;
import com.android.tools.idea.uibuilder.graphics.NlGraphics;
import com.android.tools.idea.uibuilder.model.NlComponentHelperKt;
import com.intellij.openapi.module.Module;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Handler involved in drag &amp; drop operations. Subclassed and returned by
 * {@link ViewGroupHandler#createDragHandler} for view groups that allow their
 * children to be reconfigured by drag &amp; drop.
 */
public abstract class DragHandler {
  @NotNull protected final ViewEditor editor;
  @NotNull protected final ViewGroupHandler handler;
  @NotNull protected final List<NlComponent> components;
  @NotNull protected SceneComponent layout;
  @NotNull protected DragType type = DragType.COPY;
  @AndroidDpCoordinate protected int startX;
  @AndroidDpCoordinate protected int startY;
  @AndroidDpCoordinate protected int lastX;
  @AndroidDpCoordinate protected int lastY;
  protected int lastModifiers;

  /**
   * Constructs a new drag handler for the given view handler
   *
   * @param editor     the associated IDE editor
   * @param handler    the view group handler that may receive the dragged components
   * @param layout     the layout being dragged over/into
   * @param components the components being dragged
   * @param type       the <b>initial</b> type of drag, which can change along the way
   */
  protected DragHandler(@NotNull ViewEditor editor,
                        @NotNull ViewGroupHandler handler,
                        @NotNull SceneComponent layout,
                        @NotNull List<NlComponent> components,
                        @NotNull DragType type) {
    this.editor = editor;
    this.handler = handler;
    this.layout = layout;
    this.components = components;
    this.type = type;
  }

  /**
   * Sets new drag type. This can happen during a drag (e.g. when the user presses a
   * modifier key.
   *
   * @param type the new type to use
   */
  public void setDragType(@NotNull DragType type) {
    this.type = type;
  }

  /**
   * Aborts a drag in this handler's view
   */
  public void cancel() {
  }

  /**
   * Finishes a drag to the given coordinate
   *
   * @param x         the x coordinate in the Android screen pixel coordinate system
   * @param y         the y coordinate in the Android screen pixel coordinate system
   * @param modifiers the modifier key state
   */
  public void commit(@AndroidCoordinate int x, @AndroidCoordinate int y, int modifiers, @NotNull InsertType insertType) {
    insertComponents(-1, insertType);
  }

  /**
   * Starts a drag of the given components from the given position
   *
   * @param x         the x coordinate in the Android screen pixel coordinate system
   * @param y         the y coordinate in the Android screen pixel coordinate system
   * @param modifiers the modifier key state
   */
  public void start(@AndroidDpCoordinate int x, @AndroidDpCoordinate int y, int modifiers) {
    startX = x;
    startY = y;
    lastModifiers = modifiers;
  }

  /**
   * Continues a drag of the given components from the given position. Will always come after a call to {@link #start}.
   *
   * @param x         the x coordinate in the Android screen pixel coordinate system
   * @param y         the y coordinate in the Android screen pixel coordinate system
   * @param modifiers the modifier key state
   * @return null if the drag is successful so far, or an empty string (or a short error
   * message describing the problem to be shown to the user) if not
   */
  @Nullable
  public String update(@AndroidDpCoordinate int x, @AndroidDpCoordinate int y, int modifiers) {
    lastX = x;
    lastY = y;
    lastModifiers = modifiers;
    return null;
  }

  /**
   * Paints the drag feedback during the drag &amp; drop operation
   *
   * @param graphics the graphics to buildDisplayList to
   */
  public void paint(@NotNull NlGraphics graphics) {
  }

  /**
   * If the components have dependencies that are not met by the project, this method will add them after asking the developer.
   *
   * @return true if the dragged components can be inserted into this layout
   */
  // TODO Move this to ViewEditor
  protected final boolean canInsertComponents(int insertIndex, @NotNull InsertType insertType) {
    NlModel model = editor.getModel();

    if (!model.canAddComponents(components, layout.getNlComponent(), getChild(insertIndex))) {
      return false;
    }

    Collection<GradleCoordinate> dependencies = getMissingDependencies(components);

    if (dependencies.isEmpty()) {
      return true;
    }

    return GradleDependencyManager.userWantToAddDependencies(model.getModule(), dependencies);
  }

  /**
   * Inserts the dragged components into this layout. This method will add missing dependencies without prompting the developer. Call
   * canInsertComponents if you want to ask first.
   *
   * @param insertIndex the position to drop the dragged components at, or -1 to append them at the end.
   *                    The index refers to the position of the children <b>before</b> the drag, which
   *                    matters if some of the existing children in the layout are being dragged.
   * @param insertType  the type of move/insert
   */
  // TODO Move this to ViewEditor
  protected final void insertComponents(int insertIndex, @NotNull InsertType insertType) {
    addMissingDependencies();
    editor.getModel().addComponents(components, layout.getNlComponent(), getChild(insertIndex), insertType);
  }

  private void addMissingDependencies() {
    List<GradleCoordinate> dependencies = getMissingDependencies(components);

    if (dependencies.isEmpty()) {
      return;
    }

    Module module = editor.getModel().getModule();
    GradleBuildModel model = GradleBuildModel.get(module);

    if (model == null) {
      return;
    }

    GradleDependencyManager.addDependenciesInTransaction(model, module, dependencies, null);
  }

  @Nullable
  private NlComponent getChild(int i) {
    return 0 <= i && i < layout.getChildCount() ? layout.getNlComponent().getChild(i) : null;
  }

  @NotNull
  private List<GradleCoordinate> getMissingDependencies(@NotNull Iterable<NlComponent> components) {
    Set<String> artifacts = new HashSet<>();
    components.forEach(component -> NlComponentHelperKt.getDependencies(component, artifacts));

    List<GradleCoordinate> dependencies = artifacts.stream()
      .map(artifact -> GradleCoordinate.parseCoordinateString(artifact + ":+"))
      .filter(Objects::nonNull)
      .collect(Collectors.toList());

    if (dependencies.isEmpty()) {
      return dependencies;
    }

    Module module = editor.getModel().getModule();
    return GradleDependencyManager.getInstance(module.getProject()).findMissingDependencies(module, dependencies);
  }
}