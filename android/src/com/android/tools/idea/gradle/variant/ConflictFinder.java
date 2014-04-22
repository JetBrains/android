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
import com.android.tools.idea.gradle.util.GradleUtil;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;

public class ConflictFinder {
  @NotNull
  public static ConflictSet findConflicts(@NotNull Project project) {
    Map<String, Conflict> selectionConflicts = Maps.newHashMap();
    Map<String, Conflict> structureConflicts = Maps.newHashMap();

    ModuleManager moduleManager = ModuleManager.getInstance(project);
    for (Module module : moduleManager.getModules()) {
      IdeaAndroidProject currentProject = getAndroidProject(module);
      if (currentProject == null || !currentProject.isLibrary()) {
        continue;
      }
      String gradlePath = GradleUtil.getGradlePath(module);
      assert gradlePath != null;

      String selectedVariant = currentProject.getSelectedVariant().getName();

      for (Module dependent : ModuleUtilCore.getAllDependentModules(module)) {
        IdeaAndroidProject dependentProject = getAndroidProject(dependent);
        if (dependentProject == null) {
          continue;
        }
        String expectedVariant = getExpectedVariant(dependentProject, gradlePath);
        if (StringUtil.isEmpty(expectedVariant)) {
          continue;
        }
        addConflict(structureConflicts, module, selectedVariant, dependent, expectedVariant);

        if (!selectedVariant.equals(expectedVariant)) {
          addConflict(selectionConflicts, module, selectedVariant, dependent, expectedVariant);
        }
      }
    }

    // Structural conflicts are the ones that have more than one group of modules depending on different variants of another module.
    List<Conflict> structure = Lists.newArrayList();
    for (Conflict conflict : structureConflicts.values()) {
      if (conflict.getVariants().size() > 1) {
        structure.add(conflict);
      }
    }

    List<Conflict> selection = Lists.newArrayList();
    for (Conflict conflict : selectionConflicts.values()) {
      if (conflict.getVariants().size() == 1) {
        selection.add(conflict);
      }
    }

    return new ConflictSet(selection, structure);
  }

  private static void addConflict(@NotNull Map<String, Conflict> allConflicts,
                                  @NotNull Module source,
                                  @NotNull String selectedVariant,
                                  @NotNull Module affected,
                                  @NotNull String expectedVariant) {
    String causeName = source.getName();
    Conflict conflict = allConflicts.get(causeName);
    if (conflict == null) {
      conflict = new Conflict(source, selectedVariant);
      allConflicts.put(causeName, conflict);
    }
    conflict.addAffectedModule(affected, expectedVariant);
  }

  @Nullable
  private static String getExpectedVariant(@NotNull IdeaAndroidProject dependentProject, @NotNull String dependencyGradlePath) {
    List<AndroidLibrary> dependencies = GradleUtil.getDirectLibraryDependencies(dependentProject.getSelectedVariant());
    for (AndroidLibrary dependency : dependencies) {
      if (!dependencyGradlePath.equals(dependency.getProject())) {
        continue;
      }
      return dependency.getProjectVariant();
    }
    return null;
  }

  @Nullable
  private static IdeaAndroidProject getAndroidProject(@NotNull Module module) {
    AndroidFacet facet = AndroidFacet.getInstance(module);
    if (facet == null || !facet.isGradleProject()) {
      return null;
    }
    return facet.getIdeaAndroidProject();
  }
}
