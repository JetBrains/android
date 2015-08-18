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
import com.intellij.openapi.externalSystem.util.Order;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

@Order(GradleEditorUiConstants.DEFAULT_ENTITY_UI_ORDER)
public class DummyGradleEditorEntityUi implements GradleEditorEntityUi {

  private final JLabel myLabel = new JLabel();

  @NotNull
  @Override
  public Class getTargetEntityClass() {
    return Object.class;
  }

  @NotNull
  @Override
  public JComponent getComponent(@Nullable JComponent component,
                                 @NotNull JTable table,
                                 @NotNull GradleEditorEntity entity,
                                 @NotNull Project project,
                                 boolean editing,
                                 boolean isSelected,
                                 boolean hasFocus,
                                 boolean sizeOnly,
                                 int row,
                                 int column) {
    if (component != null) {
      return component;
    }
    myLabel.setText(entity.getName());
    return myLabel;
  }

  @Nullable
  @Override
  public String flush(@NotNull GradleEditorEntity entity) {
    return null;
  }
}
