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

import com.android.SdkConstants;
import com.android.annotations.Nullable;
import com.intellij.ui.TextFieldWithAutoCompletion;
import org.jetbrains.android.dom.layout.AndroidLayoutUtil;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.util.Collections;
import java.util.List;

/**
 * A Form with only an auto-complete {@link TextFieldWithAutoCompletion} to find View tag name
 * A facet needs to be provided for the field to appear.
 */
public class AutoCompleteForm {
  private JPanel myTagNameWrapper;
  private TextFieldWithAutoCompletion<String> myTagNameField;
  private JComponent myComponent;

  public AutoCompleteForm(@Nullable AndroidFacet facet) {
    if (facet != null) {
      setFacet(facet);
    }
  }

  @NotNull
  public String getTagName() {
    return myTagNameField == null ? SdkConstants.VIEW : myTagNameField.getText();
  }

  public void setFacet(@NotNull AndroidFacet facet) {
    List<String> possibleRoots = AndroidLayoutUtil.getPossibleRoots(facet);
    Collections.sort(possibleRoots);
    myTagNameField = new TextFieldWithAutoCompletion<>(
      facet.getModule().getProject(),
      new TextFieldWithAutoCompletion.StringsCompletionProvider(
        possibleRoots, null), true, null);
    myTagNameWrapper.add(myTagNameField, BorderLayout.CENTER);
    myTagNameField.setHorizontalSizeReferent(myTagNameWrapper);
    myComponent.validate();
  }

  @NotNull
  public JComponent getComponent() {
    return myComponent;
  }
}
