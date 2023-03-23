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
package com.android.tools.idea.welcome.wizard.deprecated;

import com.android.tools.idea.wizard.dynamic.ScopedStateStore;
import com.intellij.openapi.util.SystemInfo;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * This is to be shown as the first AEHD Wizard step just to inform the user that
 * AEHD installation is about to start. It is here just to make sure we don't
 * run installation operations straight away as the first wizard step, as this
 * would not be in line with common wizard conventions
 */
@Deprecated
public class AehdInstallInfoStep extends FirstRunWizardStep {
  private JPanel myRoot;
  private final ScopedStateStore.Key<Boolean> myKeyCustomInstall;

  public AehdInstallInfoStep(@NotNull ScopedStateStore.Key<Boolean> keyCustomInstall) {
    super("Installing Android Emulator hypervisor driver");
    myKeyCustomInstall = keyCustomInstall;
    setComponent(myRoot);
  }

  @Override
  public boolean isStepVisible() {
    return SystemInfo.isWindows && Boolean.TRUE.equals(myState.get(myKeyCustomInstall));
  }

  @Override
  public void init() {}

  @Nullable
  @Override
  public JLabel getMessageLabel() {
    return null;
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    return myRoot;
  }

}
