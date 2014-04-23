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

import com.android.builder.model.AndroidLibrary;
import com.android.builder.model.Variant;
import com.android.tools.idea.gradle.IdeaAndroidProject;
import com.android.tools.idea.gradle.customizer.ModuleCustomizer;
import com.android.tools.idea.gradle.customizer.android.CompilerOutputModuleCustomizer;
import com.android.tools.idea.gradle.customizer.android.ContentRootModuleCustomizer;
import com.android.tools.idea.gradle.customizer.android.DependenciesModuleCustomizer;
import com.android.tools.idea.gradle.util.GradleUtil;
import com.android.tools.idea.gradle.util.ProjectBuilder;
import com.android.tools.idea.gradle.variant.ConflictDisplay;
import com.android.tools.idea.gradle.variant.ConflictFinder;
import com.android.tools.idea.gradle.variant.ConflictSet;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.externalSystem.util.DisposeAwareProjectChange;
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Updates the contents/settings of a module when a build variant changes.
 */
class BuildVariantUpdater {
  private static final Logger LOG = Logger.getInstance(BuildVariantUpdater.class);

  private final List<ModuleCustomizer<IdeaAndroidProject>> myAndroidModuleCustomizers =
    ImmutableList.of(new ContentRootModuleCustomizer(), new DependenciesModuleCustomizer(), new CompilerOutputModuleCustomizer());

  /**
   * Updates a module's structure when the user selects a build variant from the tool window.
   *
   * @param project          the module's project.
   * @param moduleName       the module's name.
   * @param buildVariantName the name of the selected build variant.
   * @return the facets affected by the build variant selection, if the module update was successful; an empty list otherwise.
   */
  @NotNull
  List<AndroidFacet> updateModule(@NotNull final Project project, @NotNull final String moduleName, @NotNull final String buildVariantName) {
    final List<AndroidFacet> facets = Lists.newArrayList();
    ExternalSystemApiUtil.executeProjectChangeAction(true /*synchronous*/, new DisposeAwareProjectChange(project) {
      @Override
      public void execute() {
        Module updatedModule = doUpdate(project, moduleName, buildVariantName, facets);
        if (updatedModule != null) {
          ConflictSet conflicts = ConflictFinder.findConflicts(project);
          ConflictDisplay.displayConflicts(project, conflicts);
        }

        if (!facets.isEmpty()) {
          // We build only the selected variant. If user changes variant, we need to re-generate sources since the generated sources may not
          // be there.
          if (!ApplicationManager.getApplication().isUnitTestMode()) {
            ProjectBuilder.getInstance(project).generateSourcesOnly();
          }
        }
      }
    });
    return facets;
  }

  @Nullable
  private Module doUpdate(@NotNull Project project,
                          @NotNull String moduleName,
                          @NotNull String variant,
                          @NotNull List<AndroidFacet> affectedFacets) {
    Module moduleToUpdate = findModule(project, moduleName);
    if (moduleToUpdate == null) {
      logAndShowUpdateFailure(variant, String.format("Cannot find module '%1$s'.", moduleName));
      return null;
    }
    AndroidFacet facet = getAndroidFacet(moduleToUpdate, variant);
    if (facet == null) {
      return null;
    }
    IdeaAndroidProject androidProject = getAndroidProject(facet, variant);
    if (androidProject == null) {
      return null;
    }

    if (!updateSelectedVariant(facet, androidProject, variant, affectedFacets)) {
      return null;
    }
    affectedFacets.add(facet);
    return moduleToUpdate;
  }

  @Nullable
  private static Module findModule(@NotNull Project project, @NotNull String moduleName) {
    ModuleManager moduleManager = ModuleManager.getInstance(project);
    return moduleManager.findModuleByName(moduleName);
  }

  private boolean updateSelectedVariant(@NotNull AndroidFacet androidFacet,
                                        @NotNull IdeaAndroidProject androidProject,
                                        @NotNull String variantToSelect,
                                        @NotNull List<AndroidFacet> affectedFacets) {
    Variant selectedVariant = androidProject.getSelectedVariant();
    if (variantToSelect.equals(selectedVariant.getName())) {
      return false;
    }
    androidProject.setSelectedVariantName(variantToSelect);

    androidFacet.syncSelectedVariant();

    Module module = androidFacet.getModule();
    Project project = module.getProject();
    for (ModuleCustomizer<IdeaAndroidProject> customizer : myAndroidModuleCustomizers) {
      customizer.customizeModule(module, project, androidProject);
    }

    selectedVariant = androidProject.getSelectedVariant();

    for (AndroidLibrary library : selectedVariant.getMainArtifact().getDependencies().getLibraries()) {
      String gradlePath = library.getProject();
      if (StringUtil.isEmpty(gradlePath)) {
        continue;
      }
      String projectVariant = library.getProjectVariant();
      if (StringUtil.isNotEmpty(projectVariant)) {
        ensureVariantIsSelected(project, gradlePath, projectVariant, affectedFacets);
      }
    }
    return true;
  }


  private void ensureVariantIsSelected(@NotNull Project project,
                                       @NotNull String moduleGradlePath,
                                       @NotNull String variant,
                                       @NotNull List<AndroidFacet> affectedFacets) {
    Module module = GradleUtil.findModuleByGradlePath(project, moduleGradlePath);
    if (module == null) {
      logAndShowUpdateFailure(variant, String.format("Cannot find module with Gradle path '%1$s'.", moduleGradlePath));
      return;
    }
    AndroidFacet facet = getAndroidFacet(module, variant);
    if (facet == null) {
      return;
    }
    IdeaAndroidProject androidProject = getAndroidProject(facet, variant);
    if (androidProject == null) {
      return;
    }

    if (!updateSelectedVariant(facet, androidProject, variant, affectedFacets)) {
      return;
    }
    affectedFacets.add(facet);
  }


  @Nullable
  private static AndroidFacet getAndroidFacet(@NotNull Module module, @NotNull String variantToSelect) {
    AndroidFacet facet = AndroidFacet.getInstance(module);
    if (facet == null) {
      logAndShowUpdateFailure(variantToSelect, String.format("Cannot find 'Android' facet in module '%1$s'.", module.getName()));
    }
    return facet;
  }

  @Nullable
  private static IdeaAndroidProject getAndroidProject(@NotNull AndroidFacet facet, @NotNull String variantToSelect) {
    IdeaAndroidProject androidProject = facet.getIdeaAndroidProject();
    if (androidProject == null) {
      logAndShowUpdateFailure(variantToSelect, String.format("Cannot find AndroidProject for module '%1$s'.", facet.getModule().getName()));
    }
    return androidProject;
  }

  private static void logAndShowUpdateFailure(@NotNull String buildVariantName, @NotNull String reason) {
    String prefix = String.format("Unable to select build variant '%1$s':\n", buildVariantName);
    String msg = prefix + reason;
    LOG.error(msg);
    msg += ".\n\nConsult IDE log for more details (Help | Show Log)";
    Messages.showErrorDialog(msg, "Error");
  }
}
