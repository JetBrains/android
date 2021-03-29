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

import static com.intellij.util.containers.ContainerUtil.getFirstItem;

import com.android.tools.idea.gradle.project.facet.ndk.NdkFacet;
import com.android.tools.idea.gradle.project.model.AndroidModuleModel;
import com.android.tools.idea.gradle.project.model.NdkModuleModel;
import com.android.tools.idea.gradle.project.model.VariantAbi;
import com.android.tools.idea.model.AndroidModel;
import com.intellij.openapi.util.text.StringUtil;
import java.util.Collection;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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
    if (facet == null || !AndroidModel.isRequired(facet)) {
      // project structure may have changed and the conflict is not longer applicable.
      return true;
    }
    AndroidModuleModel source = AndroidModuleModel.get(facet);
    if (source == null) {
      return false;
    }

    String newVariant = resolveNewVariant(conflict, showConflictResolutionDialog);
    if (StringUtil.isEmpty(newVariant)) {
      return false;
    }

    // If the module has an NDK model, then also update the variant in the Ndk model.
    NdkFacet ndkFacet = NdkFacet.getInstance(conflict.getSource());
    NdkModuleModel ndkModel = ndkFacet == null ? null : ndkFacet.getNdkModuleModel();
    if (ndkModel != null) {
      VariantAbi newVariantAbi = resolveNewVariantAbi(ndkFacet, ndkModel, newVariant);
      if (newVariantAbi == null) {
        return false;  // Cannot solve NDK variant induced conflict.
      }

      ndkFacet.setSelectedVariantAbi(newVariantAbi);
      ndkFacet.setNdkModuleModel(ndkModel);
    }

    source.setSelectedVariantName(newVariant);
    source.syncSelectedVariantAndTestArtifact(facet);
    return true;
  }

  /**
   * @return The name of the variant (without ABI) to use in order to resolve the provided conflict.
   */
  @Nullable
  private static String resolveNewVariant(@NotNull Conflict conflict, boolean showConflictResolutionDialog) {
    Collection<String> variants = conflict.getVariants();
    if (variants.size() == 1) {
      return getFirstItem(variants);
    }

    if (!showConflictResolutionDialog) {
      return null;
    }

    ConflictResolutionDialog dialog = new ConflictResolutionDialog(conflict);
    if (!dialog.showAndGet()) {
      return null;
    }

    return dialog.getSelectedVariant();
  }

  /**
   * @param ndkFacet   the NDK facet for the conflicting module
   * @param ndkModel   the NDK model for the conflicting module
   * @param newVariant the variant (without ABI) that will be used to resolve the current conflict
   * @return the name of the variant (with ABI) to use in order to resolve the current conflict
   */
  @Nullable
  private static VariantAbi resolveNewVariantAbi(@NotNull NdkFacet ndkFacet,
                                                 @NotNull NdkModuleModel ndkModel,
                                                 @NotNull String newVariant) {
    VariantAbi selectedVariantAbi = ndkFacet.getSelectedVariantAbi();
    if (selectedVariantAbi != null) {
      String userSelectedAbi = selectedVariantAbi.getAbi();
      VariantAbi newVariantAbi = new VariantAbi(newVariant, userSelectedAbi);

      if (ndkModel.getAllVariantAbis().contains(newVariantAbi)) {
        return newVariantAbi;
      }
    }

    // The given newVariant does not have the same ABI available. For instance, we are trying to fix a conflict by changing variant from
    // "release-x86" to "debug-x86", but the user has explicitly filtered out "debug-x86".
    // We fall back to any other available ABI under that variant, such as "debug-x86_64".
    return ndkModel.getAllVariantAbis().stream().filter(variantAbi -> variantAbi.getVariant().equals(newVariant)).findFirst().orElse(null);
  }
}
