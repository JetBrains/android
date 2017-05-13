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
package com.android.tools.idea.uibuilder.property.editors;

import com.android.annotations.Nullable;
import com.android.tools.idea.uibuilder.property.AddPropertyItem;
import com.android.tools.idea.uibuilder.property.NlProperty;
import com.android.tools.idea.uibuilder.property.NlResourceItem;
import com.android.tools.adtui.ptable.PTableCellEditor;
import com.android.tools.adtui.ptable.PTableCellEditorProvider;
import com.android.tools.adtui.ptable.PTableItem;
import com.intellij.ide.ui.LafManager;
import com.intellij.ide.ui.LafManagerListener;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

public class NlXmlEditors implements PTableCellEditorProvider, ProjectComponent, LafManagerListener {
  private final Project myProject;
  private NlTableCellEditor myPropertyEditor;
  private NlTableCellEditor myAddPropertyEditor;
  private NlResourceValueEditor myResourceValueEditor;

  @NotNull
  public static NlXmlEditors getInstance(@NotNull Project project) {
    return project.getComponent(NlXmlEditors.class);
  }

  private NlXmlEditors(@NotNull Project project) {
    myProject = project;
  }

  @Nullable
  @Override
  public PTableCellEditor getCellEditor(@NotNull PTableItem item, int column) {
    if (item instanceof NlProperty) {
      return getPropertyEditor();
    }
    if (item instanceof NlResourceItem) {
      return getResourceValueEditor();
    }
    if (item instanceof AddPropertyItem) {
      return column == 0 ? getAddPropertyEditor() : getPropertyEditor();
    }
    return null;
  }

  private void resetCachedEditors() {
    myPropertyEditor = null;
    myAddPropertyEditor = null;
    myResourceValueEditor = null;
  }

  private NlTableCellEditor getPropertyEditor() {
    if (myPropertyEditor == null) {
      myPropertyEditor = NlSlicePropertyEditor.create(myProject);
    }
    return myPropertyEditor;
  }

  private NlTableCellEditor getAddPropertyEditor() {
    if (myAddPropertyEditor == null) {
      myAddPropertyEditor = AddPropertyEditor.create(myProject);
    }
    return myAddPropertyEditor;
  }

  private NlResourceValueEditor getResourceValueEditor() {
    if (myResourceValueEditor == null) {
      myResourceValueEditor = NlResourceValueEditor.createForSliceTable(myProject);
    }
    return myResourceValueEditor;
  }

  @Override
  public void lookAndFeelChanged(LafManager source) {
    resetCachedEditors();
  }

  @Override
  public void projectOpened() {
  }

  @Override
  public void projectClosed() {
  }

  @Override
  public void initComponent() {
    LafManager.getInstance().addLafManagerListener(this);
  }

  @Override
  public void disposeComponent() {
    LafManager.getInstance().removeLafManagerListener(this);
  }

  @NotNull
  @Override
  public String getComponentName() {
    return NlXmlEditors.class.getSimpleName();
  }
}
