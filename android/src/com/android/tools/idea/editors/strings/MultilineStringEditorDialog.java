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
package com.android.tools.idea.editors.strings;

import com.android.ide.common.resources.Locale;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.ui.EditorTextField;
import com.intellij.ui.components.JBLabel;
import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.core.GridLayoutManager;
import java.awt.Dimension;
import java.awt.Insets;
import javax.swing.JComponent;
import javax.swing.JPanel;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class MultilineStringEditorDialog extends DialogWrapper {
  private JPanel myPanel;
  private JBLabel myTranslationLabel;
  private EditorTextField myDefaultTextField;
  private EditorTextField myTranslationTextField;

  private final Locale myLocale;

  private String myDefaultValue;
  private String myTranslation;

  public MultilineStringEditorDialog(@NotNull AndroidFacet facet,
                                     @NotNull String key,
                                     @Nullable String value,
                                     @Nullable Locale locale,
                                     @Nullable String translation) {
    super(facet.getModule().getProject(), false);
    setupUI();
    myLocale = locale;

    myDefaultTextField.setOneLineMode(false);
    myTranslationTextField.setOneLineMode(false);

    setTitle("Key: " + key);
    if (value != null) {
      myDefaultTextField.setText(value);
    }

    if (locale != null) {
      myTranslationLabel.setText("Translation for " + Locale.getLocaleLabel(locale, false));
      myTranslationTextField.setEnabled(true);

      if (translation != null) {
        myTranslationTextField.setText(translation);
      }
    }
    else {
      myTranslationTextField.setEnabled(false);
    }

    init();
  }

  @Nullable
  @Override
  protected JComponent createCenterPanel() {
    return myPanel;
  }

  @Nullable
  @Override
  protected String getDimensionServiceKey() {
    return "strings.multiline.dialog";
  }

  @NotNull
  public String getDefaultValue() {
    return myDefaultValue;
  }

  @NotNull
  public String getTranslation() {
    return myTranslation;
  }

  @Nullable
  public Locale getLocale() {
    return myLocale;
  }

  @Override
  protected void doOKAction() {
    myDefaultValue = myDefaultTextField.getText();
    myTranslation = myTranslationTextField.getText();

    super.doOKAction();
  }

  private void setupUI() {
    myPanel = new JPanel();
    myPanel.setLayout(new GridLayoutManager(4, 1, new Insets(0, 0, 0, 0), -1, -1));
    final JBLabel jBLabel1 = new JBLabel();
    jBLabel1.setText("Default Value:");
    myPanel.add(jBLabel1,
                new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED,
                                    GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    myDefaultTextField = new EditorTextField();
    myPanel.add(myDefaultTextField, new GridConstraints(1, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH,
                                                        GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                        GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                        new Dimension(400, 150), null, null, 0, false));
    myTranslationLabel = new JBLabel();
    myTranslationLabel.setText("Translation:");
    myPanel.add(myTranslationLabel,
                new GridConstraints(2, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED,
                                    GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    myTranslationTextField = new EditorTextField();
    myTranslationTextField.setName("translationEditorTextField");
    myPanel.add(myTranslationTextField, new GridConstraints(3, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH,
                                                            GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                            GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                            new Dimension(400, 150), null, null, 0, false));
  }
}
