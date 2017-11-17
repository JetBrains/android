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
import com.android.tools.adtui.common.AdtSecondaryPanel;
import com.android.tools.adtui.ptable.PTableCellEditor;
import com.android.tools.idea.uibuilder.property.NlResourceItem;
import com.android.tools.idea.uibuilder.property.editors.support.Quantity;
import com.android.tools.idea.uibuilder.property.editors.support.TextEditorWithAutoCompletion;
import com.android.tools.idea.uibuilder.property.renderer.NlXmlValueRenderer;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.awt.event.KeyEvent;
import java.util.EnumSet;
import java.util.List;

import static com.android.tools.idea.common.property.editors.BaseComponentEditor.*;

public class NlResourceValueEditor extends PTableCellEditor {
  private final JPanel myPanel;
  private final TextEditorWithAutoCompletion myTextEditorWithAutoCompletion;
  private NlResourceItem myItem;
  private boolean myCompletionsUpdated;

  public static NlResourceValueEditor createForSliceTable(@NotNull Project project) {
    return new NlResourceValueEditor(project);
  }

  private NlResourceValueEditor(@NotNull Project project) {
    myTextEditorWithAutoCompletion = TextEditorWithAutoCompletion.create(project, JBUI.insets(VERTICAL_SPACING + VERTICAL_PADDING,
                                                                                              HORIZONTAL_PADDING,
                                                                                              VERTICAL_SPACING + VERTICAL_PADDING,
                                                                                              HORIZONTAL_PADDING));
    myTextEditorWithAutoCompletion.registerKeyboardAction(event -> enter(),
                                                          KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0),
                                                          JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);
    myTextEditorWithAutoCompletion.registerKeyboardAction(event -> cancel(),
                                                          KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
                                                          JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);
    myTextEditorWithAutoCompletion.setFont(UIUtil.getLabelFont(UIUtil.FontSize.SMALL));
    myTextEditorWithAutoCompletion.setFontStyle(Font.BOLD);
    myTextEditorWithAutoCompletion.setTextColor(NlXmlValueRenderer.VALUE_COLOR);
    myTextEditorWithAutoCompletion.addFocusListener(new FocusListener() {
      @Override
      public void focusGained(@NotNull FocusEvent event) {
        if (!myCompletionsUpdated) {
          editorFocusGained();
        }
        // Select all when we gain focus for feedback on which editor is the active editor
        myTextEditorWithAutoCompletion.selectAll();
      }

      @Override
      public void focusLost(@NotNull FocusEvent event) {
        // Remove the selection after we lose focus for feedback on which editor is the active editor
        myTextEditorWithAutoCompletion.removeSelection();
      }
    });
    myPanel = new AdtSecondaryPanel(new BorderLayout());
    myPanel.add(myTextEditorWithAutoCompletion);
    myPanel.setFocusable(false);
  }

  @Override
  public JComponent getPreferredFocusComponent() {
    return myTextEditorWithAutoCompletion;
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
  public Component getTableCellEditorComponent(@NotNull JTable table, @Nullable Object value, boolean isSelected, int row, int column) {
    myItem = (NlResourceItem)value;
    myTextEditorWithAutoCompletion.setText(myItem != null ? myItem.getValue() : null);
    return myPanel;
  }

  @Override
  public String getCellEditorValue() {
    if (myItem == null) {
      return null;
    }
    String text = myTextEditorWithAutoCompletion.getDocument().getText();
    return Quantity.addUnit(myItem.getResourceItem().getType(), text);
  }

  private void editorFocusGained() {
    ApplicationManager.getApplication().executeOnPooledThread(() -> {
      NlResourceItem item = myItem;
      List<String> completions = TextEditorWithAutoCompletion.loadCompletions(item.getFacet(), getResourceTypes(), null);
      if (item == myItem) {
        myTextEditorWithAutoCompletion.updateCompletions(completions);
        myCompletionsUpdated = true;
      }
    });
  }
}
