/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.uibuilder.mockup.editor.creators.forms;

import com.android.ide.common.resources.MergingException;
import com.android.ide.common.resources.ValueResourceNameValidator;
import com.android.resources.ResourceType;
import com.android.tools.idea.uibuilder.mockup.backgroundremove.RemoveBackgroundPanel;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBLabel;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import java.awt.*;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

/**
 * Form to display a a Panel allowing to remove the background from an image and create a drawable.
 * and with Ok, Dismiss, undo and redo action as well as a text field to name the drawable.
 */
public class ExtractBackgroundForm {
  private static final Logger LOGGER = Logger.getInstance(ExtractBackgroundForm.class);
  private JButton myOKButton;
  private JButton myDismissButton;
  private RemoveBackgroundPanel myRemoveBackgroundPanel;
  private JPanel myComponent;
  private JTextField myDrawableName;
  private JBLabel myErrorLabel;
  private JPanel myUndoRedoPanel;
  private ActionListener myListener;

  /**
   * Construct a new Form
   *
   * @param removeBackgroundPanel The panel handling the removal actions
   */
  public ExtractBackgroundForm(@NotNull RemoveBackgroundPanel removeBackgroundPanel) {
    myRemoveBackgroundPanel = removeBackgroundPanel;
    myComponent.setBorder(BorderFactory.createLineBorder(JBColor.border()));
    myComponent.setFocusable(true);
    myComponent.repaint();
    myDismissButton.addActionListener(e -> myComponent.getParent().remove(myComponent));
    myComponent.addMouseListener(new MouseAdapter() {
      @Override
      public void mouseEntered(MouseEvent e) {
        if (!myDrawableName.hasFocus()) {
          myComponent.requestFocusInWindow();
        }
      }
    });

    myDrawableName.getDocument().addDocumentListener(createDrawableNameListener());
    final JComponent undoRedoButtons = createUndoRedoButtons();
    undoRedoButtons.setOpaque(false);
    myUndoRedoPanel.add(undoRedoButtons, BorderLayout.SOUTH);
    validateDrawableName(myDrawableName.getDocument());
  }


  /**
   * Create a new Document listener to validate the input of the text field
   */
  @NotNull
  private DocumentListener createDrawableNameListener() {
    return new DocumentListener() {
      @Override
      public void insertUpdate(DocumentEvent e) {
        myOKButton.setEnabled(validateDrawableName(e.getDocument()));
      }

      @Override
      public void removeUpdate(DocumentEvent e) {
        myOKButton.setEnabled(validateDrawableName(e.getDocument()));
      }

      @Override
      public void changedUpdate(DocumentEvent e) {
      }
    };
  }

  /**
   * Create an {@link ActionToolbar} with the undo and redo actions
   */
  private JComponent createUndoRedoButtons() {
    return ActionManager.getInstance().createActionToolbar("AndroidExtractBackgroundUndoRedo", new DefaultActionGroup(
      new AnAction("Undo", "Undo", AllIcons.Actions.Undo) {

        @Override
        public void update(AnActionEvent e) {
          e.getPresentation().setEnabled(myRemoveBackgroundPanel.canUndo());
        }

        @Override
        public void actionPerformed(AnActionEvent e) {
          myRemoveBackgroundPanel.undo();
        }
      },
      new AnAction("Redo", "Redo", AllIcons.Actions.Redo) {

        @Override
        public void update(AnActionEvent e) {
          e.getPresentation().setEnabled(myRemoveBackgroundPanel.canRedo());
        }

        @Override
        public void actionPerformed(AnActionEvent e) {
          myRemoveBackgroundPanel.redo();
        }
      }
    ), true).getComponent();
  }

  /**
   * Validate the text from the given document as a correct Drawable Resource Name
   * and displays an error if any
   *
   * @param document the document containing the color name
   * @return true if the name is correct, false otherwise
   */
  private boolean validateDrawableName(Document document) {
    if (document.getLength() <= 0) {
      myErrorLabel.setForeground(JBColor.foreground());
      myErrorLabel.setText("To create the drawable, enter a name");
      return false;
    }
    myErrorLabel.setForeground(JBColor.RED);
    try {
      String text = document.getText(0, document.getLength());
      try {
        ValueResourceNameValidator.validate(text, ResourceType.DRAWABLE, null);
        myErrorLabel.setText("");
        return true;
      }
      catch (MergingException ex) {
        myErrorLabel.setText
          (String.format("<html> %s </html>",
                         ValueResourceNameValidator.getErrorText(text, ResourceType.DRAWABLE)));
        return false;
      }
    }
    catch (BadLocationException ex) {
      LOGGER.warn(ex);
      return false;
    }
  }

  /**
   * Set the listener for the OK button.
   * Remove the previous one if any
   *
   * @param listener The listener to add
   */
  public void setOKListener(ActionListener listener) {
    if (myListener != null) {
      myOKButton.removeActionListener(myListener);
    }
    myListener = listener;
    myOKButton.addActionListener(myListener);
  }

  /**
   * Displays an error message inside the panel
   * @param errorMessage
   */
  public void setErrorText(String errorMessage) {
    myErrorLabel.setForeground(JBColor.RED);
    myErrorLabel.setText(String.format("<html>%s</html>", errorMessage));
  }

  /**
   * Return the root component of this form
   */
  public JPanel getComponent() {
    return myComponent;
  }

  /**
   * Get the value of the text field
   *
   * @return the value of the text field.
   */
  @NotNull
  public String getDrawableName() {
    return myDrawableName.getText();
  }

  private void createUIComponents() {
    // Do not remove, needed for the form creation
    myComponent = new ToolRootPanel();
  }
}
