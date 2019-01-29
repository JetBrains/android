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

import com.android.annotations.VisibleForTesting;
import com.android.tools.idea.common.api.InsertType;
import com.android.tools.idea.common.model.NlComponent;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.ValidationInfo;
import com.intellij.psi.xml.XmlTag;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.net.URI;
import java.net.URISyntaxException;

import static com.android.SdkConstants.*;

public class AddDeeplinkDialog extends DialogWrapper {
  @VisibleForTesting
  JTextField myUriField;
  private JCheckBox myAutoVerify;
  private JPanel myContentPanel;
  @Nullable private final NlComponent myExistingComponent;
  @NotNull private final NlComponent myParent;

  public AddDeeplinkDialog(@Nullable NlComponent existing, @NotNull NlComponent parent) {
    super(false);
    if (existing != null) {
      myUriField.setText(existing.getAttribute(AUTO_URI, ATTR_URI));
      myAutoVerify.setSelected(Boolean.parseBoolean(existing.getAttribute(ANDROID_URI, ATTR_AUTO_VERIFY)));
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
    myExistingComponent = existing;
    myParent = parent;
  }

  @Nullable
  @Override
  protected ValidationInfo doValidate() {
    if (myUriField.getText().isEmpty()) {
      return new ValidationInfo("URI must be set!", myUriField);
    }
    try {
      // replace placeholders with "dummy"
      new URI(myUriField.getText().replaceAll("\\{[^}]*}", "dummy"));
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

  @VisibleForTesting
  String getUri() {
    return myUriField.getText();
  }

  @VisibleForTesting
  public boolean getAutoVerify() {
    return myAutoVerify.isSelected();
  }

  @NotNull
  @Override
  protected Action[] createActions() {
    return new Action[]{getOKAction(), getCancelAction()};
  }

  @Nullable
  @Override
  public JComponent getPreferredFocusedComponent() {
    return myUriField;
  }

  public void save() {
    WriteCommandAction.runWriteCommandAction(myParent.getModel().getProject(), () -> {
      NlComponent realComponent = myExistingComponent;
      if (realComponent == null) {
        XmlTag tag = myParent.getTag().createChildTag(TAG_DEEP_LINK, null, null, false);
        realComponent = myParent.getModel().createComponent(null, tag, myParent, null, InsertType.CREATE);
        realComponent.ensureId();
      }

      realComponent.setAttribute(AUTO_URI, ATTR_URI, getUri());
      if (getAutoVerify()) {
        realComponent.setAndroidAttribute(ATTR_AUTO_VERIFY, "true");
      }
      else {
        // false is the default, so no need to specify the attribute in that case.
        realComponent.removeAttribute(ANDROID_URI, ATTR_AUTO_VERIFY);
      }
    });
  }
}
