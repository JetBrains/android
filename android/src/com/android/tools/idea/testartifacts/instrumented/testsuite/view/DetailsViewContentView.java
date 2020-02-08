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

import javax.swing.JPanel;
import org.jetbrains.annotations.NotNull;

/**
 * Shows detailed tests results for a selected device.
 */
public class DetailsViewContentView {

  // Those properties are initialized by IntelliJ form editor before the constructor using reflection.
  private JPanel myRootPanel;
  private JPanel mySummaryPanel;
  private JPanel myContentSelectorTabPanel;
  private JPanel myContentPanel;

  /**
   * Returns the root panel.
   */
  @NotNull
  public JPanel getRootPanel() {
    return myRootPanel;
  }
}
