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

import com.android.tools.idea.configurations.LocaleMenuAction;
import com.android.tools.idea.rendering.Locale;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.ui.EditorTextField;
import com.intellij.ui.components.JBLabel;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

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
    myLocale = locale;

    myDefaultTextField.setOneLineMode(false);
    myTranslationTextField.setOneLineMode(false);

    setTitle("Key: " + key);
    if (value != null) {
      myDefaultTextField.setText(value);
    }

    if (locale != null) {
      myTranslationLabel.setText("Translation for " + LocaleMenuAction.getLocaleLabel(locale, false));
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
}
