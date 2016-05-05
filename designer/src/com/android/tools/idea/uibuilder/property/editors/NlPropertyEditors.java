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

import java.util.Set;

public class NlPropertyEditors {
  private static NlBooleanTableCellEditor ourBooleanEditor;
  private static NlFlagEditor ourFlagEditor;
  private static NlEnumTableCellEditor ourComboEditor;
  private static NlReferenceTableCellEditor ourDefaultEditor;

  public static PTableCellEditor get(@NotNull NlProperty property) {
    AttributeDefinition definition = property.getDefinition();
    if (definition == null) {
      // TODO: default to text editor
      return null;
    }

    Set<AttributeFormat> formats = definition.getFormats();
    if (formats.isEmpty()) {
      // either we don't know the format or we support multiple formats
      // TODO: default to text editor
      return null;
    }

    // TODO: there can be more than one format, we need to make this more customizable
    if (formats.contains(AttributeFormat.Boolean)) {
      return getBooleanEditor();
    } else if (formats.contains(AttributeFormat.Enum)) {
      return getComboEditor();
    }

    return getDefaultEditor(property.getModel().getProject());
  }

  private static PTableCellEditor getBooleanEditor() {
    if (ourBooleanEditor == null) {
      ourBooleanEditor = new NlBooleanTableCellEditor();
    }

    return ourBooleanEditor;
  }

  public static PTableCellEditor getFlagEditor() {
    if (ourFlagEditor == null) {
      ourFlagEditor = new NlFlagEditor();
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
