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
import com.intellij.ui.components.JBLabel;
import kotlin.Unit;
import org.jdesktop.swingx.JXLabel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class SingleDeclaredLibraryDependencyDetails implements DependencyDetails {
  private JPanel myMainPanel;

  private JXLabel myGroupIdLabel;
  private JXLabel myArtifactNameLabel;
  private JXLabel myScopeLabel;
  private JPanel myRequestedVersion;

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
    myDependency = (PsDeclaredLibraryDependency)dependency;
    PsArtifactDependencySpec spec = myDependency.getSpec();

    myGroupIdLabel.setText(spec.getGroup());
    myArtifactNameLabel.setText(spec.getName());

    if (myVersionPropertyEditor != null) {
      if (myEditorComponent != null) {
        myRequestedVersion.remove(myEditorComponent);
      }
      Disposer.dispose(myVersionPropertyEditor);
    }
    myVersionPropertyEditor =
      DeclaredLibraryDependencyUiProperties.INSTANCE.makeVersionUiProperty(myDependency)
                                                    .createEditor(myContext,
                                                                  myDependency.getParent().getParent(),
                                                                  myDependency.getParent(),
                                                                  Unit.INSTANCE,
                                                                  null);
    myEditorComponent = myVersionPropertyEditor.getComponent();
    myRequestedVersion.add(myEditorComponent);

    myScopeLabel.setText(dependency.getJoinedConfigurationNames());
  }

  @Override
  @NotNull
  public Class<PsDeclaredLibraryDependency> getSupportedModelType() {
    return PsDeclaredLibraryDependency.class;
  }

  @Override
  @Nullable
  public PsLibraryDependency getModel() {
    return myDependency;
  }
}
