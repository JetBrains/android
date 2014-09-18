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

import com.android.sdklib.internal.avd.AvdInfo;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.diagnostic.Logger;

import java.awt.event.ActionEvent;

/**
 * Repair the given AVD. Not yet implemented.
 */
public class RepairAvdAction extends AvdUiAction {
  private static final Logger LOG = Logger.getInstance(RunAvdAction.class);

  public RepairAvdAction(AvdInfoProvider avdInfoProvider) {
    super(avdInfoProvider, "Repair", "Repair this AVD", AllIcons.Actions.IntentionBulb);
  }

  @Override
  public void actionPerformed(ActionEvent e) {
    LOG.warn("Repairing AVD " + getAvdInfo().getName());
    throw new UnsupportedOperationException("Repairing AVDs is not yet implemented");
  }

  @Override
  public boolean isEnabled() {
    AvdInfo avdInfo = getAvdInfo();
    return avdInfo != null && AvdManagerConnection.isAvdRepairable(avdInfo.getStatus());
  }
}
