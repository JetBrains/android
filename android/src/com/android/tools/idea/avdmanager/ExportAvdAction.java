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

import com.intellij.icons.AllIcons;
import com.intellij.openapi.diagnostic.Logger;

import java.awt.event.ActionEvent;

/**
 * Export an AVD in a format suitable for sharing or distributing. Not yet implemented.
 */
public class ExportAvdAction extends AvdUiAction {
  private static final Logger LOG = Logger.getInstance(RunAvdAction.class);

  public ExportAvdAction(AvdInfoProvider avdInfoProvider) {
    super(avdInfoProvider, "Export", "Export this AVD", AllIcons.Modules.Edit);
  }

  @Override
  public void actionPerformed(ActionEvent e) {
    LOG.warn("Exporting AVD " + getAvdInfo().getName());
    throw new UnsupportedOperationException("Exporting AVDs is not yet implemented");
  }

  @Override
  public boolean isEnabled() {
    return getAvdInfo() != null;
  }
}
