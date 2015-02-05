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
package com.android.tools.idea.gradle.editor.ui;

import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseEvent;

/**
 * Component which manages particular {@link GradleEditorEntityTable gradle editor table's} cell UI.
 *
 */
public interface GradleEditorCellComponent {

  /**
   * Asks current component to fill UI controls by the given value's data.
   *
   * @param table     current table
   * @param value     target value which data should be used to fill cell's UI control
   * @param project   current ide project
   * @param row       target table's row which holds given value
   * @param column    target table's column which holds given value
   * @param editing   a flag which identifies whether a renderer or editor should be returned
   * @param selected  a flag which identifies if target cell (identified by the given row and column) is selected
   * @param focus     a flag which identifies if target cell has a focus
   * @return          a UI component which state is updated according to the given value
   */
  @NotNull
  JComponent bind(@NotNull JTable table,
                  @Nullable Object value,
                  @NotNull Project project,
                  int row,
                  int column,
                  boolean editing,
                  boolean selected,
                  boolean focus);

  /**
   * Asks current component to return a value given to the previous call to the
   * {@link #bind(JTable, Object, Project, int, int, boolean, boolean, boolean)} with all changes made by the user to it via
   * UI offered by the current component.
   *
   * @param project  current ide project
   * @return         a value given to the previous call to the {@link #bind(JTable, Object, Project, int, int, boolean, boolean, boolean)}
   *                 with all changes made by the user via UI offered by the current component
   */
  @Nullable
  Object getValue(@NotNull Project project);

  /**
   * {@link JTable} re-uses renderers/editors for making UI state snapshots and using them later during table drawing, that's why we
   * can't just subscribe to mouse events at the renderer/editor component (technically we can but they wouldn't receive any updates
   * as the component is not part of the table's UI hierarchy and are excluded from event dispatching.
   * <p/>
   * That's why we manually define various <code>'onMouse...()'</code> methods here and organize renderers/editor in a special way
   * to keep the same renderer/editor on a per-cell basis.
   * <p/>
   * Current component is notified about mouse move event via this method (given event comes from the table's listener, i.e.
   * its coordinates are defined in terms of table's coordinates).
   *
   * @param event  target mouse event
   * @return       non-null rectangle in screen coordinate system as an indication that current component's state has been changed and
   *               that rectangle should be re-drawn; <code>null</code> if no re-drawing is necessary
   */
  @Nullable
  Rectangle onMouseMove(@NotNull MouseEvent event);

  /**
   * Similar to the {@link #onMouseMove(MouseEvent)} but targets <code>'mouse entered'</code> event.
   *
   * @param event  target mouse event
   * @return       non-null rectangle in screen coordinate system as an indication that current component's state has been changed and
   *               that rectangle should be re-drawn; <code>null</code> if no re-drawing is necessary
   */
  @Nullable
  Rectangle onMouseEntered(@NotNull MouseEvent event);

  /**
   * Similar to the {@link #onMouseMove(MouseEvent)} but targets <code>'mouse exited'</code> event.
   *
   * @return       non-null rectangle in screen coordinate system as an indication that current component's state has been changed and
   *               that rectangle should be re-drawn; <code>null</code> if no re-drawing is necessary
   */
  @Nullable
  Rectangle onMouseExited();
}
