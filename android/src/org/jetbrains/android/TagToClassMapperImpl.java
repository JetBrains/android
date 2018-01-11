/*
 * Copyright (C) 2016 The Android Open Source Project
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
package org.jetbrains.android;

import com.android.tools.idea.model.AndroidModuleInfo;
import com.android.tools.idea.psi.TagToClassMapper;
import com.google.common.collect.Maps;
import com.intellij.ProjectTopics;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.IndexNotReadyException;
import com.intellij.openapi.roots.ModuleRootEvent;
import com.intellij.openapi.roots.ModuleRootListener;
import com.intellij.openapi.util.Computable;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.SmartPointerManager;
import com.intellij.psi.SmartPsiElementPointer;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.searches.ClassInheritorsSearch;
import com.intellij.psi.util.CachedValue;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.PsiModificationTracker;
import com.intellij.util.messages.MessageBusConnection;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static com.intellij.util.ArrayUtilRt.find;
import static org.jetbrains.android.facet.LayoutViewClassUtils.getTagNamesByClass;

class TagToClassMapperImpl implements TagToClassMapper {
  private final Map<String, Map<String, SmartPsiElementPointer<PsiClass>>> myInitialClassMaps = new HashMap<>();
  private final Map<String, CachedValue<Map<String, PsiClass>>> myClassMaps = Maps.newConcurrentMap();

  private final Module myModule;

  TagToClassMapperImpl(@NotNull Module module) {
    myModule = module;
    MessageBusConnection connection = module.getMessageBus().connect(module);

    connection.subscribe(ProjectTopics.PROJECT_ROOTS, new ModuleRootListener() {
      @Override
      public void rootsChanged(ModuleRootEvent event) {
        // Clear the class inheritance map to make sure new dependencies from libraries are picked up
        clear();
      }
    });
  }

  /**
   * {@inheritDoc}
   *
   * In addition, a {@link CachedValue} for this mapping is created that is updated automatically with changes
   * to {@link PsiModificationTracker#JAVA_STRUCTURE_MODIFICATION_COUNT}.
   */
  // TODO: correctly support classes from external non-platform jars
  @Override
  @NotNull
  public Map<String, PsiClass> getClassMap(@NotNull String className) {
    CachedValue<Map<String, PsiClass>> value = myClassMaps.get(className);

    if (value == null) {
      value = CachedValuesManager.getManager(myModule.getProject()).createCachedValue(() -> {
        Map<String, PsiClass> map = computeClassMap(className);
        return CachedValueProvider.Result.create(map, PsiModificationTracker.JAVA_STRUCTURE_MODIFICATION_COUNT);
      }, false);
      myClassMaps.put(className, value);
    }

    return value.getValue();
  }

  @NotNull
  private Map<String, PsiClass> computeClassMap(@NotNull String className) {
    Map<String, SmartPsiElementPointer<PsiClass>> classMap = getInitialClassMap(className, false);
    Map<String, PsiClass> result = new HashMap<>();
    boolean shouldRebuildInitialMap = false;

    for (String key : classMap.keySet()) {
      SmartPsiElementPointer<PsiClass> pointer = classMap.get(key);

      if (!isUpToDate(pointer, key)) {
        shouldRebuildInitialMap = true;
        break;
      }
      PsiClass aClass = pointer.getElement();

      if (aClass != null) {
        result.put(key, aClass);
      }
    }

    if (shouldRebuildInitialMap) {
      result.clear();
      classMap = getInitialClassMap(className, true);

      for (String key : classMap.keySet()) {
        SmartPsiElementPointer<PsiClass> pointer = classMap.get(key);
        PsiClass aClass = pointer.getElement();

        if (aClass != null) {
          result.put(key, aClass);
        }
      }
    }
    fillMap(className, myModule.getModuleWithDependenciesAndLibrariesScope(true), result, false);
    return result;
  }

  private static boolean isUpToDate(@NotNull SmartPsiElementPointer<PsiClass> pointer, String tagName) {
    PsiClass aClass = pointer.getElement();
    if (aClass == null) {
      return false;
    }
    String[] tagNames = getTagNamesByClass(aClass, -1);
    return find(tagNames, tagName) >= 0;
  }

  @NotNull
  private Map<String, SmartPsiElementPointer<PsiClass>> getInitialClassMap(@NotNull String className, boolean forceRebuild) {
    Map<String, SmartPsiElementPointer<PsiClass>> viewClassMap;
    viewClassMap = myInitialClassMaps.get(className);
    if (viewClassMap != null && !forceRebuild) {
      return viewClassMap;
    }
    Map<String, PsiClass> map = new HashMap<>();

    if (fillMap(className, myModule.getModuleWithDependenciesAndLibrariesScope(true), map, true)) {
      viewClassMap = new HashMap<>(map.size());
      SmartPointerManager manager = SmartPointerManager.getInstance(myModule.getProject());

      for (Map.Entry<String, PsiClass> entry : map.entrySet()) {
        viewClassMap.put(entry.getKey(), manager.createSmartPsiElementPointer(entry.getValue()));
      }
      myInitialClassMaps.put(className, viewClassMap);
    }
    return viewClassMap != null ? viewClassMap : Collections.emptyMap();
  }

  private boolean fillMap(@NotNull String className,
                          @NotNull GlobalSearchScope scope,
                          @NotNull Map<String, PsiClass> map,
                          boolean libClassesOnly) {
    JavaPsiFacade facade = JavaPsiFacade.getInstance(myModule.getProject());
    PsiClass baseClass = ApplicationManager.getApplication().runReadAction((Computable<PsiClass>)() -> {
      PsiClass aClass;
      // facade.findClass uses index to find class by name, which might throw an IndexNotReadyException in dumb mode
      try {
        aClass = facade.findClass(className, myModule.getModuleWithDependenciesAndLibrariesScope(true));
      }
      catch (IndexNotReadyException e) {
        aClass = null;
      }
      return aClass;
    });
    if (baseClass == null) {
      return false;
    }

    AndroidModuleInfo androidModuleInfo = AndroidModuleInfo.getInstance(myModule);
    int api = androidModuleInfo == null ? 1 : androidModuleInfo.getModuleMinApi();

    String[] baseClassTagNames = getTagNamesByClass(baseClass, api);
    for (String tagName : baseClassTagNames) {
      map.put(tagName, baseClass);
    }
    try {
      ClassInheritorsSearch.search(baseClass, scope, true).forEach(c -> {
        if (libClassesOnly && c.getManager().isInProject(c)) {
          return true;
        }
        String[] tagNames = getTagNamesByClass(c, api);
        for (String tagName : tagNames) {
          map.put(tagName, c);
        }
        return true;
      });
    }
    catch (IndexNotReadyException e) {
      Logger.getInstance(getClass()).info(e);
      return false;
    }
    return !map.isEmpty();
  }

  public void clear() {
    myInitialClassMaps.clear();
  }
}
