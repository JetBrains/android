/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.naveditor.dialogs;

import static com.android.SdkConstants.TAG_DEEP_LINK;

import com.android.tools.idea.common.api.InsertType;
import com.android.tools.idea.common.command.NlWriteCommandActionUtil;
import com.android.tools.idea.common.model.NlComponent;
import com.android.tools.idea.flags.StudioFlags;
import com.android.tools.idea.naveditor.model.NavComponentHelperKt;
import com.android.tools.idea.uibuilder.model.NlComponentHelperKt;
import com.google.common.annotations.VisibleForTesting;
import com.google.wireless.android.sdk.stats.NavEditorEvent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.ValidationInfo;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBTextField;
import java.net.URI;
import java.net.URISyntaxException;
import javax.swing.Action;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JPanel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class AddDeeplinkDialog extends DialogWrapper {
  @VisibleForTesting
  PlaceholderTextField myUriField;
  @VisibleForTesting
  JCheckBox myAutoVerify;
  @VisibleForTesting
  JBTextField myMimeTypeField;
  @VisibleForTesting
  JBTextField myActionField;

  JBLabel myMimeTypeLabel;
  JBLabel myActionLabel;

  private JPanel myContentPanel;
  @Nullable private final NlComponent myExistingComponent;
  @NotNull private final NlComponent myParent;

  public AddDeeplinkDialog(@Nullable NlComponent existing, @NotNull NlComponent parent) {
    super(false);
    myUriField.setPlaceHolderText("Enter URI - https://www.example.com/person/{id}");
    if (existing != null) {
      myUriField.setText(NavComponentHelperKt.getUri(existing));
      myAutoVerify.setSelected(NavComponentHelperKt.getAutoVerify(existing));
      myMimeTypeField.setText(NavComponentHelperKt.getDeepLinkMimeType(existing));
      myActionField.setText(NavComponentHelperKt.getDeepLinkAction(existing));
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

    if(!StudioFlags.NAV_DEEP_LINK_EXTENDED.get()) {
      myMimeTypeField.setVisible(false);
      myActionField.setVisible(false);
      myMimeTypeLabel.setVisible(false);
      myActionLabel.setVisible(false);
    }
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

  @VisibleForTesting
  public String getMimeType() {
    return myMimeTypeField.getText();
  }

  @VisibleForTesting
  public String getAction() {
    return myActionField.getText();
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
    String name = (myExistingComponent == null) ? "Add Deep Link" : "Update Deep Link";
    NlWriteCommandActionUtil.run(myParent, name, () -> {
      NlComponent realComponent = myExistingComponent;
      if (realComponent == null) {
        realComponent = NlComponentHelperKt.createChild(myParent, TAG_DEEP_LINK, false, null, null, null, null, InsertType.CREATE);
        if (realComponent == null) {
          ApplicationManager.getApplication().invokeLater(() ->
            Messages.showErrorDialog(myParent.getModel().getProject(), "Failed to create Deep Link!", "Error")
          );
          return;
        }
        realComponent.ensureId();
      }

      NavComponentHelperKt.setUriAndLog(realComponent, getUri(), NavEditorEvent.Source.PROPERTY_INSPECTOR);
      NavComponentHelperKt.setAutoVerifyAndLog(realComponent, getAutoVerify(), NavEditorEvent.Source.PROPERTY_INSPECTOR);
      NavComponentHelperKt.setDeeplinkMimeTypeAndLog(realComponent, getMimeType(), NavEditorEvent.Source.PROPERTY_INSPECTOR);
      NavComponentHelperKt.setDeeplinkActionAndLog(realComponent, getAction(), NavEditorEvent.Source.PROPERTY_INSPECTOR);
    });
  }
}
