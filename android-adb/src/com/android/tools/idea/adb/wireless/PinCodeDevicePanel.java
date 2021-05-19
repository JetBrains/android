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

import com.intellij.ui.RelativeFont;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.ui.JBUI;
import java.awt.Component;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import javax.swing.JButton;
import javax.swing.JPanel;
import org.jetbrains.annotations.NotNull;

public class PinCodeDevicePanel {
  @NotNull private final MdnsService myMdnsService;
  @NotNull private JButton myPairButton;
  @NotNull private JBLabel myDeviceIpLabel;
  @NotNull private JPanel myRootContainer;
  @NotNull private JBLabel myAvailableToPairLabel;

  public PinCodeDevicePanel(@NotNull MdnsService mdnsService, @NotNull Runnable pairActionRunnable) {
    myMdnsService = mdnsService;
    myRootContainer.setBorder(JBUI.Borders.empty(5, 10));
    myRootContainer.setBackground(UIColors.PAIRING_CONTENT_BACKGROUND);
    myPairButton.setBackground(UIColors.PAIRING_CONTENT_BACKGROUND);
    myAvailableToPairLabel.setForeground(UIColors.LIGHT_LABEL);
    RelativeFont.SMALL.install(myAvailableToPairLabel);
    myPairButton.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        pairActionRunnable.run();
      }
    });
    myDeviceIpLabel.setText(mdnsService.getDisplayString());
  }

  @NotNull
  public Component getComponent() {
    return myRootContainer;
  }

  @NotNull
  public MdnsService getMdnsService() {
    return myMdnsService;
  }
}
