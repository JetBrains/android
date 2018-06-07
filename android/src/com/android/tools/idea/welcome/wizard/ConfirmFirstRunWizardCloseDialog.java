/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.welcome.wizard;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

public class ConfirmFirstRunWizardCloseDialog extends JDialog {
  private boolean myWasCanceled;

  public enum Result {
    DoNotClose, Rerun, Skip
  }

  private JPanel contentPane;
  private JButton buttonOK;
  private JButton buttonCancel;
  private JRadioButton myRun;
  private JRadioButton myDoNotRun;

  public ConfirmFirstRunWizardCloseDialog() {
    setContentPane(contentPane);
    setModal(true);
    getRootPane().setDefaultButton(buttonOK);

    buttonOK.addActionListener(e -> onOK());

    buttonCancel.addActionListener(e -> onCancel());

    setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
    addWindowListener(new WindowAdapter() {
      @Override
      public void windowClosing(WindowEvent e) {
        onCancel();
      }
    });

    contentPane.registerKeyboardAction(e -> onCancel(),
                                       KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);
    myRun.setSelected(true);
  }

  private void onOK() {
    dispose();
  }

  private void onCancel() {
    myWasCanceled = true;
    dispose();
  }

  public Result open() {
    pack();
    Dimension size = getSize();
    Rectangle screen = getGraphicsConfiguration().getBounds();
    setLocation(screen.x + (screen.width - size.width)/2, screen.y + (screen.height - size.height)/2);
    setVisible(true);
    if (myWasCanceled) {
      return Result.DoNotClose;
    }
    else if (myRun.isSelected()) {
      return Result.Rerun;
    }
    else {
      return Result.Skip;
    }
  }
}
