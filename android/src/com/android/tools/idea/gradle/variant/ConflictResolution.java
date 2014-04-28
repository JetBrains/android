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
import com.android.tools.idea.gradle.messages.Message;
import com.android.tools.idea.gradle.messages.ProjectSyncMessages;
import com.android.tools.idea.gradle.service.notification.NotificationHyperlink;
import com.android.tools.idea.gradle.util.GradleUtil;
import com.android.tools.idea.gradle.variant.view.BuildVariantView;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static com.android.tools.idea.gradle.messages.CommonMessageGroupNames.PROJECT_STRUCTURE_CONFLICTS;
import static com.android.tools.idea.gradle.messages.CommonMessageGroupNames.VARIANT_SELECTION_CONFLICTS;

public final class ConflictResolution {
  private ConflictResolution() {
  }

  public static void updateConflicts(@NotNull Project project) {
    ConflictSet conflicts = findConflicts(project);
    List<Conflict> selectionConflicts = conflicts.getSelectionConflicts();
    displaySelectionConflicts(project, selectionConflicts);
    BuildVariantView view = BuildVariantView.getInstance(project);
    view.updateNotification(selectionConflicts);
    view.updateContents();
  }

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

  public static boolean solveSelectionConflict(@NotNull Conflict conflict) {
    AndroidFacet facet = AndroidFacet.getInstance(conflict.getSource());
    if (facet == null || !facet.isGradleProject()) {
      // project structure may have changed and the conflict is not longer applicable.
      return true;
    }
    IdeaAndroidProject source = facet.getIdeaAndroidProject();
    if (source == null) {
      return false;
    }
    Collection<String> variants = conflict.getVariants();
    assert variants.size() == 1;
    String expectedVariant = ContainerUtil.getFirstItem(variants);
    if (StringUtil.isNotEmpty(expectedVariant)) {
      source.setSelectedVariantName(expectedVariant);
      facet.syncSelectedVariant();
      return true;
    }
    return false;
  }

  @Nullable
  private static IdeaAndroidProject getAndroidProject(@NotNull Module module) {
    AndroidFacet facet = AndroidFacet.getInstance(module);
    if (facet == null || !facet.isGradleProject()) {
      return null;
    }
    return facet.getIdeaAndroidProject();
  }

  public static void displaySelectionConflicts(@NotNull final Project project, @NotNull List<Conflict> conflicts) {
    String groupName = VARIANT_SELECTION_CONFLICTS;

    ProjectSyncMessages messages = ProjectSyncMessages.getInstance(project);
    messages.removeMessages(groupName);

    for (final Conflict conflict : conflicts) {
      String text = getText(conflict);

      final Module source = conflict.getSource();
      String hyperlinkText = String.format("Select '%1$s' in \"Build Variants\" window", source.getName());

      NotificationHyperlink selectInBuildVariantsWindowHyperlink =
        new NotificationHyperlink("select.conflict.in.variants.window", hyperlinkText) {
        @Override
        protected void execute(@NotNull Project project) {
          BuildVariantView.getInstance(project).selectAndScrollTo(source);
        }
      };

      NotificationHyperlink quickFixHyperlink = new NotificationHyperlink("fix.conflict", "Fix problem") {
        @Override
        protected void execute(@NotNull Project project) {
          boolean solved = solveSelectionConflict(conflict);
          if (solved) {
            updateConflicts(project);
          }
        }
      };

      Message msg = new Message(groupName, Message.Type.ERROR, text);
      messages.add(msg, selectInBuildVariantsWindowHyperlink, quickFixHyperlink);
    }
  }

  @NotNull
  public static String getText(@NotNull Conflict conflict) {
    Module source = conflict.getSource();
    String text = String.format("Module '%1$s' has variant '%2$s' selected, ", source.getName(), conflict.getSelectedVariant());

    List<String> expectedVariants = Lists.newArrayList(conflict.getVariants());
    assert expectedVariants.size() == 1;

    String expectedVariant = expectedVariants.get(0);
    List<String> modules = Lists.newArrayList();
    for (Conflict.AffectedModule affected : conflict.getModulesExpectingVariant(expectedVariant)) {
      modules.add("'" + affected.getTarget().getName() + "'");

    }
    Collections.sort(modules);
    text += String.format("but variant '%1$s' is expected by module(s) %2$s.", expectedVariant, modules);

    return text;
  }

  public static void displayStructureConflicts(@NotNull final Project project, @NotNull List<Conflict> conflicts) {
    String groupName = PROJECT_STRUCTURE_CONFLICTS;

    ProjectSyncMessages messages = ProjectSyncMessages.getInstance(project);
    messages.removeMessages(groupName);

    for (Conflict conflict : conflicts) {
      List<String> text = Lists.newArrayList();
      final Module conflictSource = conflict.getSource();
      text.add(String.format("Module '%1$s' has variant '%2$s' selected.", conflictSource.getName(), conflict.getSelectedVariant()));

      List<String> expectedVariants = Lists.newArrayList(conflict.getVariants());
      Collections.sort(expectedVariants);

      for (String expectedVariant : expectedVariants) {
        List<String> modules = Lists.newArrayList();
        for (Conflict.AffectedModule affected : conflict.getModulesExpectingVariant(expectedVariant)) {
          modules.add("'" + affected.getTarget().getName() + "'");

        }
        Collections.sort(modules);
        text.add(String.format("- Variant '%1$s' expected by module(s) %2$s.", expectedVariant, modules));
        text.add("");
      }

      messages.add(new Message(groupName, Message.Type.ERROR, text.toArray(new String[text.size()])));
    }
  }
}
