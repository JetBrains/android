/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.gradle.project.sync.ng;

import com.android.builder.model.AndroidProject;
import com.android.builder.model.Variant;
import com.android.ide.common.gradle.model.level2.IdeDependenciesFactory;
import com.android.tools.idea.flags.StudioFlags;
import com.android.tools.idea.gradle.project.model.AndroidModuleModel;
import com.android.tools.idea.gradle.project.sync.GradleModuleModels;
import com.android.tools.idea.gradle.project.sync.common.VariantSelector;
import com.intellij.openapi.module.Module;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.List;

import static com.android.tools.idea.gradle.util.GradleProjects.findModuleRootFolderPath;
import static java.util.Collections.singletonList;

class AndroidModelFactory {
  @NotNull private final VariantSelector myVariantSelector;
  @NotNull private final IdeDependenciesFactory myDependenciesFactory;

  AndroidModelFactory(@NotNull VariantSelector variantSelector, @NotNull IdeDependenciesFactory dependenciesFactory) {
    myVariantSelector = variantSelector;
    myDependenciesFactory = dependenciesFactory;
  }

  @Nullable
  AndroidModuleModel createAndroidModel(@NotNull Module module,
                                        @NotNull AndroidProject androidProject,
                                        @NotNull GradleModuleModels moduleModels) {
    if (NewGradleSync.isSingleVariantSync(module.getProject())) {
      if (androidProject.getVariants().isEmpty()) {
        List<Variant> variants = moduleModels.findModels(Variant.class);
        if (variants != null) {
          AndroidModuleModel androidModel = createAndroidModel(module, androidProject, variants,
                                                               true /* Add variant to AndroidProject. */);
          if (androidModel != null) {
            return androidModel;
          }
        }
      }
    }
    Variant variantToSelect = myVariantSelector.findVariantToSelect(androidProject);
    if (variantToSelect != null) {
      AndroidModuleModel androidModel = createAndroidModel(module, androidProject, singletonList(variantToSelect),
                                                           false /* Do not add Variant to AndroidProject. */);
      if (androidModel != null) {
        return androidModel;
      }
    }
    // If an Android project does not have variants, it would be impossible to build. This is a possible but invalid use case.
    // For now we are going to treat this case as a Java library module, because everywhere in the IDE (e.g. run configurations,
    // editors, test support, variants tool window, project building, etc.) we have the assumption that there is at least one variant
    // per Android project, and changing that in the code base is too risky, for very little benefit.
    // See https://code.google.com/p/android/issues/detail?id=170722
    return null;
  }

  @Nullable
  private AndroidModuleModel createAndroidModel(@NotNull Module module,
                                                @NotNull AndroidProject androidProject,
                                                @NotNull List<Variant> variants,
                                                boolean addVariantToAndroidProject) {
    File moduleRootFolderPath = findModuleRootFolderPath(module);
    if (moduleRootFolderPath != null) {
      String selectedVariant = variants.get(variants.size() - 1).getName();
      // With single-variant sync, the variants are not part of AndroidProject. We need to manually add it.
      List<Variant> variantsToAdd = addVariantToAndroidProject ? variants : null;
      return new AndroidModuleModel(module.getName(), moduleRootFolderPath, androidProject, selectedVariant, myDependenciesFactory,
                                    variantsToAdd);
    }
    return null;
  }
}
