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
package com.android.tools.idea.naveditor.property.inspector;

import com.android.tools.idea.common.model.NlComponent;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.ValidationInfo;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.net.URI;
import java.net.URISyntaxException;

import static com.android.SdkConstants.*;

public class AddDeeplinkDialog extends DialogWrapper {
  private JTextField myUriField;
  private JCheckBox myAutoVerify;
  private JPanel myContentPanel;

  public AddDeeplinkDialog(@Nullable NlComponent existing) {
    super(false);
    if (existing != null) {
      myUriField.setText(existing.getAttribute(AUTO_URI, ATTR_URI));
      myAutoVerify.setSelected(Boolean.parseBoolean(existing.getAttribute(AUTO_URI, ATTR_AUTO_VERIFY)));
    }
    init();
    if (existing == null) {
      myOKAction.putValue(Action.NAME, "Add");
      setTitle("Add Deep Link");
    }
    else {
      myOKAction.putValue(Action.NAME, "Update");
      setTitle("Update Deep Link");
    }
  }

  @Nullable
  @Override
  protected ValidationInfo doValidate() {
    if (myUriField.getText().isEmpty()) {
      return new ValidationInfo("URI must be set!", myUriField);
    }
    try {
      new URI(myUriField.getText());
    }
    catch (URISyntaxException e) {
      return new ValidationInfo("Invalid URI!", myUriField);
    }
    return null;
  }

  @Nullable
  @Override
  protected JComponent createCenterPanel() {
    return myContentPanel;
  }

  public String getUri() {
    return myUriField.getText();
  }

  public boolean getAutoVerify() {
    return myAutoVerify.isSelected();
  }

  @NotNull
  @Override
  protected Action[] createActions() {
    return new Action[]{getOKAction(), getCancelAction()};
  }
}
