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

import com.android.tools.idea.gradle.editor.entity.GradleEditorEntity;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * UI component which represents particular {@link GradleEditorEntity}.
 *
 * @param <T>
 */
public interface GradleEditorEntityUi<T extends GradleEditorEntity> {

  ExtensionPointName<GradleEditorEntityUi<?>> EP_NAME = ExtensionPointName.create("com.android.gradle.gradleEditorEntityUi");

  @NotNull
  Class<T> getTargetEntityClass();

  /**
   * Current component is used either as a renderer or editor by the {@link GradleEditorEntityTable} and this method allows to obtain
   * actual UI control to use.
   * <p/>
   * <b>Note:</b> it's important to use different renderer ('editing' flag is false) and editor ('editing' flag is true) components
   * because we encountered a situation when particular entity is being rendered but swing infrastructure asks for the same cell's
   * renderer for its internal purposes, so, when the same object is used either as a renderer or editor, it shows the cell in incorrect
   * state after that.
   *
   * @param component   component to be used for rendering target entity (if any) - actual when more than one {@link GradleEditorEntityUi}
   *                    is registered for the same entity type. The first UI receives <code>null</code> as this argument then and
   *                    result of calling this method on it is used as this argument when calling the second UI etc
   * @param table       target table where target entity is rendered/edited
   * @param entity      target entity which should be rendered by the current UI
   * @param project     current project
   * @param editing     a flag which determines if given entity is used at the renderer or editor
   * @param isSelected  flag which identifies if table's row which holds target gradle entity is selected
   * @param hasFocus    flag which identifies if table's row which holds target gradle entity has focus
   * @param sizeOnly    flag which identifies if returned component will be used only for obtaining
   *                    {@link JComponent#getPreferredSize() size}. The general idea is that we might want to know how much size
   *                    an editor component requires during preparing a renderer component - the editor might already be used
   *                    at the table and we can't afford to modify it's state, so, we might provide <code>'true'</code> here
   *                    as an indication that a separate non-editor component should be initialized by the given entity and returned
   * @param row         given table's row which points to the given value
   * @param column      given table's column which points to the given value
   * @return            UI component which will be used as a renderer/editor
   */
  @NotNull
  JComponent getComponent(@Nullable JComponent component,
                          @NotNull JTable table,
                          @NotNull T entity,
                          @NotNull Project project,
                          boolean editing,
                          boolean isSelected,
                          boolean hasFocus,
                          boolean sizeOnly,
                          int row,
                          int column);

  /**
   * Asks current UI to flush data modified by user at the editor UI (if any) to the given entity.
   *
   * @param entity  target entity where current UI should flush changes entered by end-user at the editor UI (if any)
   * @return        <code>null</code> string as an indication of target editor UI values are successfully flushed to the given entity;
   *                error message otherwise
   */
  @Nullable
  String flush(@NotNull T entity);
}
