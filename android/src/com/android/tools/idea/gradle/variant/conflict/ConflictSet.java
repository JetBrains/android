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
package com.android.tools.idea.gradle.variant.conflict;

import com.android.builder.model.AndroidLibrary;
import com.android.tools.idea.gradle.AndroidGradleModel;
import com.android.tools.idea.gradle.messages.Message;
import com.android.tools.idea.gradle.messages.ProjectSyncMessages;
import com.android.tools.idea.gradle.service.notification.hyperlink.NotificationHyperlink;
import com.android.tools.idea.gradle.util.GradleUtil;
import com.android.tools.idea.gradle.variant.view.BuildVariantView;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;
import java.util.Map;

import static com.android.tools.idea.gradle.messages.CommonMessageGroupNames.VARIANT_SELECTION_CONFLICTS;
import static com.android.tools.idea.gradle.util.GradleUtil.getDirectLibraryDependencies;
import static com.android.tools.idea.gradle.util.Projects.getAndroidModel;
import static com.android.tools.idea.gradle.variant.conflict.ConflictResolution.solveSelectionConflict;

/**
 * Set of all variant-selection-related conflicts. We classify these conflicts in 2 groups:
 * <ol>
 * <li>
 * <b>Selection conflicts.</b> These conflicts occur when module A depends on module B/variant X but module B has variant Y selected
 * instead. These conflicts can be easily fixed by selecting the right variant in the "Build Variants" tool window.
 * </li>
 * <b>Structure conflicts.</b> These conflicts occur when there are multiple modules depending on different variants of a single module.
 * For example, module A depends on module E/variant X, module B depends on module E/variant Y and module C depends on module E/variant Z.
 * These conflicts cannot be resolved through the "Build Variants" tool window because regardless of the variant is selected on module E,
 * we will always have a selection conflict. These conflicts can be resolved by importing a subset of modules into the IDE (i.e. project
 * profiles.)
 * </ol>
 */
public class ConflictSet {
  @NotNull
  public static ConflictSet findConflicts(@NotNull Project project) {
    Map<String, Conflict> selectionConflicts = Maps.newHashMap();
    Map<String, Conflict> structureConflicts = Maps.newHashMap();

    ModuleManager moduleManager = ModuleManager.getInstance(project);
    for (Module module : moduleManager.getModules()) {
      AndroidGradleModel currentAndroidModel = AndroidGradleModel.get(module);
      if (currentAndroidModel == null || !currentAndroidModel.isLibrary()) {
        continue;
      }
      String gradlePath = GradleUtil.getGradlePath(module);
      if (gradlePath == null) {
        continue;
      }

      String selectedVariant = currentAndroidModel.getSelectedVariant().getName();

      for (Module dependent : ModuleUtilCore.getAllDependentModules(module)) {
        AndroidGradleModel dependentAndroidModel = AndroidGradleModel.get(dependent);
        if (dependentAndroidModel == null) {
          continue;
        }
        String expectedVariant = getExpectedVariant(dependentAndroidModel, gradlePath);
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
    List<Conflict> filteredStructureConflicts = Lists.newArrayList();
    for (Conflict conflict : structureConflicts.values()) {
      if (conflict.getVariants().size() > 1) {
        filteredStructureConflicts.add(conflict);
      }
    }
    return new ConflictSet(project, selectionConflicts.values(), filteredStructureConflicts);
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
  private static String getExpectedVariant(@NotNull AndroidGradleModel dependentAndroidModel, @NotNull String dependencyGradlePath) {
    List<AndroidLibrary> dependencies = getDirectLibraryDependencies(dependentAndroidModel.getSelectedVariant(), dependentAndroidModel);
    for (AndroidLibrary dependency : dependencies) {
      if (!dependencyGradlePath.equals(dependency.getProject())) {
        continue;
      }
      return dependency.getProjectVariant();
    }
    return null;
  }

  @NotNull private final Project myProject;
  @NotNull private final ImmutableList<Conflict> mySelectionConflicts;
  @NotNull private final ImmutableList<Conflict> myStructureConflicts;

  ConflictSet(@NotNull Project project, @NotNull Collection<Conflict> selectionConflicts, @NotNull Collection<Conflict> structureConflicts) {
    myProject = project;
    mySelectionConflicts = ImmutableList.copyOf(selectionConflicts);
    myStructureConflicts = ImmutableList.copyOf(structureConflicts);
  }

  @NotNull
  public Project getProject() {
    return myProject;
  }

  @NotNull
  public List<Conflict> getSelectionConflicts() {
    return mySelectionConflicts;
  }

  @NotNull
  public List<Conflict> getStructureConflicts() {
    return myStructureConflicts;
  }

  /**
   * Shows the "variant selection" conflicts in the "Build Variant" and "Messages" windows.
   */
  public void showSelectionConflicts() {
    ProjectSyncMessages messages = ProjectSyncMessages.getInstance(myProject);
    String groupName = VARIANT_SELECTION_CONFLICTS;
    messages.removeMessages(groupName);

    for (final Conflict conflict : mySelectionConflicts) {
      // Creates the "Select in 'Build Variants' window" hyperlink.
      final Module source = conflict.getSource();
      String hyperlinkText = String.format("Select '%1$s' in \"Build Variants\" window", source.getName());
      NotificationHyperlink selectInBuildVariantsWindowHyperlink =
        new NotificationHyperlink("select.conflict.in.variants.window", hyperlinkText) {
          @Override
          protected void execute(@NotNull Project project) {
            BuildVariantView.getInstance(project).findAndSelect(source);
          }
        };

      // Creates the "Fix problem" hyperlink.
      NotificationHyperlink quickFixHyperlink = new NotificationHyperlink("fix.conflict", "Fix problem") {
        @Override
        protected void execute(@NotNull Project project) {
          boolean solved = solveSelectionConflict(conflict);
          if (solved) {
            ConflictSet conflicts = findConflicts(project);
            conflicts.showSelectionConflicts();
          }
        }
      };

      Message msg = new Message(groupName, Message.Type.ERROR, conflict.toString());
      messages.add(msg, selectInBuildVariantsWindowHyperlink, quickFixHyperlink);
    }

    BuildVariantView.getInstance(myProject).updateContents(mySelectionConflicts);
  }
}
