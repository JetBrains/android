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
package com.android.tools.idea.gradle.variant;

import com.android.builder.model.AndroidLibrary;
import com.android.tools.idea.gradle.IdeaAndroidProject;
import com.android.tools.idea.gradle.facet.AndroidGradleFacet;
import com.android.tools.idea.gradle.messages.AbstractNavigatable;
import com.android.tools.idea.gradle.messages.Message;
import com.android.tools.idea.gradle.messages.ProjectSyncMessages;
import com.android.tools.idea.gradle.variant.view.BuildVariantView;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.*;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.pom.Navigatable;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

import static com.android.tools.idea.gradle.messages.CommonMessageGroupNames.VARIANT_SELECTION_CONFLICTS;

public class VariantSelectionVerifier {
  @NotNull private final Project myProject;

  @NotNull
  public static VariantSelectionVerifier getInstance(@NotNull Project project) {
    return ServiceManager.getService(project, VariantSelectionVerifier.class);
  }

  public VariantSelectionVerifier(@NotNull Project project) {
    myProject = project;
  }

  public void findAndShowSelectionConflicts() {
    ImmutableList<SelectionConflict> conflicts = findSelectionConflicts();

    ProjectSyncMessages messages = ProjectSyncMessages.getInstance(myProject);
    messages.removeMessages(VARIANT_SELECTION_CONFLICTS);

    for (SelectionConflict conflict : conflicts) {
      List<String> text = Lists.newArrayList();
      final Module conflictSource = conflict.getSource();
      text.add(String.format("Module '%1$s' has variant '%2$s' selected.", conflictSource.getName(), conflict.getSelectedVariant()));

      Multimap<String, String> affectedModulesByVariant = ArrayListMultimap.create();
      TreeSet<String> expectedVariants = Sets.newTreeSet();

      for (SelectionConflict.AffectedModule affectedModule : conflict.getAffectedModules()) {
        String expectedVariant = affectedModule.getExpectedVariant();
        expectedVariants.add(expectedVariant);
        String name = affectedModule.getTarget().getName();
        affectedModulesByVariant.put(expectedVariant, "'" + name + "'");
      }

      for (String expectedVariant : expectedVariants) {
        List<String> modules = Lists.newArrayList(affectedModulesByVariant.get(expectedVariant));
        Collections.sort(modules);
        text.add(String.format("- Variant '%1$s' expected by module(s) %2$s.", expectedVariant, modules));
        text.add("");
      }

      Navigatable navigateToConflictSource = new AbstractNavigatable() {
        @Override
        public void navigate(boolean requestFocus) {
          BuildVariantView.getInstance(myProject).selectAndScrollTo(conflictSource);
        }

        @Override
        public boolean canNavigate() {
          return true;
        }
      };

      messages.add(new Message(VARIANT_SELECTION_CONFLICTS, Message.Type.ERROR, navigateToConflictSource, text.toArray(new String[text.size()])));
    }

    BuildVariantView.getInstance(myProject).updateNotification(conflicts);
  }

  @VisibleForTesting
  @NotNull
  ImmutableList<SelectionConflict> findSelectionConflicts() {
    Map<String, SelectionConflict> conflictsByModuleName = Maps.newHashMap();

    ModuleManager moduleManager = ModuleManager.getInstance(myProject);
    for (Module module : moduleManager.getModules()) {
      String gradlePath = getGradlePath(module);
      if (StringUtil.isEmpty(gradlePath)) {
        continue;
      }
      IdeaAndroidProject libraryProject = getAndroidProject(module, true);
      if (libraryProject == null || !libraryProject.getDelegate().isLibrary()) {
        continue;
      }

      String selectedVariant = libraryProject.getSelectedVariant().getName();

      for (Module dependent : ModuleUtilCore.getAllDependentModules(module)) {
        IdeaAndroidProject androidProject = getAndroidProject(dependent, false);
        if (androidProject == null) {
          continue;
        }
        for (AndroidLibrary library : androidProject.getSelectedVariant().getMainArtifact().getDependencies().getLibraries()) {
          if (!gradlePath.equals(library.getProject())) {
            continue;
          }
          String expected = library.getProjectVariant();
          if (StringUtil.isNotEmpty(expected) && !selectedVariant.equals(expected)) {
            String causeName = module.getName();
            SelectionConflict conflict = conflictsByModuleName.get(causeName);
            if (conflict == null) {
              conflict = new SelectionConflict(module, selectedVariant);
              conflictsByModuleName.put(causeName, conflict);
            }
            conflict.addAffectedModule(dependent, expected);
          }
        }
      }
    }
    return ImmutableList.copyOf(conflictsByModuleName.values());
  }

  @Nullable
  private static IdeaAndroidProject getAndroidProject(@NotNull Module module, boolean libraryProject) {
    AndroidFacet facet = AndroidFacet.getInstance(module);
    if (facet == null || !facet.isGradleProject()) {
      return null;
    }
    IdeaAndroidProject androidProject = facet.getIdeaAndroidProject();
    if (androidProject != null && androidProject.getDelegate().isLibrary() == libraryProject) {
      return androidProject;
    }
    return null;
  }

  @Nullable
  private static String getGradlePath(@NotNull Module module) {
    AndroidGradleFacet facet = AndroidGradleFacet.getInstance(module);
    return facet != null ? facet.getConfiguration().GRADLE_PROJECT_PATH : null;
  }
}
