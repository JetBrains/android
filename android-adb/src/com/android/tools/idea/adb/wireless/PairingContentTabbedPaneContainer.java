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
package com.android.tools.idea.adb.wireless;

import java.awt.BorderLayout;
import javax.swing.JComponent;
import javax.swing.JPanel;
import org.jetbrains.annotations.NotNull;

/**
 * Top level component in each pairing tab. Contains a top and bottom row of fixed size,
 * and a center panel for a custom component.
 */
public class PairingContentTabbedPaneContainer {
  @NotNull private JPanel myRootComponent;
  @NotNull private JPanel myTopRow;
  @NotNull private JPanel myCenterRow;
  @NotNull private JPanel myBottomRow;

  public PairingContentTabbedPaneContainer() {
    EditorPaneUtils.setTitlePanelBorder(myTopRow);
    EditorPaneUtils.setBottomPanelBorder(myBottomRow);
  }

  public void setContent(@NotNull JComponent component) {
    myCenterRow.removeAll();
    myCenterRow.add(component, BorderLayout.CENTER);
  }
}
