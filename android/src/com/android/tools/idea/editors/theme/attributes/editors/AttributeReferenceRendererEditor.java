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

import com.android.tools.idea.editors.theme.ThemeEditorUtils;
import com.android.tools.idea.editors.theme.datamodels.EditedStyleItem;
import com.intellij.openapi.project.Project;
import com.intellij.ui.TextFieldWithAutoCompletion;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.util.List;

/**
 * Cell editor that allows editing references in styles. It also allows providing auto-complete suggestions.
 */
public class AttributeReferenceRendererEditor extends TypedCellRendererEditor<EditedStyleItem, String> {
  protected final Box myBox = new Box(BoxLayout.LINE_AXIS);
  /** Renderer component, with isShowing overridden because of the use of a {@link CellRendererPane} */
  protected final JLabel myLabel = new JLabel() {
    @Override
    public boolean isShowing() {
      return true;
    }
  };
  protected final TextFieldWithAutoCompletion<String> myTextField;
  protected final CompletionProvider myCompletionProvider;
  protected EditedStyleItem myEditValue;
  protected String myStringValue;

  /**
   * Constructs a new <code>AttributeReferenceRendererEditor</code> with optional auto-completion.
   * @param project the project to be used for auto-completion.
   * @param completionProvider an optional {@link CompletionProvider} to provide the completion suggestions.
   */
  public AttributeReferenceRendererEditor(@Nullable Project project,
                                          @Nullable CompletionProvider completionProvider) {
    if (project != null) {
      myCompletionProvider = completionProvider;
    } else {
      myCompletionProvider = null;
    }

    //noinspection unchecked (EMPTY_COMPLETION doesn't need the generic type as it's empty)
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

    myBox.add(myTextField);
    myBox.add(Box.createHorizontalGlue());
    myTextField.setAlignmentX(Component.LEFT_ALIGNMENT);
    myTextField.setOneLineMode(true);
    myLabel.setOpaque(true); // Allows for colored background
  }

  @Override
  public Component getRendererComponent(JTable table, EditedStyleItem value, boolean isSelected, boolean hasFocus, int row, int column) {
    final Component component;
    if (column == 0) {
      component = table.getDefaultRenderer(String.class)
        .getTableCellRendererComponent(table, ThemeEditorUtils.getDisplayHtml(value), isSelected, hasFocus, row, column);
    }
    else {
      myLabel.setFont(table.getFont());
      myLabel.setText(value.getValue());
      myLabel.setBackground(isSelected ? table.getSelectionBackground() : table.getBackground());
      component = myLabel;
    }

    return component;
  }

  @Override
  public Component getEditorComponent(JTable table, EditedStyleItem value, boolean isSelected, int row, int column) {
    myEditValue = value;
    myStringValue = value.getValue();

    myTextField.setText(myStringValue);
    myTextField.setFont(table.getFont());

    if (myCompletionProvider != null) {
      myTextField.setVariants(myCompletionProvider.getCompletions(value));
    }

    myBox.setBackground(isSelected ? table.getSelectionBackground() : table.getBackground());

    return myBox;
  }

  @Override
  public String getEditorValue() {
    return myTextField.getText();
  }

  public interface CompletionProvider {
    /**
     * Returns the available completions for the given attribute.
     */
    @NotNull
    List<String> getCompletions(@NotNull EditedStyleItem value);
  }
}
