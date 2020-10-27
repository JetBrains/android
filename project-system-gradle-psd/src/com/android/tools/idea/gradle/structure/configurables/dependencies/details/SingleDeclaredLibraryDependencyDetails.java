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
import com.android.tools.idea.gradle.structure.configurables.ui.properties.ModelPropertyEditor;
import com.android.tools.idea.gradle.structure.model.*;
import com.intellij.openapi.util.Disposer;
import kotlin.Unit;
import org.jdesktop.swingx.JXLabel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class SingleDeclaredLibraryDependencyDetails implements ConfigurationDependencyDetails {
  private JPanel myMainPanel;

  private JXLabel myGroupIdLabel;
  private JXLabel myArtifactNameLabel;
  private JPanel myRequestedVersion;
  private JPanel myConfigurationPanel;

  @NotNull private final PsContext myContext;
  @Nullable private PsDeclaredLibraryDependency myDependency;
  @Nullable private ModelPropertyEditor<?> myVersionPropertyEditor;
  @Nullable private JComponent myEditorComponent;

  public SingleDeclaredLibraryDependencyDetails(@NotNull PsContext context) {
    myContext = context;
  }

  @Override
  @NotNull
  public JPanel getPanel() {
    return myMainPanel;
  }

  @Override
  public void display(@NotNull PsBaseDependency dependency) {
    PsDeclaredLibraryDependency d = (PsDeclaredLibraryDependency) dependency;

    displayVersion(d);
    displayConfiguration(d, PsModule.ImportantFor.LIBRARY);
    if (myDependency != dependency) {
      PsArtifactDependencySpec spec = d.getSpec();
      myGroupIdLabel.setText(spec.getGroup());
      myArtifactNameLabel.setText(spec.getName());
    }

    myDependency = d;
  }

  private void displayVersion(@NotNull PsDeclaredLibraryDependency dependency) {
    if (myVersionPropertyEditor != null) {
      if (dependency == myDependency) {
        myVersionPropertyEditor.reloadIfNotChanged();
      } else {
        if (myEditorComponent != null) {
          myRequestedVersion.remove(myEditorComponent);
        }
        Disposer.dispose(myVersionPropertyEditor);
        myVersionPropertyEditor = null; // remake the editor below
      }
    }
    if (myVersionPropertyEditor == null) {
      myVersionPropertyEditor =
        DeclaredLibraryDependencyUiProperties.INSTANCE.makeVersionUiProperty(dependency)
          .createEditor(myContext, dependency.getParent().getParent(), dependency.getParent(), Unit.INSTANCE, null);
      myEditorComponent = myVersionPropertyEditor.getComponent();
      myRequestedVersion.add(myEditorComponent);
    }
  }

  @Override
  @NotNull
  public Class<PsDeclaredLibraryDependency> getSupportedModelType() {
    return PsDeclaredLibraryDependency.class;
  }

  @Override
  @Nullable
  public PsDeclaredLibraryDependency getModel() {
    return myDependency;
  }

  @Override
  public PsContext getContext() {
    return myContext;
  }

  @Override
  public JPanel getConfigurationUI() {
    return myConfigurationPanel;
  }
}
