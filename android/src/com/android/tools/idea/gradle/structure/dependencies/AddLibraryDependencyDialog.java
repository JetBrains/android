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
package com.android.tools.idea.gradle.structure.dependencies;

import com.android.tools.idea.gradle.structure.dependencies.android.AndroidDependencyScopesPanel;
import com.android.tools.idea.gradle.structure.model.PsArtifactDependencySpec;
import com.android.tools.idea.gradle.structure.model.PsModule;
import com.android.tools.idea.gradle.structure.model.android.PsAndroidModule;
import com.android.tools.idea.gradle.structure.model.android.PsLibraryAndroidDependency;
import com.android.tools.idea.gradle.structure.model.android.dependency.PsNewDependencyScopes;
import com.android.tools.idea.gradle.structure.model.java.PsJavaModule;
import com.intellij.openapi.ui.ValidationInfo;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Ref;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.net.UnknownHostException;
import java.util.List;
import java.util.Objects;

import static com.intellij.openapi.util.text.StringUtil.isEmpty;
import static com.intellij.openapi.util.text.StringUtil.isNotEmpty;

public class AddLibraryDependencyDialog extends AbstractAddDependenciesDialog {
  @NotNull public static final String TITLE = "Add Library Dependency";

  private LibraryDependenciesForm myLibraryDependenciesForm;

  public AddLibraryDependencyDialog(@NotNull PsModule module) {
    super(module);
    setTitle(TITLE);
  }

  @Override
  public void addNewDependencies() {
    String library = myLibraryDependenciesForm.getSelectedLibrary();
    assert library != null;

    AbstractDependencyScopesPanel scopesPanel = getScopesPanel();
    List<String> scopesNames = scopesPanel.getSelectedScopeNames();

    PsModule module = getModule();
    if (module instanceof PsAndroidModule) {
      PsNewDependencyScopes newScopes = ((AndroidDependencyScopesPanel)scopesPanel).getNewScopes();
      assert newScopes != null;

      ((PsAndroidModule)module).addLibraryDependency(library, newScopes, scopesNames);
    }
    else if (module instanceof PsJavaModule) {
      ((PsJavaModule)module).addLibraryDependency(library, scopesNames);
    }
  }

  @Override
  @NotNull
  protected String getSplitterProportionKey() {
    return "psd.add.library.dependency.main.horizontal.splitter.proportion";
  }

  @Override
  @NotNull
  protected JComponent getDependencySelectionView() {
    if (myLibraryDependenciesForm == null) {
      myLibraryDependenciesForm = new LibraryDependenciesForm(getModule());
    }
    return myLibraryDependenciesForm.getPanel();
  }

  @Override
  @NotNull
  protected String getInstructions() {
    return "Use the form below to find the library to add. This form uses the repositories specified in  the project's build files (e.g. " +
           "JCenter, Maven Central, etc.)";
  }

  @Override
  @NotNull
  protected String getDimensionServiceKey() {
    return "psd.add.library.dependency.panel.dimension";
  }

  @Override
  @Nullable
  public JComponent getPreferredFocusedComponent() {
    return myLibraryDependenciesForm != null ? myLibraryDependenciesForm.getPreferredFocusedComponent() : null;
  }

  @Override
  protected void dispose() {
    super.dispose();
    if (myLibraryDependenciesForm != null) {
      Disposer.dispose(myLibraryDependenciesForm);
    }
  }

  @Override
  @Nullable
  protected ValidationInfo doValidate() {
    List<Exception> searchErrors = myLibraryDependenciesForm.getSearchErrors();
    if (!searchErrors.isEmpty()) {
      StringBuilder buffer = new StringBuilder();
      searchErrors.forEach(e -> buffer.append(getErrorMessage(e)).append("\n"));
      return new ValidationInfo(buffer.toString(), myLibraryDependenciesForm.getPreferredFocusedComponent());
    }

    String selectedLibrary = myLibraryDependenciesForm.getSelectedLibrary();
    if (isEmpty(selectedLibrary)) {
      return new ValidationInfo("Please specify the library to add as dependency", myLibraryDependenciesForm.getPreferredFocusedComponent());
    }
    PsArtifactDependencySpec spec = PsArtifactDependencySpec.create(selectedLibrary);
    PsModule module = getModule();
    if (spec != null && module instanceof PsAndroidModule) {
      PsAndroidModule androidModule = (PsAndroidModule)module;
      Ref<Boolean> found = new Ref<>(false);
      androidModule.forEachDeclaredDependency(dependency -> {
        if (dependency instanceof PsLibraryAndroidDependency) {
          PsLibraryAndroidDependency libraryDependency = (PsLibraryAndroidDependency)dependency;
          PsArtifactDependencySpec resolvedSpec = libraryDependency.getResolvedSpec();
          if (Objects.equals(spec.group, resolvedSpec.group) && Objects.equals(spec.name, resolvedSpec.name)) {
            found.set(true);
          }
        }
      });

      if (found.get()) {
        String msg = String.format("Library '%1$s' is already a dependency", spec.name);
        return new ValidationInfo(msg, myLibraryDependenciesForm.getPreferredFocusedComponent());
      }
    }

    return getScopesPanel().validateInput();
  }

  @NotNull
  private static String getErrorMessage(@NotNull Exception error) {
    if (error instanceof UnknownHostException) {
      return "Failed to connect to host '" + error.getMessage() + "'. Please check your Internet connection.";
    }

    String msg = error.getMessage();
    if (isNotEmpty(msg)) {
      return msg;
    }
    return error.getClass().getName();
  }
}
