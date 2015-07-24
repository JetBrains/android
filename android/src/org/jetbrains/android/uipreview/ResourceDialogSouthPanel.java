/*
 * Copyright (C) 2015 The Android Open Source Project
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
package org.jetbrains.android.uipreview;

import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.ui.HideableDecorator;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBLabel;

import java.awt.Color;
import java.awt.Component;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;

public class ResourceDialogSouthPanel {
  private JTextField myResourceNameField;
  private JBLabel myResourceNameMessage;
  private JPanel myFullPanel;
  private JPanel myExpertPlaceholder;
  private JPanel myExpertPanel;
  private HideableDecorator myExpertDecorator;

  public ResourceDialogSouthPanel() {
    Color backgroundColor = EditorColorsManager.getInstance().getGlobalScheme().getColor(EditorColors.NOTIFICATION_BACKGROUND);
    myResourceNameMessage.setBackground(backgroundColor == null ? JBColor.YELLOW : backgroundColor);
    myExpertDecorator = new HideableDecorator(myExpertPlaceholder, "Location", true) {
      private void pack() {
        // Hack to not shrink the window too small when we close or open the advanced panel.
        SwingUtilities.invokeLater(new Runnable() {
          @Override
          public void run() {
            SwingUtilities.getWindowAncestor(myExpertPlaceholder).pack();
          }
        });
      }

      @Override
      protected void on() {
        super.on();
        pack();
      }

      @Override
      protected void off() {
        super.off();
        pack();
      }
    };
    myExpertDecorator.setContentComponent(myExpertPanel);
  }

  void setExpertPanel(Component comp) {
    myExpertPanel.removeAll();
    myExpertPanel.add(comp);
  }

  public JPanel getFullPanel() {
    return myFullPanel;
  }

  public JBLabel getResourceNameMessage() {
    return myResourceNameMessage;
  }

  public JTextField getResourceNameField() {
    return myResourceNameField;
  }

  public void setOn(boolean on) {
    myExpertDecorator.setOn(on);
  }
}
