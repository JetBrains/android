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

import com.android.tools.idea.gradle.structure.model.PsArtifactDependencySpec;
import com.android.tools.idea.gradle.structure.model.PsDependency;
import com.android.tools.idea.gradle.structure.model.PsLibraryDependency;
import com.intellij.ui.components.JBLabel;
import org.jdesktop.swingx.JXLabel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

import static com.intellij.openapi.util.text.StringUtil.isNotEmpty;

public class SingleLibraryDependencyDetails implements DependencyDetails {
  private JPanel myMainPanel;

  private JXLabel myGroupIdLabel;
  private JXLabel myArtifactNameLabel;
  private JBLabel myResolvedVersionLabel;
  private JXLabel myScopeLabel;
  private JBLabel myRequestedVersionLabel;

  @Nullable private PsLibraryDependency myDependency;

  @Override
  @NotNull
  public JPanel getPanel() {
    return myMainPanel;
  }

  @Override
  public void display(@NotNull PsDependency dependency) {
    display(dependency, dependency.getJoinedConfigurationNames());
  }

  @Override
  public void display(@NotNull PsDependency dependency, @Nullable String configurationNames) {
    myDependency = (PsLibraryDependency)dependency;

    PsArtifactDependencySpec spec = myDependency.getDeclaredSpec();
    if (spec == null) {
      spec = myDependency.getResolvedSpec();
    }

    myGroupIdLabel.setText(spec.group);
    myArtifactNameLabel.setText(spec.name);
    myResolvedVersionLabel.setText(myDependency.getResolvedSpec().version);

    myRequestedVersionLabel.setText(spec.version);

    String scopeToDisplay = isNotEmpty(configurationNames) ? configurationNames : dependency.getJoinedConfigurationNames();
    myScopeLabel.setText(scopeToDisplay);
  }

  @Override
  @NotNull
  public Class<PsLibraryDependency> getSupportedModelType() {
    return PsLibraryDependency.class;
  }

  @Override
  @Nullable
  public PsLibraryDependency getModel() {
    return myDependency;
  }
}
