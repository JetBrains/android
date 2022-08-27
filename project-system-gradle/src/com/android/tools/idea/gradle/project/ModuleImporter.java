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
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.ContainerUtil;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Importers for different project types, e.g. ADT, Gradle.
 */
public abstract class ModuleImporter {
  private static final Logger LOG = Logger.getInstance(ModuleImporter.class);
  private static final Key<ModuleImporter[]> KEY_IMPORTERS = new Key<>("com.android.tools.importers");
  private static final Key<ModuleImporter> KEY_CURRENT_IMPORTER = new Key<>("com.android.tools.currentImporter");
  private static final ModuleImporter NONE = new ModuleImporter() {
    @Override
    public boolean isStepVisible(@NotNull ModuleWizardStep step) {
      return false;
    }

    @Override
    @NotNull
    public List<? extends ModuleWizardStep> createWizardSteps() {
      return Collections.emptyList();
    }

    @Override
    public void importProjects(@Nullable Map<String, VirtualFile> projects) {
      LOG.error("Unsupported import kind");
    }

    @Override
    public boolean isValid() {
      return false;
    }

    @Override
    public boolean canImport(@NotNull VirtualFile importSource) {
      return false;
    }

    @Override
    @NotNull
    public Set<ModuleToImport> findModules(@NotNull VirtualFile importSource) {
      return Collections.emptySet();
    }
  };

  /**
   * Importers live in the wizard context. This method lazily creates importers if they are
   * not already there.
   */
  @NotNull
  public static synchronized ModuleImporter[] getAllImporters(@NotNull WizardContext context) {
    ModuleImporter[] importers = context.getUserData(KEY_IMPORTERS);
    if (importers == null) {
      importers = createImporters(context);
    }
    return importers;
  }

  /**
   * Create supported importers.
   */
  // TODO: Consider creating an extension point
  @NotNull
  private static ModuleImporter[] createImporters(@NotNull WizardContext context) {
    ModuleImporter[] importers =
      ContainerUtil.map(AndroidModuleImporter.IMPORTER.getExtensionList(), it -> it.create(context))
        .toArray(new ModuleImporter[0]);
    context.putUserData(KEY_IMPORTERS, importers);
    return importers;
  }

  @NotNull
  public static ModuleImporter getImporter(@NotNull WizardContext context) {
    ModuleImporter importer = context.getUserData(KEY_CURRENT_IMPORTER);
    if (importer != null) {
      return importer;
    }
    else {
      return NONE;
    }
  }

  @NotNull
  public static ModuleImporter importerForLocation(WizardContext context, VirtualFile importSource) {
    for (ModuleImporter importer : getAllImporters(context)) {
      if (importer.canImport(importSource)) {
        return importer;
      }
    }
    return NONE;
  }

  public static void setImporter(@NotNull WizardContext context, @Nullable ModuleImporter importer) {
    context.putUserData(KEY_CURRENT_IMPORTER, importer);
  }

  public abstract boolean isStepVisible(@NotNull ModuleWizardStep step);

  @NotNull
  public abstract List<? extends ModuleWizardStep> createWizardSteps();

  public abstract void importProjects(@Nullable Map<String, VirtualFile> projects);

  public abstract boolean isValid();

  public abstract boolean canImport(@NotNull VirtualFile importSource);

  @NotNull
  public abstract Set<ModuleToImport> findModules(@NotNull VirtualFile importSource) throws IOException;
}
