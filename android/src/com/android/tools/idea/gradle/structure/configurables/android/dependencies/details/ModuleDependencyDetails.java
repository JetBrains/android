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
package com.android.tools.idea.gradle.structure.configurables.android.dependencies.details;

import com.android.tools.idea.gradle.structure.configurables.PsContext;
import com.android.tools.idea.gradle.structure.model.PsDependency;
import com.android.tools.idea.gradle.structure.model.android.PsModuleDependency;
import com.intellij.ui.HyperlinkAdapter;
import com.intellij.ui.HyperlinkLabel;
import com.intellij.ui.components.JBLabel;
import org.jdesktop.swingx.JXLabel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.HyperlinkEvent;

public class ModuleDependencyDetails implements DependencyDetails {
  @NotNull private final PsContext myContext;
  private final boolean myShowScope;

  private JPanel myMainPanel;
  private JXLabel myNameLabel;
  private JXLabel myGradlePathLabel;
  private JBLabel myScopePromptLabel;
  private JXLabel myScopeLabel;
  private HyperlinkLabel myGoToLabel;

  private PsModuleDependency myDependency;

  public ModuleDependencyDetails(@NotNull PsContext context, boolean showScope) {
    myContext = context;
    myShowScope = showScope;
    myScopePromptLabel.setVisible(showScope);
    myScopeLabel.setVisible(showScope);

    myGoToLabel.setHyperlinkText("See Dependencies");
    myGoToLabel.addHyperlinkListener(new HyperlinkAdapter() {
      @Override
      protected void hyperlinkActivated(HyperlinkEvent e) {
        assert myDependency != null;
        myContext.setSelectedModule(myDependency.getName(), this);
      }
    });
  }

  @Override
  @NotNull
  public JPanel getPanel() {
    return myMainPanel;
  }

  @Override
  public void display(@NotNull PsDependency dependency) {
    myDependency = (PsModuleDependency)dependency;
    myNameLabel.setText(myDependency.getName());
    myGradlePathLabel.setText(myDependency.getGradlePath());
    if (myShowScope) {
      myScopeLabel.setText(myDependency.getJoinedConfigurationNames());
    }
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
}
