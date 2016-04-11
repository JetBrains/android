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

import com.android.builder.model.AndroidProject;
import com.android.tools.idea.gradle.AndroidGradleModel;
import com.android.tools.idea.gradle.dsl.model.GradleBuildModel;
import com.android.tools.idea.gradle.dsl.model.repositories.JCenterDefaultRepositoryModel;
import com.android.tools.idea.gradle.dsl.model.repositories.MavenCentralRepositoryModel;
import com.android.tools.idea.gradle.dsl.model.repositories.RepositoryModel;
import com.android.tools.idea.gradle.structure.configurables.ui.ArtifactRepositorySearchForm;
import com.android.tools.idea.gradle.structure.model.PsModule;
import com.android.tools.idea.gradle.structure.model.android.PsAndroidModule;
import com.android.tools.idea.gradle.structure.model.repositories.search.AndroidSdkRepository;
import com.android.tools.idea.gradle.structure.model.repositories.search.ArtifactRepository;
import com.android.tools.idea.gradle.structure.model.repositories.search.JCenterRepository;
import com.android.tools.idea.gradle.structure.model.repositories.search.MavenCentralRepository;
import com.google.common.collect.Lists;
import com.intellij.openapi.Disposable;
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
    myLibraryLabel.setBorder(getTextFieldBorder());
    myLibraryLabel.setBackground(getTextFieldBackground());
    myLibraryLabel.setText(" ");

    List<ArtifactRepository> repositories = Lists.newArrayList();
    GradleBuildModel parsedModel = module.getParsedModel();
    if (parsedModel != null) {
      for (RepositoryModel repositoryModel : parsedModel.repositories().repositories()) {
        if (repositoryModel instanceof JCenterDefaultRepositoryModel) {
          repositories.add(new JCenterRepository());
          continue;
        }
        if (repositoryModel instanceof MavenCentralRepositoryModel) {
          repositories.add(new MavenCentralRepository());
        }
      }
    }

    AndroidProject androidProject = null;
    if (module instanceof PsAndroidModule) {
      AndroidGradleModel gradleModel = ((PsAndroidModule)module).getGradleModel();
      androidProject = gradleModel.getAndroidProject();
    }
    repositories.add(new AndroidSdkRepository(androidProject));

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

  @Override
  public void dispose() {
  }
}
