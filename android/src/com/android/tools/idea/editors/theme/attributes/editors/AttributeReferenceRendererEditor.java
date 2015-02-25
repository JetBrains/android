/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.android.tools.idea.editors.theme.attributes.editors;

import com.android.tools.idea.editors.theme.EditedStyleItem;
import com.intellij.openapi.project.Project;
import com.intellij.ui.TextFieldWithAutoCompletion;
import com.intellij.util.ui.AbstractTableCellEditor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.table.TableCellRenderer;
import java.awt.*;
import java.awt.event.*;
import java.util.List;

/**
 * Cell editor that allows editing references in styles. It also allows providing auto-complete suggestions.
 */
public class AttributeReferenceRendererEditor extends AbstractTableCellEditor implements TableCellRenderer {
  protected final JPanel myPanel = new JPanel();
  protected final JLabel myLabel = new JLabel();
  protected final JButton myEditButton = new JButton();
  protected final TextFieldWithAutoCompletion<String> myTextField;
  protected final CompletionProvider myCompletionProvider;
  protected EditedStyleItem myEditValue;
  protected String myStringValue;
  protected boolean myAreDetailsActive;

  /**
   * Constructs a new <code>AttributeReferenceRendererEditor</code> with optional auto-completion.
   * @param listener listener to be called when the details button is pressed.
   * @param project the project to be used for auto-completion.
   * @param completionProvider an optional {@link CompletionProvider} to provide the completion suggestions.
   */
  public AttributeReferenceRendererEditor(@Nullable final ClickListener listener,
                                          @Nullable Project project,
                                          @Nullable CompletionProvider completionProvider) {
    if (project != null) {
      myCompletionProvider = completionProvider;
    } else {
      myCompletionProvider = null;
    }

    myTextField = new TextFieldWithAutoCompletion<String>(project, TextFieldWithAutoCompletion.EMPTY_COMPLETION, true, null) {
      @Override
      protected boolean processKeyBinding(KeyStroke ks, KeyEvent e, int condition, boolean pressed) {
        if (e.getKeyCode() == KeyEvent.VK_ENTER) {
          stopCellEditing();
          return true;
        }
        return false;
      }
    };

    myPanel.setLayout(new BoxLayout(myPanel, BoxLayout.LINE_AXIS));
    myPanel.add(myTextField);
    myPanel.add(Box.createHorizontalGlue());
    myPanel.add(myEditButton);
    myTextField.setAlignmentX(Component.LEFT_ALIGNMENT);
    myTextField.setOneLineMode(true);
    myEditButton.setAlignmentX(Component.RIGHT_ALIGNMENT);
    myEditButton.setText("...");
    int buttonWidth = myEditButton.getFontMetrics(myEditButton.getFont()).stringWidth("...") + 10;
    myEditButton.setPreferredSize(new Dimension(buttonWidth, myEditButton.getHeight()));
    myLabel.setOpaque(true); // Allows for colored background

    if (listener != null) {
      myEditButton.addActionListener(new ActionListener() {
        @Override
        public void actionPerformed(ActionEvent actionEvent) {
          AttributeReferenceRendererEditor.this.cancelCellEditing();
          if (myEditValue == null) {
            return;
          }

          cancelCellEditing();

          listener.clicked(myEditValue);
        }
      });
      setAreDetailsActive(true);
    } else {
      // No listener, so no need for details button.
      setAreDetailsActive(false);
    }
  }

  public AttributeReferenceRendererEditor(@Nullable Project project, @Nullable CompletionProvider completionProvider) {
    this(null, project, completionProvider);
  }

  public AttributeReferenceRendererEditor(@NotNull final ClickListener listener) {
    this(listener, null, null);
  }

  @Override
  public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
    if (!(value instanceof EditedStyleItem)) {
      return null;
    }

    myLabel.setFont(table.getFont());
    myLabel.setText(((EditedStyleItem)value).getValue());

    return myLabel;
  }

  @Override
  public Component getTableCellEditorComponent(JTable table, Object value, boolean isSelected, int row, int column) {
    if (!(value instanceof EditedStyleItem)) {
      return null;
    }

    myEditButton.setVisible(myAreDetailsActive);
    EditedStyleItem item = (EditedStyleItem)value;
    myEditValue = item;
    myStringValue = item.getRawXmlValue();

    myTextField.setText(myStringValue);
    myTextField.setFont(table.getFont());

    if (myCompletionProvider != null) {
      myTextField.setVariants(myCompletionProvider.getCompletions(item));
    }

    myPanel.setBackground(isSelected ? table.getSelectionBackground() : table.getBackground());

    return myPanel;
  }

  @Override
  public Object getCellEditorValue() {
    return myTextField.getText();
  }

  public void setAreDetailsActive(boolean areDetailsActive) {
    myAreDetailsActive = areDetailsActive;
  }

  public interface ClickListener {
    void clicked(@NotNull EditedStyleItem value);
  }

  public interface CompletionProvider {
    /**
     * Returns the available completions for the given attribute.
     */
    @NotNull
    List<String> getCompletions(@NotNull EditedStyleItem value);
  }
}
