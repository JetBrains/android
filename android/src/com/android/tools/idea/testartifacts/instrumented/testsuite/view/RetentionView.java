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
package com.android.tools.idea.testartifacts.instrumented.testsuite.view;

import java.io.File;
import javax.swing.JButton;
import javax.swing.JPanel;
import org.jetbrains.annotations.NotNull;

// TODO(yahan@) rework this view when we have the UI mock

/**
 * Shows the Android Test Retention artifacts
 */
public class RetentionView {
  private JPanel myRootPanel;
  private JButton myStartDebuggingButton;
  @NotNull private String packageName = "";
  private File snapshot = null;
  @NotNull private String snapshotId = "";

  /**
   * Returns the root panel.
   */
  @NotNull
  public JPanel getRootPanel() {
    return myRootPanel;
  }

  public void setPackageName(@NotNull String packageName) {
    this.packageName = packageName;
  }

  public void setSnapshotFile(File snapshot) {
    this.snapshot = snapshot;
  }
}
