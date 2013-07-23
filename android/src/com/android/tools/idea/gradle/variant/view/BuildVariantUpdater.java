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
package com.android.tools.idea.gradle.variant.view;

import com.android.tools.idea.gradle.IdeaAndroidProject;
import com.android.tools.idea.gradle.customizer.CompilerOutputPathModuleCustomizer;
import com.android.tools.idea.gradle.customizer.ContentRootModuleCustomizer;
import com.android.tools.idea.gradle.customizer.DependenciesModuleCustomizer;
import com.android.tools.idea.gradle.customizer.ModuleCustomizer;
import com.android.tools.idea.gradle.util.Facets;
import com.android.tools.idea.gradle.util.Projects;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Ref;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Updates the contents/settings of a module when a build variant changes.
 */
class BuildVariantUpdater {
  private static final Logger LOG = Logger.getInstance(BuildVariantUpdater.class);

  private final ModuleCustomizer[] myModuleCustomizers = {
    new ContentRootModuleCustomizer(), new DependenciesModuleCustomizer(), new CompilerOutputPathModuleCustomizer()
  };

  /**
   * Updates a module's structure when the user selects a build variant from the tool window.
   *
   * @param project          the module's project.
   * @param moduleName       the module's name.
   * @param buildVariantName the name of the selected build variant.
   * @return the facet containing the updated build variant, if the module update was successful; {@code null} otherwise.
   */
  @Nullable
  AndroidFacet updateModule(@NotNull final Project project, @NotNull final String moduleName, @NotNull final String buildVariantName) {
    final Ref<AndroidFacet> facetRef = new Ref<AndroidFacet>();
    ExternalSystemApiUtil.executeProjectChangeAction(true /*synchronous*/, new Runnable() {
      @Override
      public void run() {
        AndroidFacet updatedFacet = doUpdate(project, moduleName, buildVariantName);
        facetRef.set(updatedFacet);
      }
    });
    return facetRef.get();
  }

  @Nullable
  private AndroidFacet doUpdate(@NotNull Project project, @NotNull String moduleName, @NotNull String buildVariantName) {
    Module moduleToUpdate = findModule(project, moduleName);
    if (moduleToUpdate == null) {
      // Reason is not capitalized because it is a sentence fragment.
      String reason = String.format("cannot find module with name '%1$s' in project '%2$s", moduleName, project.getName());
      logAndShowUpdateFailure(buildVariantName, reason);
      return null;
    }
    AndroidFacet facet = Facets.getFirstFacetOfType(moduleToUpdate, AndroidFacet.ID);
    if (facet == null) {
      // Reason is not capitalized because it is a sentence fragment.
      String reason = String.format("cannot find 'Android' facet in module '%1$s', project '%2$s'", moduleName, project.getName());
      logAndShowUpdateFailure(buildVariantName, reason);
      return null;
    }
    final IdeaAndroidProject androidProject = facet.getIdeaAndroidProject();
    if (androidProject == null) {
      // Reason is not capitalized because it is a sentence fragment.
      String reason = String.format("cannot find AndroidProject for module '%1$s', project '%2$s'", moduleName, project.getName());
      logAndShowUpdateFailure(buildVariantName, reason);
      return null;
    }
    androidProject.setSelectedVariantName(buildVariantName);
    facet.syncSelectedVariant();

    for (ModuleCustomizer customizer : myModuleCustomizers) {
      customizer.customizeModule(moduleToUpdate, project, androidProject);
    }

    // We changed the way we build projects: now we build only the selected variant. If user changes variant, we need to re-generate sources
    // since the generated sources may not be there.
    if (!ApplicationManager.getApplication().isUnitTestMode()) {
      Projects.generateSourcesOnly(project, androidProject.getRootDirPath());
    }

    return facet;
  }

  private static void logAndShowUpdateFailure(@NotNull String buildVariantName, @NotNull String reason) {
    String prefix = String.format("Unable to select build variant '%1$s'", buildVariantName);
    LOG.error(prefix + ": " + reason);
    String msg = prefix + ".\n\nConsult IDE log for more details (Help | Show Log)";
    Messages.showErrorDialog(msg, "Error");
  }

  @Nullable
  private static Module findModule(@NotNull Project project, @NotNull String moduleName) {
    ModuleManager moduleManager = ModuleManager.getInstance(project);
    return moduleManager.findModuleByName(moduleName);
  }
}
