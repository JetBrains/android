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
package com.android.tools.idea.avdmanager;

import com.android.tools.idea.wizard.model.ModelWizardDialog;
import icons.StudioIcons;

import javax.swing.*;
import java.awt.event.ActionEvent;

public class EditAvdAction extends AvdUiAction {
  public EditAvdAction(AvdInfoProvider avdInfoProvider) {
    this(avdInfoProvider, "Edit", "Edit this AVD", StudioIcons.Avd.EDIT);
  }

  protected EditAvdAction(AvdInfoProvider avdInfoProvider, String text, String description, Icon icon) {
    super(avdInfoProvider, text, description, icon);
  }

  @Override
  public void actionPerformed(ActionEvent e) {
    ModelWizardDialog dialog = AvdWizardUtils.createAvdWizard(myAvdInfoProvider.getComponent(), getProject(), getAvdInfo());
    if (dialog.showAndGet()) {
      refreshAvds();
    }
  }

  @Override
  public boolean isEnabled() {
    return getAvdInfo() != null;
  }
}
