/*
 * Copyright 2000-2012 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.android.compiler;

import com.intellij.compiler.server.BuildManager;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.project.Project;
import com.intellij.ui.RawCommandLineEditor;
import com.intellij.ui.components.JBCheckBox;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
* @author Dmitry Avdeev
*         Date: 9/21/12
*/
public class AndroidDexCompilerSettingsConfigurable implements SearchableConfigurable, Configurable.NoScroll {
  private final AndroidDexCompilerConfiguration myConfig;
  private final Project myProject;
  private JPanel myContentPanel;
  private JSpinner myHeapSizeSpinner;
  private JLabel myVmOptionsLabel;
  private RawCommandLineEditor myVmOptionsEditor;
  private JBCheckBox myOptimizeCheckBox;
  private JBCheckBox myJumboModeCheckBox;
  private JBCheckBox myCoreLibraryCheckBox;
  private RawCommandLineEditor myProguardVmOptionsEditor;

  public AndroidDexCompilerSettingsConfigurable(Project project) {
    myConfig = AndroidDexCompilerConfiguration.getInstance(project);
    myProject = project;
    myVmOptionsLabel.setLabelFor(myVmOptionsEditor);
    myVmOptionsEditor.setDialogCaption(AndroidBundle.message("android.dex.compiler.vm.options.title"));
    myProguardVmOptionsEditor.setDialogCaption(AndroidBundle.message("android.proguard.vm.options.title"));
  }

  @Nls
  @Override
  public String getDisplayName() {
    return AndroidBundle.message("android.dex.compiler.configurable.display.name");
  }

  @Override
  public String getHelpTopic() {
    return "settings.android.dx.compiler";
  }

  @Override
  public JComponent createComponent() {
    return myContentPanel;
  }

  @Override
  public boolean isModified() {
    int maxHeapSize = ((Integer)myHeapSizeSpinner.getValue()).intValue();
    return maxHeapSize != myConfig.MAX_HEAP_SIZE ||
           !myVmOptionsEditor.getText().equals(myConfig.VM_OPTIONS) ||
           myOptimizeCheckBox.isSelected() != myConfig.OPTIMIZE ||
           myJumboModeCheckBox.isSelected() != myConfig.FORCE_JUMBO ||
           myCoreLibraryCheckBox.isSelected() != myConfig.CORE_LIBRARY ||
           !myProguardVmOptionsEditor.getText().equals(myConfig.PROGUARD_VM_OPTIONS);
  }

  @Override
  public void apply() throws ConfigurationException {
    try {
      myConfig.MAX_HEAP_SIZE = ((Integer)myHeapSizeSpinner.getValue()).intValue();
      myConfig.VM_OPTIONS = myVmOptionsEditor.getText();
      myConfig.OPTIMIZE = myOptimizeCheckBox.isSelected();
      myConfig.FORCE_JUMBO = myJumboModeCheckBox.isSelected();
      myConfig.CORE_LIBRARY = myCoreLibraryCheckBox.isSelected();
      myConfig.PROGUARD_VM_OPTIONS = myProguardVmOptionsEditor.getText();
    }
    finally {
      BuildManager.getInstance().clearState(myProject);
    }
  }

  @Override
  public void reset() {
    myHeapSizeSpinner.setModel(new SpinnerNumberModel(myConfig.MAX_HEAP_SIZE, 1, 10000000, 1));
    myVmOptionsEditor.setText(myConfig.VM_OPTIONS);
    myOptimizeCheckBox.setSelected(myConfig.OPTIMIZE);
    myJumboModeCheckBox.setSelected(myConfig.FORCE_JUMBO);
    myCoreLibraryCheckBox.setSelected(myConfig.CORE_LIBRARY);
    myProguardVmOptionsEditor.setText(myConfig.PROGUARD_VM_OPTIONS);
  }

  @NotNull
  @Override
  public String getId() {
    return "android.dex.compiler";
  }
}
