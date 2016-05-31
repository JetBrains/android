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

import com.android.tools.idea.gradle.structure.configurables.ui.ArtifactRepositorySearchForm;
import com.android.tools.idea.gradle.structure.model.PsModule;
import com.android.tools.idea.gradle.structure.model.repositories.search.ArtifactRepository;
import com.intellij.openapi.Disposable;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.components.JBLabel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.List;

import static com.google.common.base.Strings.emptyToNull;
import static com.intellij.openapi.util.text.StringUtil.isEmpty;
import static com.intellij.util.ui.UIUtil.getTextFieldBackground;
import static com.intellij.util.ui.UIUtil.getTextFieldBorder;

class LibraryDependencyForm implements Disposable {
  @NotNull private final ArtifactRepositorySearchForm mySearchForm;

  private JPanel myMainPanel;
  private JBLabel myLibraryLabel;
  private JPanel mySearchPanelHost;

  LibraryDependencyForm(@NotNull PsModule module) {
    myLibraryLabel.setBorder(BorderFactory.createCompoundBorder(getTextFieldBorder(), IdeBorderFactory.createEmptyBorder(2)));
    myLibraryLabel.setBackground(getTextFieldBackground());
    myLibraryLabel.setText(" ");

    List<ArtifactRepository> repositories = module.getArtifactRepositories();

    mySearchForm = new ArtifactRepositorySearchForm(repositories);
    mySearchForm.add(selectedLibrary -> {
      String text = isEmpty(selectedLibrary) ? " " : selectedLibrary;
      myLibraryLabel.setText(text);
    }, this);

    mySearchPanelHost.add(mySearchForm.getPanel(), BorderLayout.CENTER);
  }

  @NotNull
  JComponent getPreferredFocusedComponent() {
    return mySearchForm.getPreferredFocusedComponent();
  }

  @NotNull
  JPanel getPanel() {
    return myMainPanel;
  }

  @Nullable
  String getSelectedLibrary() {
    String text = myLibraryLabel.getText().trim();
    return emptyToNull(text);
  }

  @NotNull
  List<Exception> getSearchErrors() {
    return mySearchForm.getSearchErrors();
  }

  @Override
  public void dispose() {
  }
}
