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
import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.core.GridLayoutManager;
import com.intellij.util.Consumer;
import com.intellij.util.textCompletion.TextFieldWithCompletion;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Insets;
import java.util.Locale;
import javax.swing.plaf.FontUIResource;
import javax.swing.text.StyleContext;
import org.apache.xerces.util.XMLChar;
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
    setupUI();
    setName(MORPH_DIALOG_NAME);
    setFocusable(true);
    myOkButton.addActionListener(e -> doOkAction());
    setupTextTagField(oldTag);
    setupButtonList(tagSuggestions, oldTag);
  }

  private void setupButtonList(List<String> suggestions, @NotNull String oldTag) {
    DefaultListModel<Palette.Item> model = new DefaultListModel<>();
    ViewHandlerManager manager = ViewHandlerManager.get(myProject);
    for (String tagSuggestion : suggestions) {
      ViewHandler handler = manager.getHandlerOrDefault(tagSuggestion, () -> {
      });
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
    }
    else {
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
    myNewTagText.registerKeyboardAction(e -> {
      // Only accept enter if the ok button is enabled
      if (myOkButton.isEnabled()) {
        doOkAction();
      }
    }, KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), JComponent.WHEN_IN_FOCUSED_WINDOW);
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
        String tagName = e.getDocument().getText();
        if (XMLChar.isValidName(tagName)) {
          myOkButton.setEnabled(true);
          myOkButton.setToolTipText(null);

          if (myNameChangeConsumer != null) {
            myNameChangeConsumer.consume(tagName);
          }
        }
        else {
          myOkButton.setEnabled(false);
          myOkButton.setToolTipText("The tag name must be valid");
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

  private void setupUI() {
    createUIComponents();
    myRoot.setLayout(new GridLayoutManager(3, 3, new Insets(10, 10, 10, 10), -1, -1));
    myNewTagLabel = new JBLabel();
    Font myNewTagLabelFont = getFont(null, Font.BOLD, -1, myNewTagLabel.getFont());
    if (myNewTagLabelFont != null) myNewTagLabel.setFont(myNewTagLabelFont);
    myNewTagLabel.setText("Convert View to:");
    myRoot.add(myNewTagLabel,
               new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED,
                                   GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    myRoot.add(myNewTagText, new GridConstraints(0, 1, 1, 2, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL,
                                                 GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED,
                                                 new Dimension(200, -1), null, null, 0, false));
    myOkButton = new JButton();
    myOkButton.setText("Apply");
    myRoot.add(myOkButton, new GridConstraints(2, 2, 1, 1, GridConstraints.ANCHOR_EAST, GridConstraints.FILL_NONE,
                                               GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                               GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    mySuggestionsList = new JList();
    mySuggestionsList.setName("suggestionList");
    myRoot.add(mySuggestionsList, new GridConstraints(1, 0, 1, 3, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL,
                                                      GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                      GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
  }

  private Font getFont(String fontName, int style, int size, Font currentFont) {
    if (currentFont == null) return null;
    String resultName;
    if (fontName == null) {
      resultName = currentFont.getName();
    }
    else {
      Font testFont = new Font(fontName, Font.PLAIN, 10);
      if (testFont.canDisplay('a') && testFont.canDisplay('1')) {
        resultName = fontName;
      }
      else {
        resultName = currentFont.getName();
      }
    }
    Font font = new Font(resultName, style >= 0 ? style : currentFont.getStyle(), size >= 0 ? size : currentFont.getSize());
    boolean isMac = System.getProperty("os.name", "").toLowerCase(Locale.ENGLISH).startsWith("mac");
    Font fontWithFallback = isMac
                            ? new Font(font.getFamily(), font.getStyle(), font.getSize())
                            : new StyleContext().getFont(font.getFamily(), font.getStyle(), font.getSize());
    return fontWithFallback instanceof FontUIResource ? fontWithFallback : new FontUIResource(fontWithFallback);
  }
}
