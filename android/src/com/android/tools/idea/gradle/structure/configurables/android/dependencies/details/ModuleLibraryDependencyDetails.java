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
import com.android.tools.idea.gradle.structure.model.android.PsAndroidLibraryDependency;
import com.intellij.ui.components.JBLabel;
import org.jdesktop.swingx.JXLabel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class ModuleLibraryDependencyDetails implements DependencyDetails<PsAndroidLibraryDependency> {
  private JPanel myMainPanel;

  private JXLabel myGroupIdLabel;
  private JXLabel myArtifactNameLabel;
  private JBLabel myResolvedVersionLabel;
  private JXLabel myScopeLabel;
  private JBLabel myRequestedVersionLabel;

  @Nullable private PsAndroidLibraryDependency myDependency;

  public ModuleLibraryDependencyDetails() {
  }

  @Override
  @NotNull
  public JPanel getPanel() {
    return myMainPanel;
  }

  @Override
  public void display(@NotNull PsAndroidLibraryDependency dependency) {
    myDependency = dependency;

    PsArtifactDependencySpec spec = myDependency.getDeclaredSpec();
    if (spec == null) {
      spec = myDependency.getResolvedSpec();
    }

    myGroupIdLabel.setText(spec.group);
    myArtifactNameLabel.setText(spec.name);
    myResolvedVersionLabel.setText(myDependency.getResolvedSpec().version);

    myRequestedVersionLabel.setText(spec.version);
    myScopeLabel.setText(myDependency.getJoinedConfigurationNames());
  }

  @Override
  @NotNull
  public Class<PsAndroidLibraryDependency> getSupportedModelType() {
    return PsAndroidLibraryDependency.class;
  }

  @Override
  @Nullable
  public PsAndroidLibraryDependency getModel() {
    return myDependency;
  }
}
