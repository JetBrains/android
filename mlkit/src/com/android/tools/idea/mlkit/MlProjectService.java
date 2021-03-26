/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.mlkit;

import com.android.tools.idea.mlkit.lightpsi.LightModelClass;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import com.intellij.psi.util.CachedValue;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jetbrains.annotations.NotNull;

@Service
public final class MlProjectService {

  public static MlProjectService getInstance(@NotNull Project project) {
    return project.getService(MlProjectService.class);
  }

  private final Project myProject;
  private final CachedValue<Map<String, List<PsiClass>>> lightClassMap;

  public MlProjectService(@NotNull Project project) {
    myProject = project;

    lightClassMap = CachedValuesManager.getManager(project).createCachedValue(() -> {
      Map<String, List<PsiClass>> lightClassMap = new HashMap<>();
      for (Module module : ModuleManager.getInstance(myProject).getModules()) {
        if (MlUtils.isMlModelBindingBuildFeatureEnabled(module)) {
          for (LightModelClass lightModelClass : MlModuleService.getInstance(module).getLightModelClassList()) {
            lightClassMap.computeIfAbsent(lightModelClass.getName(), key -> new ArrayList<>()).add(lightModelClass);

            for (PsiClass innerClass : lightModelClass.getInnerClasses()) {
              lightClassMap.computeIfAbsent(innerClass.getName(), key -> new ArrayList<>()).add(innerClass);
            }
          }
        }
      }
      return CachedValueProvider.Result.create(
        lightClassMap, ProjectMlModelFileTracker.getInstance(myProject), ModuleManager.getInstance(project));
    });
  }

  @NotNull
  public List<PsiClass> getLightClassListByClassName(@NotNull String className) {
    return lightClassMap.getValue().getOrDefault(className, Collections.emptyList());
  }

  @NotNull
  public Set<String> getAllClassNames() {
    return lightClassMap.getValue().keySet();
  }
}
