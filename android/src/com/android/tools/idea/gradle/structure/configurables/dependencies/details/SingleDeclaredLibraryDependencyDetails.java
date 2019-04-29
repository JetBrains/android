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
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import kotlin.Unit;
import org.jdesktop.swingx.JXLabel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class SingleDeclaredLibraryDependencyDetails implements DependencyDetails {
  private JPanel myMainPanel;

  private JXLabel myGroupIdLabel;
  private JXLabel myArtifactNameLabel;
  private JPanel myRequestedVersion;
  private JTextField myScope;

  @NotNull private final PsContext myContext;
  @Nullable private PsDeclaredLibraryDependency myDependency;
  @Nullable private ModelPropertyEditor<?> myVersionPropertyEditor;
  @Nullable private JComponent myEditorComponent;

  public SingleDeclaredLibraryDependencyDetails(@NotNull PsContext context) {
    myContext = context;
    // TODO(xof): common code with ModuleDependencyDetails.java
    myScope.addFocusListener(new FocusAdapter() {
      @Override
      public void focusLost(FocusEvent e) {
        super.focusLost(e);
        modifyConfiguration();
      }
    });
    myScope.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        modifyConfiguration();
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
    PsDeclaredLibraryDependency d = (PsDeclaredLibraryDependency) dependency;

    displayVersion(d);
    if (myDependency != dependency) {
      PsArtifactDependencySpec spec = d.getSpec();
      myGroupIdLabel.setText(spec.getGroup());
      myArtifactNameLabel.setText(spec.getName());
    }
    myScope.setText(dependency.getJoinedConfigurationNames());

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
  public PsLibraryDependency getModel() {
    return myDependency;
  }

  // TODO(xof): common code with ModuleDependencyDetails
  private void modifyConfiguration() {
    PsModule module = myDependency.getParent();
    module.modifyDependencyConfiguration(myDependency, myScope.getText());
  }
}
