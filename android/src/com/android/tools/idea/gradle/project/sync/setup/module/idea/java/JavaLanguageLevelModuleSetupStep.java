/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.gradle.project.sync.setup.module.idea.java;

import com.android.tools.idea.gradle.project.model.AndroidModuleModel;
import com.android.tools.idea.gradle.project.model.JavaModuleModel;
import com.android.tools.idea.gradle.project.sync.ModuleSetupContext;
import com.android.tools.idea.gradle.project.sync.setup.module.idea.JavaModuleSetupStep;
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.LanguageLevelModuleExtensionImpl;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.pom.java.LanguageLevel;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

import static com.android.tools.idea.gradle.project.sync.setup.Facets.findFacet;
import static com.intellij.pom.java.LanguageLevel.JDK_1_6;

public class JavaLanguageLevelModuleSetupStep extends JavaModuleSetupStep {
  @Override
  protected void doSetUpModule(@NotNull ModuleSetupContext context, @NotNull JavaModuleModel javaModuleModel) {
    LanguageLevel languageLevel = javaModuleModel.getJavaLanguageLevel();

    if (languageLevel == null) {
      // Java language is still not correct. Most likely this module does not have dependents.
      // Get minimum language level from all Android modules.
      languageLevel = getMinimumLanguageLevelForAndroidModules(context.getIdeModelsProvider());
    }

    if (languageLevel == null) {
      languageLevel = JDK_1_6; // The minimum safe Java language level.
    }

    ModifiableRootModel rootModel = context.getModifiableRootModel();
    LanguageLevelModuleExtensionImpl moduleExtension = rootModel.getModuleExtension(LanguageLevelModuleExtensionImpl.class);
    moduleExtension.setLanguageLevel(languageLevel);
  }

  @Nullable
  private static LanguageLevel getMinimumLanguageLevelForAndroidModules(@NotNull IdeModifiableModelsProvider modelsProvider) {
    Module[] modules = modelsProvider.getModules();
    if (modules.length == 0) {
      return null;
    }

    LanguageLevel result = null;

    List<LanguageLevel> languageLevels = new ArrayList<>();
    for (Module dependency : modules) {
      LanguageLevel dependencyLanguageLevel = getLanguageLevelForAndroidModule(dependency, modelsProvider);
      if (dependencyLanguageLevel != null) {
        languageLevels.add(dependencyLanguageLevel);
      }
    }

    for (LanguageLevel dependencyLanguageLevel : languageLevels) {
      if (result == null || result.compareTo(dependencyLanguageLevel) > 0) {
        result = dependencyLanguageLevel;
      }
    }

    return result;
  }

  @Nullable
  private static LanguageLevel getLanguageLevelForAndroidModule(@NotNull Module module,
                                                                @NotNull IdeModifiableModelsProvider modelsProvider) {
    AndroidFacet facet = findFacet(module, modelsProvider, AndroidFacet.ID);
    if (facet != null) {
      AndroidModuleModel androidModel = AndroidModuleModel.get(facet);
      if (androidModel != null) {
        return androidModel.getJavaLanguageLevel();
      }
    }
    return null;
  }
}
