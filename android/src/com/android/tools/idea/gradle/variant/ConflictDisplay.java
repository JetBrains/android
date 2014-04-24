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

import com.android.tools.idea.gradle.messages.AbstractNavigatable;
import com.android.tools.idea.gradle.messages.Message;
import com.android.tools.idea.gradle.messages.ProjectSyncMessages;
import com.android.tools.idea.gradle.variant.view.BuildVariantView;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.pom.Navigatable;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;

import static com.android.tools.idea.gradle.messages.CommonMessageGroupNames.PROJECT_STRUCTURE_CONFLICTS;
import static com.android.tools.idea.gradle.messages.CommonMessageGroupNames.VARIANT_SELECTION_CONFLICTS;

public final class ConflictDisplay {
  private ConflictDisplay() {
  }

  /**
   * Displays in the "Messages" window the given variant-related conflicts of the given project.
   *
   * @param project   the given project.
   * @param conflicts the conflicts to display.
   */
  public static void displayConflicts(@NotNull Project project, @NotNull ConflictSet conflicts) {
    ImmutableList<Conflict> selectionConflicts = conflicts.getSelectionConflicts();
    displaySelectionConflicts(project, selectionConflicts, VARIANT_SELECTION_CONFLICTS);
    BuildVariantView.getInstance(project).updateNotification(selectionConflicts);
    displaySelectionConflicts(project, conflicts.getStructureConflicts(), PROJECT_STRUCTURE_CONFLICTS);
  }

  private static void displaySelectionConflicts(@NotNull final Project project,
                                                @NotNull ImmutableList<Conflict> conflicts,
                                                @NotNull String groupName) {
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

      Navigatable navigateToConflictSource = new AbstractNavigatable() {
        @Override
        public void navigate(boolean requestFocus) {
          BuildVariantView.getInstance(project).selectAndScrollTo(conflictSource);
        }

        @Override
        public boolean canNavigate() {
          return true;
        }
      };

      messages.add(new Message(groupName, Message.Type.ERROR, navigateToConflictSource, text.toArray(new String[text.size()])));
    }
  }
}
