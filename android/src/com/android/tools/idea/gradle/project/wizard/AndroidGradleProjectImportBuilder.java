/*
 * Copyright (C) 2013 The Android Open Source Project
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
package com.android.tools.idea.gradle.project.wizard;

import com.intellij.ide.util.projectWizard.WizardContext;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.externalSystem.service.project.manage.ProjectDataManager;
import com.intellij.openapi.options.ConfigurationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.service.project.wizard.GradleProjectImportBuilder;

public class AndroidGradleProjectImportBuilder extends GradleProjectImportBuilder {
  public AndroidGradleProjectImportBuilder() {
    this(ServiceManager.getService(ProjectDataManager.class));
  }

  public AndroidGradleProjectImportBuilder(@NotNull ProjectDataManager dataManager) {
    super(dataManager);
  }

  @Override
  public void ensureProjectIsDefined(@NotNull WizardContext wizardContext) throws ConfigurationException {
    // don't do anything to avoid the "double import" problem with the original GradleProjectImportBuilder.
  }
}
