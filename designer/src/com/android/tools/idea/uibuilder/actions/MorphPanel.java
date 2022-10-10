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

import com.google.common.annotations.VisibleForTesting;
import com.android.tools.idea.uibuilder.api.ViewHandler;
import com.android.tools.idea.uibuilder.handlers.ViewHandlerManager;
import com.android.tools.idea.uibuilder.palette.Palette;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.openapi.project.Project;
import com.intellij.ui.SimpleListCellRenderer;
import com.intellij.ui.TextFieldWithAutoCompletion;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.Consumer;
import com.intellij.util.textCompletion.TextFieldWithCompletion;
import org.jetbrains.android.dom.layout.AndroidLayoutUtil;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.KeyEvent;
import java.util.Collections;
import java.util.List;

/**
 * Form for the {@link MorphComponentAction} dialog
 */
@SuppressWarnings("unused") // For form fields
public class MorphPanel extends JPanel {

  public static final String MORPH_DIALOG_NAME = "MorphPanel";
  private final AndroidFacet myFacet;
  private final Project myProject;
  private JBLabel myNewTagLabel;
  private TextFieldWithCompletion myNewTagText;
  @SuppressWarnings("FieldCanBeLocal") private JPanel myRoot; // Needed for the .form file
  private JButton myOkButton;
  private JList<Palette.Item> mySuggestionsList;
  private Consumer<String> myOkAction;
  @Nullable private Consumer<String> myNameChangeConsumer;

  /**
   * @param facet          {@link AndroidFacet} for the highlighting
   * @param project        Current project used for the autocompletion
   * @param oldTag         old tag name of the morphed component to pre-fill the text field
   * @param tagSuggestions Suggestion of tags to morph the view to
   */
  public MorphPanel(@NotNull AndroidFacet facet,
                    @NotNull Project project,
                    @NotNull String oldTag,
                    @NotNull List<String> tagSuggestions) {
    myFacet = facet;
    myProject = project;
    setName(MORPH_DIALOG_NAME);
    setFocusable(true);
    myOkButton.addActionListener(e -> doOkAction());
    setupTextTagField(oldTag);
    setupButtonList(tagSuggestions, oldTag);
  }

  private void setupButtonList(List<String> suggestions, @NotNull String oldTag) {
    DefaultListModel<Palette.Item> model = new DefaultListModel<>();
    ViewHandlerManager manager = ViewHandlerManager.get(myProject);
    for (String tagSuggestion: suggestions) {
      ViewHandler handler = manager.getHandlerOrDefault(tagSuggestion);
      model.addElement(new Palette.Item(tagSuggestion, handler));
    }

    mySuggestionsList.setModel(model);
    mySuggestionsList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    mySuggestionsList.setLayoutOrientation(JList.HORIZONTAL_WRAP);
    mySuggestionsList.setCellRenderer(SimpleListCellRenderer.create((label, value, index) -> {
      String name = value.getTagName();
      int i = name.lastIndexOf('.');
      if (i > -1 && i < name.length() - 1) {
        name = name.substring(i + 1);
      }
      label.setText(name);
      label.setIcon(value.getIcon());
    }));
    mySuggestionsList.setBackground(getBackground().brighter());
    mySuggestionsList.setVisibleRowCount(5);
    int oldTagPos = suggestions.indexOf(oldTag);
    if (oldTagPos > -1) {
      mySuggestionsList.setSelectedIndex(oldTagPos);
    } else {
      myNewTagText.setText(suggestions.get(0));
      mySuggestionsList.setSelectedIndex(0);
    }
    mySuggestionsList.addListSelectionListener(e -> {
      String tag = mySuggestionsList.getSelectedValue().getTagName();
      myNewTagText.setText(tag);
    });
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

  private void doOkAction() {
    if (myOkAction != null) {
      myOkAction.consume(myNewTagText.getText());
    }
  }

  @NotNull
  private DocumentListener createDocumentListener() {
    return new DocumentListener() {
      @Override
      public void documentChanged(@NotNull DocumentEvent e) {
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
  public JComponent getPreferredFocusComponent() {
    return myNewTagText;
  }

  @VisibleForTesting
  void setTagNameText(String name) {
    myNewTagLabel.setText(name);
  }
}
