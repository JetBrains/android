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
import com.android.tools.idea.uibuilder.property.ptable.PTableCellEditor;
import com.intellij.openapi.project.Project;
import org.jetbrains.android.dom.attrs.AttributeDefinition;
import org.jetbrains.android.dom.attrs.AttributeFormat;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.Set;

import static com.android.tools.idea.uibuilder.property.editors.NlEditingListener.DEFAULT_LISTENER;

public class NlPropertyEditors {
  private static NlBooleanTableCellEditor ourBooleanEditor;
  private static NlFlagTableCellEditor ourFlagEditor;
  private static NlEnumTableCellEditor ourComboEditor;
  private static NlReferenceTableCellEditor ourDefaultEditor;

  public enum EditorType {DEFAULT, BOOLEAN, FLAG, COMBO}

  @NotNull
  public static EditorType getEditorType(@NotNull NlProperty property) {
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
    if (isBoolean == Boolean.TRUE) {
      return EditorType.BOOLEAN;
    }
    else {
      return EditorType.DEFAULT;
    }
  }

  public static PTableCellEditor get(@NotNull NlProperty property) {
    switch (getEditorType(property)) {
      case BOOLEAN:
        return getBooleanEditor();
      case FLAG:
        return getFlagEditor();
      case COMBO:
        return getComboEditor();
      default:
        return getDefaultEditor(property.getModel().getProject());
    }
  }

  public static NlComponentEditor create(@NotNull NlProperty property) {
    switch (getEditorType(property)) {
      case BOOLEAN:
        return NlBooleanEditor.createForInspector(DEFAULT_LISTENER);
      case FLAG:
        return NlFlagsEditor.create();
      case COMBO:
        return NlEnumEditor.createForInspector(NlEnumEditor.getDefaultListener());
      default:
        return NlReferenceEditor.createForInspectorWithBrowseButton(property.getModel().getProject(), DEFAULT_LISTENER);
    }
  }

  private static PTableCellEditor getBooleanEditor() {
    if (ourBooleanEditor == null) {
      ourBooleanEditor = new NlBooleanTableCellEditor();
    }

    return ourBooleanEditor;
  }

  public static PTableCellEditor getFlagEditor() {
    if (ourFlagEditor == null) {
      ourFlagEditor = new NlFlagTableCellEditor();
    }

    return ourFlagEditor;
  }

  private static PTableCellEditor getComboEditor() {
    if (ourComboEditor == null) {
      ourComboEditor = new NlEnumTableCellEditor();
    }

    return ourComboEditor;
  }

  private static PTableCellEditor getDefaultEditor(Project project) {
    if (ourDefaultEditor == null) {
      ourDefaultEditor = new NlReferenceTableCellEditor(project);
    }

    return ourDefaultEditor;
  }
}
