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

import com.android.tools.idea.gradle.editor.entity.AbstractSimpleGradleEditorEntity;
import com.android.tools.idea.gradle.editor.metadata.StdGradleEditorEntityMetaData;
import com.intellij.openapi.externalSystem.util.Order;
import com.intellij.openapi.project.Project;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBPanel;
import com.intellij.util.ui.GridBag;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.lang.ref.WeakReference;
import java.util.List;

@Order(GradleEditorUiConstants.DEFAULT_ENTITY_UI_ORDER)
public class SimpleGradleEntityUi implements GradleEditorEntityUi<AbstractSimpleGradleEditorEntity> {

  private final MyComponent myRenderer = new MyComponent();
  private final MyComponent myEditor = new MyComponent();
  private final MyComponent mySizeComponent = new MyComponent();

  @NotNull
  @Override
  public Class<AbstractSimpleGradleEditorEntity> getTargetEntityClass() {
    return AbstractSimpleGradleEditorEntity.class;
  }

  @NotNull
  @Override
  public JComponent getComponent(@Nullable JComponent component,
                                 @NotNull JTable table,
                                 @NotNull AbstractSimpleGradleEditorEntity entity,
                                 @NotNull Project project,
                                 boolean editing,
                                 boolean isSelected,
                                 boolean hasFocus,
                                 boolean sizeOnly,
                                 int row,
                                 int column) {
    if (component != null) {
      // Renounce if another UI provides more specific component as this is a core UI implementation and it's
      // possible that particular plugin wants to change it into something more specific.
      return component;
    }
    final MyComponent c;
    if (sizeOnly) {
      c = mySizeComponent;
      // com.intellij.openapi.ui.ComboBoxWithWidePopup strips representation value string (extracted from combo-box
      // model) if combo-box size is not set and the string is long enough
      // (see AdjustingListCellRenderer.getListCellRendererComponent()). That means that we have different sizes
      // for 'editor' component and 'size only editor' component if contained value is long enough (the editor
      // has defined value because it's actually shown to the end-user). That's why we explicitly set combo-box
      // value here.
      c.editorSimpleValue.setSize(new Dimension(table.getSize().width, table.getRowHeight(row)));
    }
    else {
      c = editing ? myEditor : myRenderer;
    }
    c.bind(entity, project, table, editing);
    c.getPreferredSize();
    return c;
  }

  @Nullable
  @Override
  public String flush(@NotNull AbstractSimpleGradleEditorEntity entity) {
    if (!myEditor.editorSimpleValue.isVisible()) {
      // We don't want to modify underlying *.gradle files if the entity uses referenced value.
      return null;
    }
    Object selected = myEditor.editorSimpleValue.getEditor().getItem();
    if (selected != null) {
      return entity.changeValue(selected.toString());
    }
    return null;
  }

  private static class MyComponent extends JBPanel {

    private final JBLabel myName = new JBLabel();
    private final JBLabel mySimpleValue = new JBLabel();
    private final ReferencedValuesGradleEditorComponent myRefValue = new ReferencedValuesGradleEditorComponent();
    private final DefaultComboBoxModel myEditorSimpleValueModel = new DefaultComboBoxModel();
    final GradleEditorComboBox editorSimpleValue = new GradleEditorComboBox(myEditorSimpleValueModel);
    @Nullable private WeakReference<JTable> myTableRef;

    MyComponent() {
      super(new GridBagLayout());
      GridBag constraints = new GridBag().anchor(GridBagConstraints.WEST);
      add(myName, constraints);
      add(editorSimpleValue, constraints);
      add(mySimpleValue, constraints.insets(0, GradleEditorUiConstants.VALUE_INSET, 0, 0));
      add(myRefValue, constraints);

      setBackground(GradleEditorUiConstants.BACKGROUND_COLOR);
      editorSimpleValue.setBackground(GradleEditorUiConstants.BACKGROUND_COLOR);
      editorSimpleValue.setEditable(true);

      addMouseListener(new MouseAdapter() {
        @Override
        public void mouseClicked(MouseEvent e) {
          if (myTableRef == null) {
            return;
          }
          JTable table = myTableRef.get();
          if (table == null) {
            return;
          }
          Component c = SwingUtilities.getDeepestComponentAt(MyComponent.this, e.getX(), e.getY());
          if (c == null || (c != myRefValue && c != editorSimpleValue)) {
            Component editorComponent = table.getEditorComponent();
            if (editorComponent != null && UIUtil.isAncestor(editorComponent, MyComponent.this)) {
              table.getCellEditor().stopCellEditing();
            }
          }
        }
      });
    }

    public void bind(@NotNull AbstractSimpleGradleEditorEntity entity, @NotNull Project project, @NotNull JTable table, boolean editing) {
      myTableRef = new WeakReference<JTable>(table);
      myName.setText(entity.getName() + ":");
      String currentValue = entity.getCurrentValue();
      mySimpleValue.setText(currentValue);
      myRefValue.bind(project, entity.getDefinitionValueSourceBindings());

      boolean simpleValue = !currentValue.isEmpty()
                            && (entity.getDefinitionValueSourceBindings().size() == 1
                                || entity.getMetaData().contains(StdGradleEditorEntityMetaData.READ_ONLY));
      mySimpleValue.setVisible(!editing && simpleValue);
      editorSimpleValue.setVisible(editing && simpleValue);
      editorSimpleValue.setTable(table);
      myRefValue.setVisible(!simpleValue);
      if (editing && simpleValue) {
        myEditorSimpleValueModel.removeAllElements();
        List<String> versions = entity.getValueManager().hintAvailableVersions();// Trigger versions loading if necessary.
        if (versions == null) {
          myEditorSimpleValueModel.addElement(currentValue);
        }
        else {
          boolean currentValueIsInAvailableList = versions.contains(currentValue);
          if (!currentValueIsInAvailableList) {
            myEditorSimpleValueModel.addElement(currentValue);
          }
          for (String version : versions) {
            myEditorSimpleValueModel.addElement(version);
          }
        }
      }
      editorSimpleValue.setSelectedItem(currentValue);
    }
  }
}
