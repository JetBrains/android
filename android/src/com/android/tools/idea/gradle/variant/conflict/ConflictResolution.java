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

import com.android.tools.idea.gradle.project.model.AndroidModuleModel;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

import static com.intellij.openapi.util.text.StringUtil.isNotEmpty;
import static com.intellij.util.containers.ContainerUtil.getFirstItem;

public final class ConflictResolution {
  private ConflictResolution() {
  }

  /**
   * Attempts to solve the given "variant selection" conflict.
   * <p>
   * The conflict is solved by selecting the expected variant in the module that is the source of such conflict. For example, Module1
   * depends on Module2-VariantX, but Module2 has currently VariantY selected. To solve this conflict, this method selects VariantX on
   * Module2.
   * </p>
   * <p>
   * There are cases that a conflict cannot be solved without creating new conflicts. For example, Module1 depends on Module3-VariantX while
   * Module2 depends on Module3-VariantY, and Module2 has currently VariantZ selected. Selecting VariantX on Module3 will satisfy Module1's
   * dependency, but there will still be a conflict with Module2. In this case, this method shows a dialog explaining the dependencies
   * involved and allows the user to select the variant that would partially solve the conflict.
   * </p>
   *
   * @param conflict the given conflict.
   * @return {@code true} if the conflict was successfully solved; {@code false} otherwise.
   */
  public static boolean solveSelectionConflict(@NotNull Conflict conflict) {
    return solveSelectionConflict(conflict, true);
  }

  private static boolean solveSelectionConflict(@NotNull Conflict conflict, boolean showConflictResolutionDialog) {
    AndroidFacet facet = AndroidFacet.getInstance(conflict.getSource());
    if (facet == null || !facet.requiresAndroidModel()) {
      // project structure may have changed and the conflict is not longer applicable.
      return true;
    }
    AndroidModuleModel source = AndroidModuleModel.get(facet);
    if (source == null) {
      return false;
    }
    Collection<String> variants = conflict.getVariants();
    if (variants.size() == 1) {
      String expectedVariant = getFirstItem(variants);
      if (isNotEmpty(expectedVariant)) {
        source.setSelectedVariantName(expectedVariant);
        source.syncSelectedVariantAndTestArtifact(facet);
        return true;
      }
    }
    else if (showConflictResolutionDialog) {
      ConflictResolutionDialog dialog = new ConflictResolutionDialog(conflict);
      if (dialog.showAndGet()) {
        String selectedVariant = dialog.getSelectedVariant();
        if (isNotEmpty(selectedVariant)) {
          source.setSelectedVariantName(selectedVariant);
          source.syncSelectedVariantAndTestArtifact(facet);
          return true;
        }
      }
    }
    return false;
  }
}
