/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.android.tools.idea.gradle.project;

import com.intellij.ide.util.projectWizard.ModuleWizardStep;
import com.intellij.ide.util.projectWizard.WizardContext;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.util.GradleConstants;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Creates new project module from source files with Gradle configuration.
 */
public final class GradleModuleImporter extends ModuleImporter {
  private final Logger LOG = Logger.getInstance(getClass());

  @Nullable private final Project myProject;
  private final boolean myIsWizard;
  private final GradleProjectImporter myImporter;

  public GradleModuleImporter(@NotNull WizardContext context) {
    this(context.getProject(), true);
  }

  public GradleModuleImporter(@NotNull Project project) {
    this(project, false);
  }

  private GradleModuleImporter(@Nullable Project project, boolean isWizard) {
    myIsWizard = isWizard;
    myProject = project;
    myImporter = GradleProjectImporter.getInstance();
  }

  public static boolean isGradleProject(VirtualFile importSource) {
    VirtualFile target = ProjectImportUtil.findImportTarget(importSource);
    return target != null && GradleConstants.EXTENSION.equals(target.getExtension());
  }

  @Override
  public boolean isStepVisible(ModuleWizardStep step) {
    return false;
  }

  @Override
  public List<? extends ModuleWizardStep> createWizardSteps() {
    return Collections.emptyList();
  }

  @Override
  public void importProjects(Map<String, VirtualFile> projects) {
    try {
      myImporter.importModules(projects, myProject, null);
    }
    catch (IOException e) {
      LOG.error(e);
    }
    catch (ConfigurationException e) {
      LOG.error(e);
    }
  }

  @Override
  public boolean isValid() {
    return true;
  }

  @Override
  public boolean canImport(VirtualFile importSource) {
    try {
      return isGradleProject(importSource) && (myIsWizard || findModules(importSource).size() == 1);
    }
    catch (IOException e) {
      LOG.error(e);
      return false;
    }
  }

  @Override
  public Set<ModuleToImport> findModules(VirtualFile importSource) throws IOException {
    assert myProject != null;
    return myImporter.getRelatedProjects(importSource, myProject);
  }
}
