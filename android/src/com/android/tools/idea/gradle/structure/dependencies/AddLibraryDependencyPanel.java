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

import com.android.tools.idea.gradle.structure.dependencies.android.AndroidDependencyScopesForm;
import com.android.tools.idea.gradle.structure.model.PsArtifactDependencySpec;
import com.android.tools.idea.gradle.structure.model.PsModule;
import com.android.tools.idea.gradle.structure.model.android.PsAndroidModule;
import com.android.tools.idea.gradle.structure.model.android.PsLibraryAndroidDependency;
import com.android.tools.idea.gradle.structure.model.android.dependency.PsNewDependencyScopes;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.ui.OnePixelDivider;
import com.intellij.openapi.ui.ValidationInfo;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Ref;
import com.intellij.ui.JBSplitter;
import com.intellij.ui.SideBorder;
import com.intellij.ui.components.JBLabel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.Border;
import java.awt.*;
import java.net.UnknownHostException;
import java.util.List;
import java.util.Objects;

import static com.intellij.openapi.util.text.StringUtil.isEmpty;
import static com.intellij.openapi.util.text.StringUtil.isNotEmpty;
import static com.intellij.ui.SideBorder.BOTTOM;
import static com.intellij.util.ui.UIUtil.getButtonFont;
import static com.intellij.util.ui.UIUtil.getTreeFont;
import static javax.swing.BorderFactory.createCompoundBorder;
import static javax.swing.BorderFactory.createEmptyBorder;
import static org.jetbrains.android.util.AndroidUiUtil.setUpAsHtmlLabel;

class AddLibraryDependencyPanel extends JPanel implements Disposable {
  @NotNull private final PsModule myModule;
  @NotNull private final LibraryDependencyForm myLibraryDependencyForm;
  @NotNull private final DependencyScopesForm myScopesForm;

  AddLibraryDependencyPanel(@NotNull PsModule module) {
    super(new BorderLayout());
    myModule = module;

    JBSplitter splitter = new JBSplitter(true, "psd.add.library.dependency.main.horizontal.splitter.proportion", .55f);
    splitter.setBorder(createEmptyBorder());

    myLibraryDependencyForm = new LibraryDependencyForm(myModule);
    JPanel libraryDependencyPanel = myLibraryDependencyForm.getPanel();
    libraryDependencyPanel.setBorder(createMainPanelBorder());

    splitter.setFirstComponent(libraryDependencyPanel);

    if (myModule instanceof PsAndroidModule) {
      myScopesForm = new AndroidDependencyScopesForm((PsAndroidModule)myModule);
      JPanel scopesPanel = myScopesForm.getPanel();
      scopesPanel.setBorder(createMainPanelBorder());
      splitter.setSecondComponent(scopesPanel);
    }
    else {
      // TODO Implement "configurations" for Java modules
      myScopesForm = null;
    }

    add(splitter, BorderLayout.CENTER);
    add(new TitlePanel(myModule), BorderLayout.NORTH);
  }

  @NotNull
  private static Border createMainPanelBorder() {
    return createCompoundBorder(new SideBorder(OnePixelDivider.BACKGROUND, BOTTOM), createEmptyBorder(5, 5, 5, 5));
  }

  @NotNull
  JComponent getPreferredFocusedComponent() {
    return myLibraryDependencyForm.getPreferredFocusedComponent();
  }

  @Nullable
  ValidationInfo validateInput() {
    List<Exception> searchErrors = myLibraryDependencyForm.getSearchErrors();
    if (!searchErrors.isEmpty()) {
      StringBuilder buffer = new StringBuilder();
      searchErrors.forEach(e -> buffer.append(getErrorMessage(e)).append("\n"));
      return new ValidationInfo(buffer.toString(), myLibraryDependencyForm.getPreferredFocusedComponent());
    }

    String selectedLibrary = myLibraryDependencyForm.getSelectedLibrary();
    if (isEmpty(selectedLibrary)) {
      return new ValidationInfo("Please specify the library to add as dependency", myLibraryDependencyForm.getPreferredFocusedComponent());
    }
    PsArtifactDependencySpec spec = PsArtifactDependencySpec.create(selectedLibrary);
    if (spec != null && myModule instanceof PsAndroidModule) {
      PsAndroidModule androidModule = (PsAndroidModule)myModule;
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
        return new ValidationInfo(msg, myLibraryDependencyForm.getPreferredFocusedComponent());
      }
    }

    return myScopesForm.validateInput();
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

  public void addNewDependency() {
    String library = myLibraryDependencyForm.getSelectedLibrary();
    assert library != null;

    List<String> scopesNames = myScopesForm.getSelectedScopesNames();
    if (myScopesForm instanceof AndroidDependencyScopesForm && myModule instanceof PsAndroidModule) {
      PsNewDependencyScopes newScopes = ((AndroidDependencyScopesForm)myScopesForm).getNewScopes();
      assert newScopes != null;

      ((PsAndroidModule)myModule).addLibraryDependency(library, newScopes, scopesNames);
    }
  }

  @Override
  public void dispose() {
    Disposer.dispose(myScopesForm);
    Disposer.dispose(myLibraryDependencyForm);
  }

  private static class TitlePanel extends JPanel {
    TitlePanel(@NotNull PsModule module) {
      super(new BorderLayout());
      JBLabel titleLabel = new JBLabel();
      titleLabel.setFont(getButtonFont().deriveFont(Font.BOLD));
      titleLabel.setIcon(module.getIcon());
      titleLabel.setText(String.format("Module '%1$s'", module.getName()));
      add(titleLabel, BorderLayout.NORTH);

      JEditorPane instructionsPane = new JEditorPane();
      setUpAsHtmlLabel(instructionsPane, getTreeFont());
      instructionsPane.setText("<html><body><b>Step 1.</b><br/>" +
                               "Use the form below to find the library to add. This form uses the repositories specified in " +
                               "the project's build files (e.g. JCenter, Maven Central, etc.)</body></html>");
      instructionsPane.setBorder(createEmptyBorder(8, 5, 0, 5));
      add(instructionsPane, BorderLayout.CENTER);

      setBorder(createEmptyBorder(5, 5, 5, 5));
    }
  }
}
