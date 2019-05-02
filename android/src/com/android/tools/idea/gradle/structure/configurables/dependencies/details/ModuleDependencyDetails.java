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
package com.android.tools.idea.gradle.structure.configurables.dependencies.details;

import com.android.tools.idea.gradle.structure.configurables.PsContext;
import com.android.tools.idea.gradle.structure.model.PsBaseDependency;
import com.android.tools.idea.gradle.structure.model.PsDeclaredModuleDependency;
import com.android.tools.idea.gradle.structure.model.PsModule;
import com.android.tools.idea.gradle.structure.model.PsModuleDependency;
import com.intellij.ui.HyperlinkAdapter;
import com.intellij.ui.HyperlinkLabel;
import com.intellij.ui.components.JBLabel;
import javax.swing.JComboBox;
import javax.swing.JPanel;
import javax.swing.event.HyperlinkEvent;
import org.jdesktop.swingx.JXLabel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ModuleDependencyDetails implements ConfigurationDependencyDetails {
  @NotNull private final PsContext myContext;
  private final boolean myShowScope;

  private JPanel myMainPanel;
  private JXLabel myNameLabel;
  private JXLabel myGradlePathLabel;
  private JBLabel myConfigurationLabel;
  private HyperlinkLabel myGoToLabel;
  private JComboBox<String> myConfiguration;

  private PsModuleDependency myDependency;

  public ModuleDependencyDetails(@NotNull PsContext context, boolean showScope) {
    myContext = context;
    myShowScope = showScope;
    myConfigurationLabel.setVisible(showScope);
    myConfiguration.setVisible(showScope);

    myGoToLabel.setHyperlinkText("See Dependencies");
    myGoToLabel.addHyperlinkListener(new HyperlinkAdapter() {
      @Override
      protected void hyperlinkActivated(HyperlinkEvent e) {
        assert myDependency != null;
        myContext.getMainConfigurable().navigateTo(
          myContext
            .getProject()
            .findModuleByGradlePath(myDependency.getGradlePath())
            .getPath()
            .getDependenciesPath()
            .getPlaceDestination(myContext),
          true);
      }
    });
  }

  @Override
  @NotNull
  public JPanel getPanel() {
    return myMainPanel;
  }

  @Override
  public void display(@NotNull PsBaseDependency dependency) {
    PsModuleDependency d = (PsModuleDependency) dependency;
    myNameLabel.setText(d.getName());
    myGradlePathLabel.setText(d.getGradlePath());
    if (myShowScope) {
      displayConfiguration((PsDeclaredModuleDependency) d, PsModule.ImportantFor.MODULE);
    }
    myDependency = d;
  }

  @Override
  @NotNull
  public Class<PsModuleDependency> getSupportedModelType() {
    return PsModuleDependency.class;
  }

  @Override
  @Nullable
  public PsModuleDependency getModel() {
    return myDependency;
  }

  @Override
  public JComboBox<String> getConfigurationUI() {
    return myConfiguration;
  }

  private void createUIComponents() {
    myConfiguration = createConfigurationUI();
  }
}
