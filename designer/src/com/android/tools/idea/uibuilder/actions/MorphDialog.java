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
package com.android.tools.idea.uibuilder.actions;

import com.android.annotations.VisibleForTesting;
import com.intellij.openapi.editor.event.DocumentAdapter;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.project.Project;
import com.intellij.ui.TextFieldWithAutoCompletion;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.Consumer;
import com.intellij.util.textCompletion.TextFieldWithCompletion;
import org.jetbrains.android.dom.layout.AndroidLayoutUtil;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.KeyEvent;
import java.util.Collections;
import java.util.List;

/**
 * Form for the {@link MorphComponentAction} dialog
 */
@SuppressWarnings("unused") // For form fields
public class MorphDialog extends JPanel {

  public static final String MORPH_DIALOG_NAME = "MorphDialog";
  private final AndroidFacet myFacet;
  private final Project myProject;
  private final JComponent myCodePreview;
  private JBLabel myNewTagLabel;
  private TextFieldWithCompletion myNewTagText;
  private JPanel myRoot;
  private JButton myOkButton;
  private Consumer<String> myOkAction;
  @Nullable private Consumer<String> myNameChangeConsumer;

  /**
   * @param facet             {@link AndroidFacet} for the highlighting
   * @param project           Current project used for the autocompletion
   * @param codePreviewEditor Editor showing the code preview
   * @param oldTag            old tag name of the morphed component to pre-fill the text field
   */
  public MorphDialog(@NotNull AndroidFacet facet, @NotNull Project project, @NotNull JComponent codePreviewEditor, @NotNull String oldTag) {
    myFacet = facet;
    myProject = project;
    myCodePreview = codePreviewEditor;
    setName(MORPH_DIALOG_NAME);
    setFocusable(true);
    myOkButton.addActionListener(e -> doOkAction());
    setupTextTagField(oldTag);
  }

  private void setupTextTagField(@NotNull String oldTag) {
    myNewTagText.addDocumentListener(createDocumentListener());
    myNewTagText.setText(oldTag);
    myNewTagText.registerKeyboardAction(e -> doOkAction(), KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), JComponent.WHEN_IN_FOCUSED_WINDOW);
    myNewTagText.setRequestFocusEnabled(true);
    myNewTagText.requestFocusInWindow();
    myNewTagText.addFocusListener(new FocusAdapter() {
      @Override
      public void focusGained(FocusEvent e) {
        myNewTagText.selectAll();
      }
    });
  }

  @VisibleForTesting
  void doOkAction() {
    if (myOkAction != null) {
      myOkAction.consume(myNewTagText.getText());
    }
  }

  @NotNull
  private DocumentAdapter createDocumentListener() {
    return new DocumentAdapter() {
      @Override
      public void documentChanged(DocumentEvent e) {
        if (myNameChangeConsumer != null) {
          myNameChangeConsumer.consume(e.getDocument().getText());
        }
      }
    };
  }

  public void createUIComponents() {
    myRoot = this;
    myNewTagText = createAutoCompleteTagField();
  }

  @NotNull
  private TextFieldWithCompletion createAutoCompleteTagField() {
    List<String> possibleRoots = AndroidLayoutUtil.getPossibleRoots(myFacet);
    Collections.sort(possibleRoots);
    TextFieldWithAutoCompletion.StringsCompletionProvider completionProvider =
      new TextFieldWithAutoCompletion.StringsCompletionProvider(possibleRoots, null);
    return new TextFieldWithAutoCompletion<>(myProject, completionProvider, true, null);
  }

  /**
   * Action to execute when the "Apply" button is clicked of the enter key clicked
   *
   * @param okAction
   */
  public void setOkAction(@Nullable Consumer<String> okAction) {
    myOkAction = okAction;
  }

  /**
   * Set the consumer called when the TextField is modified
   */
  public void setTagNameChangeConsumer(@Nullable Consumer<String> consumer) {
    myNameChangeConsumer = consumer;
  }

  /**
   * @return The component that should e focused when this panel is shown
   */
  @NotNull
  public Component getPreferredFocusComponent() {
    return myNewTagText;
  }

  @VisibleForTesting
  void setTagNameText(String name) {
    myNewTagLabel.setText(name);
  }
}
