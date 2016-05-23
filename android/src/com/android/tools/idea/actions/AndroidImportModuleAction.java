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
package com.android.tools.idea.actions;

import com.android.tools.idea.gradle.project.ModuleImporter;
import com.android.tools.idea.gradle.project.ModuleToImport;
import com.android.tools.idea.npw.NewModuleWizard;
import com.google.common.collect.Maps;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.Map;

/**
 * Action for importing existing sources as an Android project modules.
 */
public class AndroidImportModuleAction extends AnAction implements DumbAware {
  public AndroidImportModuleAction() {
    super("Import Module...");
  }

  /**
   * Imports sources from a given location as a new IDE project module. Wizard will be
   * shown if the import source location was not specified or more then one module
   * will be imported.
   *
   * @throws java.io.IOException if an error condition prevents the module from being imported.
   */
  public static void importGradleSubprojectAsModule(@Nullable VirtualFile importSource,
                                                    @NotNull Project destinationProject)
      throws IOException {
    if (importSource != null && performImportWithoutUI(importSource, destinationProject)) {
      return;
    }
    NewModuleWizard wizard = NewModuleWizard.createImportModuleWizard(destinationProject, importSource);
    if (wizard.showAndGet()) {
      wizard.createModule(true);
    }
  }

  private static boolean performImportWithoutUI(VirtualFile importSource, Project destinationProject) throws IOException {
    for (ModuleImporter importer : ModuleImporter.getAllImporters(destinationProject)) {
      if (importer.canImport(importSource)) {
        Map<String, VirtualFile> modules = Maps.newHashMap();
        for (ModuleToImport module : importer.findModules(importSource)) {
          modules.put(module.name, module.location);
        }
        importer.importProjects(modules);
        return true;
      }
    }
    return false;
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    Project project = e.getProject();
    if (project != null) {
      try {
        importGradleSubprojectAsModule(null, project);
      }
      catch (IOException e1) {
        Logger.getInstance(getClass()).error(e1);
      }
    }
  }

  @Override
  public void update(AnActionEvent e) {
    Project project = e.getProject();
    e.getPresentation().setEnabled(project != null);
  }
}
