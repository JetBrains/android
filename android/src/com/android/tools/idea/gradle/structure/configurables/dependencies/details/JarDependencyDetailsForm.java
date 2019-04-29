/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.gradle.structure.configurables.dependencies.details;

import com.intellij.openapi.ui.ComboBox;
import com.intellij.ui.components.JBLabel;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import javax.swing.JComboBox;
import javax.swing.JPanel;
import org.jdesktop.swingx.JXLabel;

public abstract class JarDependencyDetailsForm {
  protected JPanel myMainPanel;
  protected JXLabel myNameText;
  protected JXLabel myIncludesText;
  protected JBLabel myExcludesLabel;
  protected JXLabel myExcludesText;
  protected JBLabel myNameLabel;
  protected JBLabel myIncludesLabel;
  protected JBLabel myScopeLabel;
  protected JComboBox myScope;

  protected boolean comboMaintenance = false;

  private void createUIComponents() {
    myScope = new ComboBox<String>();
    myScope.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        if (!comboMaintenance) {
          modifyConfiguration();
        }
      }
    });
  }

  public abstract void modifyConfiguration();
}
