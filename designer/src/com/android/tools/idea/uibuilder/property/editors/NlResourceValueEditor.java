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

import com.android.resources.ResourceType;
import com.android.tools.idea.uibuilder.property.NlResourceItem;
import com.android.tools.idea.uibuilder.property.editors.support.Quantity;
import com.android.tools.idea.uibuilder.property.editors.support.TextEditorWithAutoCompletion;
import com.android.tools.idea.uibuilder.property.ptable.PTableCellEditor;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.awt.event.KeyEvent;
import java.util.EnumSet;

public class NlResourceValueEditor extends PTableCellEditor {
  private final TextEditorWithAutoCompletion myTextEditorWithAutoCompletion;
  private NlResourceItem myItem;
  private boolean myCompletionsUpdated;

  public static NlResourceValueEditor createForSliceTable(@NotNull Project project) {
    return new NlResourceValueEditor(project);
  }

  private NlResourceValueEditor(@NotNull Project project) {
    //noinspection UseDPIAwareInsets
    myTextEditorWithAutoCompletion = TextEditorWithAutoCompletion.create(project, new Insets(0, 0, 0, 0));
    myTextEditorWithAutoCompletion.registerKeyboardAction(event -> enter(),
                                                          KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0),
                                                          JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);
    myTextEditorWithAutoCompletion.registerKeyboardAction(event -> cancel(),
                                                          KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
                                                          JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);
    myTextEditorWithAutoCompletion.addFocusListener(new FocusListener() {
      @Override
      public void focusGained(@NotNull FocusEvent event) {
        if (!myCompletionsUpdated) {
          myTextEditorWithAutoCompletion.updateCompletionsFromTypes(myItem.getFacet(), getResourceTypes());
          myCompletionsUpdated = true;
        }
        myTextEditorWithAutoCompletion.selectAll();
      }

      @Override
      public void focusLost(@NotNull FocusEvent event) {
        enter();
        // Remove the selection after we lose focus for feedback on which editor is the active editor
        myTextEditorWithAutoCompletion.removeSelection();
      }
    });
  }

  @NotNull
  private EnumSet<ResourceType> getResourceTypes() {
    return myItem != null ? EnumSet.of(myItem.getResourceItem().getType()) : EnumSet.noneOf(ResourceType.class);
  }

  private void cancel() {
    // Update the selected value for immediate feedback from resource editor.
    myTextEditorWithAutoCompletion.setText(myItem != null ? myItem.getValue() : null);
    myTextEditorWithAutoCompletion.selectAll();
    cancelCellEditing();
  }

  private void enter() {
    // Select all the text to give visual confirmation that the value has been applied.
    if (myTextEditorWithAutoCompletion.editorHasFocus()) {
      myTextEditorWithAutoCompletion.selectAll();
    }
    saveValue(getCellEditorValue());
    stopCellEditing();
  }

  private void saveValue(@NotNull String value) {
    myItem.setValue(value);
  }

  @Override
  public Component getTableCellEditorComponent(JTable table, Object value, boolean isSelected, int row, int column) {
    myItem = (NlResourceItem)value;
    myTextEditorWithAutoCompletion.setText(myItem != null ? myItem.getValue() : null);
    return myTextEditorWithAutoCompletion;
  }

  @Override
  public String getCellEditorValue() {
    if (myItem == null) {
      return null;
    }
    String text = myTextEditorWithAutoCompletion.getDocument().getText();
    return Quantity.addUnit(myItem.getResourceItem().getType(), text);
  }
}
