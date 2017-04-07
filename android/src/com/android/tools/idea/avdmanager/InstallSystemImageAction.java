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
package com.android.tools.idea.avdmanager;

import com.android.annotations.Nullable;
import com.android.sdklib.internal.avd.AvdInfo;
import com.android.tools.idea.sdk.wizard.SdkQuickfixUtils;
import com.android.tools.idea.wizard.model.ModelWizardDialog;
import com.google.common.collect.ImmutableList;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;

import java.awt.event.ActionEvent;
import java.util.List;

/**
 * Action for downloading a missing system image.
 */
public class InstallSystemImageAction extends AvdUiAction {

  public InstallSystemImageAction(AvdInfoProvider avdInfoProvider) {
    super(avdInfoProvider, "Download", "The corresponding system image is missing", AllIcons.General.BalloonWarning);
  }

  @Override
  public boolean isEnabled() {
    return getPackagePath() != null;
  }

  @Override
  public void actionPerformed(ActionEvent actionEvent) {
    String path = getPackagePath();
    assert path != null;
    List<String> requested = ImmutableList.of(path);
    int response = Messages.showOkCancelDialog("The corresponding system image is missing.\n\nDownload it now?", "Download System Image",
                                               Messages.getQuestionIcon());
    if (response != Messages.OK) {
      return;
    }
    ModelWizardDialog sdkQuickfixWizard = SdkQuickfixUtils.createDialogForPaths(getProject(), requested);
    if (sdkQuickfixWizard != null) {
      sdkQuickfixWizard.show();
      refreshAvds();
    }
  }

  @Nullable
  private String getPackagePath() {
    AvdInfo avdInfo = getAvdInfo();
    if (avdInfo == null) {
      return null;
    }
    return AvdManagerConnection.getRequiredSystemImagePath(avdInfo);
  }
}
