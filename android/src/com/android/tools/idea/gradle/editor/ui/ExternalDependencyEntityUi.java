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

import com.android.tools.idea.gradle.editor.entity.ExternalDependencyGradleEditorEntity;
import com.intellij.openapi.externalSystem.util.Order;
import com.intellij.openapi.project.Project;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBPanel;
import com.intellij.util.ui.GridBag;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

/**
 * {@link GradleEditorEntityUi} for {@link ExternalDependencyGradleEditorEntity}.
 */
@Order(GradleEditorUiConstants.DEFAULT_ENTITY_UI_ORDER)
public class ExternalDependencyEntityUi implements GradleEditorEntityUi<ExternalDependencyGradleEditorEntity> {

  private final MyComponent myRenderer = new MyComponent();
  private final MyComponent myEditor = new MyComponent();
  private final MyComponent mySizeComponent = new MyComponent();

  @NotNull
  @Override
  public Class<ExternalDependencyGradleEditorEntity> getTargetEntityClass() {
    return ExternalDependencyGradleEditorEntity.class;
  }

  @NotNull
  @Override
  public JComponent getComponent(@Nullable JComponent component,
                                 @NotNull JTable table,
                                 @NotNull ExternalDependencyGradleEditorEntity entity,
                                 @NotNull Project project,
                                 boolean editing,
                                 boolean isSelected,
                                 boolean hasFocus,
                                 boolean sizeOnly,
                                 int row,
                                 int column) {
    if (component != null) {
      // Renounce if another UI provides more specific component as this is a core UI implementation and it's possible that particular
      // plugin wants to change it into something more specific.
      return component;
    }

    MyComponent c = sizeOnly ? mySizeComponent : (editing ? myEditor : myRenderer);
    c.bind(entity, project, table, editing);
    return c;
  }

  @Nullable
  @Override
  public String flush(@NotNull ExternalDependencyGradleEditorEntity entity) {
    if (!myEditor.editorVersion.isVisible()) {
      return null;
    }
    Object item = myEditor.editorVersion.getEditor().getItem();
    if (item != null) {
      return entity.changeVersion(item.toString());
    }
    return null;
  }

  private static class MyComponent extends JBPanel {

    private final JLabel mySimpleScope = new JLabel();
    private final ReferencedValuesGradleEditorComponent myRefScope = new ReferencedValuesGradleEditorComponent();
    private final JLabel mySimpleGroup = new JLabel();
    private final ReferencedValuesGradleEditorComponent myRefGroup = new ReferencedValuesGradleEditorComponent();
    private final JLabel mySimpleArtifact = new JLabel();
    private final ReferencedValuesGradleEditorComponent myRefArtifact = new ReferencedValuesGradleEditorComponent();
    private final JLabel mySimpleVersion = new JLabel();
    private final ReferencedValuesGradleEditorComponent myRefVersion = new ReferencedValuesGradleEditorComponent();
    private final DefaultComboBoxModel myEditorVersionModel = new DefaultComboBoxModel();
    final GradleEditorComboBox editorVersion = new GradleEditorComboBox(myEditorVersionModel);

    MyComponent() {
      super(new GridBagLayout());
      setBackground(GradleEditorUiConstants.BACKGROUND_COLOR);
      editorVersion.setBackground(GradleEditorUiConstants.BACKGROUND_COLOR);
      editorVersion.setEditable(true);
      GridBag constraints = new GridBag().anchor(GridBagConstraints.WEST);
      add(mySimpleScope, constraints);
      add(myRefScope, constraints);
      add(new JBLabel(" "), constraints); // Separate scope from group
      add(mySimpleGroup, constraints);
      add(myRefGroup, constraints);
      add(new JBLabel(":"), constraints);
      add(mySimpleArtifact, constraints);
      add(myRefArtifact, constraints);
      add(new JBLabel(":"), constraints);
      add(mySimpleVersion, constraints);
      add(myRefVersion, constraints);
      add(editorVersion, constraints);
    }

    public void bind(@NotNull ExternalDependencyGradleEditorEntity entity,
                     @NotNull Project project,
                     @NotNull JTable table,
                     boolean editing) {
      boolean refScope = entity.getScope().isEmpty();
      mySimpleScope.setVisible(!refScope);
      myRefScope.setVisible(refScope);
      mySimpleScope.setText(entity.getScope());
      myRefScope.bind(project, entity.getScopeBindings());

      boolean refGroup = entity.getGroupId().isEmpty();
      mySimpleGroup.setVisible(!refGroup);
      myRefGroup.setVisible(refGroup);
      mySimpleGroup.setText(entity.getGroupId());
      myRefGroup.bind(project, entity.getGroupIdSourceBindings());

      boolean refArtifact = entity.getArtifactId().isEmpty();
      mySimpleArtifact.setVisible(!refArtifact);
      myRefArtifact.setVisible(refArtifact);
      mySimpleArtifact.setText(entity.getArtifactId());
      myRefArtifact.bind(project, entity.getArtifactIdSourceBindings());

      boolean refVersion = entity.getVersion().isEmpty();
      mySimpleVersion.setVisible(!editing && !refVersion);
      myRefVersion.setVisible(refVersion);
      editorVersion.setVisible(editing && !refVersion);
      editorVersion.setTable(table);
      mySimpleVersion.setText(entity.getVersion());
      myRefVersion.bind(project, entity.getVersionSourceBindings());
      if (editing && !refVersion) {
        myEditorVersionModel.removeAllElements();
        myEditorVersionModel.addElement(entity.getVersion());
        entity.getVersionValueManager().hintAvailableVersions(); // Trigger versions loading if necessary.
      }
    }
  }
}
