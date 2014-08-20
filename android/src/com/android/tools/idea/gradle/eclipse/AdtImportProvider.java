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
package com.android.tools.idea.gradle.eclipse;

import com.intellij.ide.util.projectWizard.ModuleWizardStep;
import com.intellij.ide.util.projectWizard.WizardContext;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.projectImport.ProjectImportProvider;
import org.jetbrains.annotations.Nullable;

public class AdtImportProvider extends ProjectImportProvider {
  private final boolean myProjectImport;

  public AdtImportProvider(boolean projectImport) {
    this(new AdtImportBuilder(projectImport), projectImport);
  }

  public AdtImportProvider(AdtImportBuilder builder, boolean projectImport) {
    super(builder);
    myProjectImport = projectImport;
  }

  @Override
  public ModuleWizardStep[] createSteps(WizardContext context) {
    if (myProjectImport) {
      return new ModuleWizardStep[]{
        new AdtImportLocationStep(context),
        new AdtImportPrefsStep(context),
        new AdtWorkspaceForm(context),
        new AdtImportSdkStep(context),
        new AdtRepositoriesStep(context),
        new AdtImportWarningsStep(context)
      };
    } else {
      return new ModuleWizardStep[]{
        new AdtImportPrefsStep(context),
        new AdtWorkspaceForm(context),
        new AdtImportSdkStep(context),
        new AdtRepositoriesStep(context),
        new AdtImportWarningsStep(context)
      };
    }
  }

  @Override
  protected boolean canImportFromFile(VirtualFile file) {
    VirtualFile dir = file;
    if (!dir.isDirectory()) {
      dir = dir.getParent();
    }
    return dir != null && GradleImport.isAdtProjectDir(VfsUtilCore.virtualToIoFile(dir));
  }

  @Nullable
  @Override
  public String getFileSample() {
    return "<b>Eclipse</b> Android project (.project) or classpath (.classpath) file";
  }

  @Nullable
  static GradleImport getImporter(@Nullable WizardContext context) {
    if (context != null) {
      AdtImportBuilder builder = (AdtImportBuilder)context.getProjectBuilder();
      if (builder != null) {
        return builder.getImporter();
      }
    }
    return null;
  }
}
