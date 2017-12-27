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
package com.android.tools.idea.uibuilder.property.editors;

import com.android.tools.idea.uibuilder.property.NlProperty;
import com.android.tools.idea.uibuilder.property.editors.support.EnumSupportFactory;
import com.android.tools.adtui.ptable.PTableCellEditor;
import com.android.tools.adtui.ptable.PTableCellEditorProvider;
import com.android.tools.adtui.ptable.PTableItem;
import com.intellij.ide.ui.LafManager;
import com.intellij.ide.ui.LafManagerListener;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.project.Project;
import org.jetbrains.android.dom.attrs.AttributeDefinition;
import org.jetbrains.android.dom.attrs.AttributeFormat;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.Set;

import static com.android.SdkConstants.ATTR_STYLE;
import static com.android.tools.idea.uibuilder.property.editors.NlEditingListener.DEFAULT_LISTENER;

public class NlPropertyEditors implements PTableCellEditorProvider, ProjectComponent, LafManagerListener {
  private Project myProject;
  private NlTableCellEditor myBooleanEditor;
  private NlTableCellEditor myFlagEditor;
  private NlTableCellEditor myComboEditor;
  private NlTableCellEditor myDefaultEditor;

  public enum EditorType {DEFAULT, BOOLEAN, FLAG, COMBO, COMBO_WITH_BROWSE}

  @NotNull
  public static NlPropertyEditors getInstance(@NotNull Project project) {
    return project.getComponent(NlPropertyEditors.class);
  }

  private NlPropertyEditors(@NotNull Project project) {
    myProject = project;
  }

  @NotNull
  @Override
  public PTableCellEditor getCellEditor(@NotNull PTableItem item, int column) {
    if (!(item instanceof NlProperty)) {
      return NlNoEditor.getInstance();
    }
    switch (getEditorType((NlProperty)item)) {
      case BOOLEAN:
        return getBooleanEditor();
      case FLAG:
        return getMyFlagEditor();
      case COMBO:
      case COMBO_WITH_BROWSE:
        return getMyComboEditor();
      default:
        return getDefaultEditor();
    }
  }

  @NotNull
  public NlComponentEditor create(@NotNull NlProperty property) {
    switch (getEditorType(property)) {
      case BOOLEAN:
        return NlBooleanEditor.createForInspector(DEFAULT_LISTENER);
      case FLAG:
        return NlFlagsEditor.create();
      case COMBO:
        return NlEnumEditor.createForInspector(DEFAULT_LISTENER);
      case COMBO_WITH_BROWSE:
        return NlEnumEditor.createForInspectorWithBrowseButton(DEFAULT_LISTENER);
      default:
        return NlReferenceEditor.createForInspectorWithBrowseButton(property.getModel().getProject(), DEFAULT_LISTENER);
    }
  }

  private void resetCachedEditors() {
    myBooleanEditor = null;
    myFlagEditor = null;
    myComboEditor = null;
    myDefaultEditor = null;
  }

  @NotNull
  private static EditorType getEditorType(@NotNull NlProperty property) {
    AttributeDefinition definition = property.getDefinition();
    Set<AttributeFormat> formats = definition != null ? definition.getFormats() : Collections.emptySet();
    Boolean isBoolean = null;
    for (AttributeFormat format : formats) {
      switch (format) {
        case Boolean:
          if (isBoolean == null) {
            isBoolean = Boolean.TRUE;
          }
          break;
        case String:
        case Color:
        case Dimension:
        case Integer:
        case Float:
        case Fraction:
          if (isBoolean == null) {
            isBoolean = Boolean.FALSE;
          }
          break;
        case Enum:
          return EditorType.COMBO;
        case Flag:
          return EditorType.FLAG;
        default:
          break;
      }
    }
    // Do not inline this method. Other classes should not know about EnumSupportFactory.
    if (isBoolean == Boolean.TRUE) {
      return EditorType.BOOLEAN;
    }
    else if (EnumSupportFactory.supportsProperty(property)) {
      if (property.getName().equals(ATTR_STYLE)) {
        return EditorType.COMBO_WITH_BROWSE;
      }
      return EditorType.COMBO;
    }
    return EditorType.DEFAULT;
  }

  private PTableCellEditor getBooleanEditor() {
    if (myBooleanEditor == null) {
      myBooleanEditor = NlBooleanEditor.createForTable();
    }

    return myBooleanEditor;
  }

  public PTableCellEditor getMyFlagEditor() {
    if (myFlagEditor == null) {
      myFlagEditor = NlFlagEditor.createForTable();
    }

    return myFlagEditor;
  }

  private PTableCellEditor getMyComboEditor() {
    if (myComboEditor == null) {
      myComboEditor = NlEnumEditor.createForTable();
    }

    return myComboEditor;
  }

  private PTableCellEditor getDefaultEditor() {
    if (myDefaultEditor == null) {
      myDefaultEditor = NlReferenceEditor.createForTable(myProject);
    }

    return myDefaultEditor;
  }

  @Override
  public void initComponent() {
    LafManager.getInstance().addLafManagerListener(this);
  }

  @Override
  public void disposeComponent() {
    LafManager.getInstance().removeLafManagerListener(this);
  }

  @Override
  public void lookAndFeelChanged(LafManager source) {
    resetCachedEditors();
  }

  @NotNull
  @Override
  public String getComponentName() {
    return NlPropertyEditors.class.getSimpleName();
  }
}
