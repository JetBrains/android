/*
 * Copyright (C) 2019 The Android Open Source Project
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

import static com.intellij.psi.search.GlobalSearchScope.notScope;
import static org.jetbrains.android.facet.AndroidClassesForXmlUtilKt.getTagNamesByClass;

import com.android.tools.idea.AndroidPsiUtils;
import com.android.tools.idea.model.AndroidModuleInfo;
import com.android.tools.idea.model.StudioAndroidModuleInfo;
import com.android.tools.idea.projectsystem.ProjectSystemUtil;
import com.android.tools.idea.projectsystem.ScopeType;
import com.android.tools.idea.psi.TagToClassMapper;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Maps;
import com.intellij.ProjectTopics;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.IndexNotReadyException;
import com.intellij.openapi.roots.ModuleRootEvent;
import com.intellij.openapi.roots.ModuleRootListener;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.SmartPointerManager;
import com.intellij.psi.SmartPsiElementPointer;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.ProjectScope;
import com.intellij.psi.search.searches.ClassInheritorsSearch;
import com.intellij.psi.util.CachedValue;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.util.ArrayUtil;
import com.intellij.util.messages.MessageBusConnection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

class TagToClassMapperImpl implements TagToClassMapper {
  private static final Logger LOG = Logger.getInstance(TagToClassMapper.class);

  private final Map<String, Map<String, SmartPsiElementPointer<PsiClass>>> myInitialClassMaps = new HashMap<>();
  private final Map<String, CachedValue<Map<String, PsiClass>>> myClassMaps = Maps.newConcurrentMap();

  private final Module myModule;

  TagToClassMapperImpl(@NotNull Module module) {
    myModule = module;
    MessageBusConnection connection = module.getProject().getMessageBus().connect(module);

    connection.subscribe(ProjectTopics.PROJECT_ROOTS, new ModuleRootListener() {
      @Override
      public void rootsChanged(@NotNull ModuleRootEvent event) {
        // Clear the class inheritance map to make sure new dependencies from libraries are picked up
        clearInitialClassMaps();
      }
    });
  }

  @NotNull
  @Override
  public ClassMapFreshness getClassMapFreshness(String className) {
    CachedValue<Map<String, PsiClass>> value = myClassMaps.get(className);
    if (value == null) {
      return ClassMapFreshness.REBUILD_ENTIRE_CLASS_MAP;
    }
    if (value.hasUpToDateValue()) {
      return ClassMapFreshness.VALID_CLASS_MAP;
    }
    Map<String, SmartPsiElementPointer<PsiClass>> classMap = myInitialClassMaps.get(className);
    if (classMap != null && isClassMapUpToDate(classMap)) {
      return ClassMapFreshness.REBUILD_PARTIAL_CLASS_MAP;
    }
    return ClassMapFreshness.REBUILD_ENTIRE_CLASS_MAP;
  }

  @Override
  @TestOnly
  public void resetAllClassMaps() {
    clearInitialClassMaps();
    myClassMaps.clear();
  }

  @Override
  @NotNull
  public Map<String, PsiClass> getClassMap(String classMapKey) {
    if (DumbService.isDumb(myModule.getProject())) {
      return Collections.emptyMap();
    }
    CachedValue<Map<String, PsiClass>> value = myClassMaps.get(classMapKey);

    if (value == null) {
      value = CachedValuesManager.getManager(myModule.getProject()).createCachedValue(() -> {
        Map<String, PsiClass> map = computeClassMap(classMapKey);
        return CachedValueProvider.Result.create(map, AndroidPsiUtils.getPsiModificationTrackerIgnoringXml(myModule.getProject()));
      }, false);
      myClassMaps.put(classMapKey, value);
    }

    return Collections.unmodifiableMap(value.getValue());
  }

  @NotNull
  private Map<String, PsiClass> computeClassMap(@NotNull String classMapKey) {
    Map<String, SmartPsiElementPointer<PsiClass>> classMap = getInitialClassMap(classMapKey, false);
    Map<String, PsiClass> result = new HashMap<>();
    boolean shouldRebuildInitialMap = false;
    int apiLevel = getMinApiLevel();

    for (String key : classMap.keySet()) {
      SmartPsiElementPointer<PsiClass> pointer = classMap.get(key);
      PsiClass aClass = pointer.getElement();

      if (aClass != null) {
        if (!isUpToDate(aClass, key, apiLevel, classMapKey)) {
          shouldRebuildInitialMap = true;
          break;
        }
        result.put(key, aClass);
      }
    }

    if (shouldRebuildInitialMap) {
      result.clear();
      classMap = getInitialClassMap(classMapKey, true);

      for (String key : classMap.keySet()) {
        SmartPsiElementPointer<PsiClass> pointer = classMap.get(key);
        PsiClass aClass = pointer.getElement();

        if (aClass != null) {
          result.put(key, aClass);
        }
      }
    }
    fillMap(classMapKey, projectClassesScope(), result);
    return result;
  }

  private static boolean isUpToDate(@NotNull PsiClass aClass,
                                    @NotNull String tagName,
                                    int apiLevel,
                                    @Nullable String classMapKey) {
    return ArrayUtil.contains(tagName, getTagNamesByClass(aClass, apiLevel, classMapKey));
  }

  private boolean isClassMapUpToDate(@NotNull Map<String, SmartPsiElementPointer<PsiClass>> classMap) {
    int apiLevel = getMinApiLevel();
    for (String key : classMap.keySet()) {
      SmartPsiElementPointer<PsiClass> pointer = classMap.get(key);
      PsiClass aClass = pointer.getElement();
      if (aClass != null) {
        if (!isUpToDate(aClass, key, apiLevel, key)) {
          return false;
        }
      }
    }
    return true;
  }

  @NotNull
  private Map<String, SmartPsiElementPointer<PsiClass>> getInitialClassMap(@NotNull String classMapKey, boolean forceRebuild) {
    Map<String, SmartPsiElementPointer<PsiClass>> viewClassMap = myInitialClassMaps.get(classMapKey);
    if (viewClassMap != null && !forceRebuild) {
      return viewClassMap;
    }
    return computeInitialClassMap(classMapKey);
  }

  @VisibleForTesting
  @NotNull
  Map<String, SmartPsiElementPointer<PsiClass>> computeInitialClassMap(@NotNull String classMapKey) {
    LOG.info("Building initial class map for " + classMapKey);

    Map<String, SmartPsiElementPointer<PsiClass>> viewClassMap = null;
    Map<String, PsiClass> map = new HashMap<>();

    if (fillMap(classMapKey, dependenciesClassesScope(), map)) {
      viewClassMap = new HashMap<>(map.size());
      SmartPointerManager manager = SmartPointerManager.getInstance(myModule.getProject());

      for (Map.Entry<String, PsiClass> entry : map.entrySet()) {
        viewClassMap.put(entry.getKey(), manager.createSmartPsiElementPointer(entry.getValue()));
      }
      myInitialClassMaps.put(classMapKey, viewClassMap);
    }
    return viewClassMap != null ? viewClassMap : Collections.emptyMap();
  }

  @NotNull
  private GlobalSearchScope moduleResolveScope() {
    return ProjectSystemUtil.getModuleSystem(myModule).getResolveScope(ScopeType.MAIN);
  }

  @NotNull
  private GlobalSearchScope allLibrariesScope() {
    return ProjectScope.getLibrariesScope(myModule.getProject());
  }

  @NotNull
  private GlobalSearchScope projectClassesScope() {
    return moduleResolveScope().intersectWith(notScope(allLibrariesScope()));
  }

  @NotNull
  private GlobalSearchScope dependenciesClassesScope() {
    return moduleResolveScope().intersectWith(allLibrariesScope());
  }

  private boolean fillMap(@NotNull String className,
                          @NotNull GlobalSearchScope scope,
                          @NotNull Map<String, PsiClass> map) {
    JavaPsiFacade facade = JavaPsiFacade.getInstance(myModule.getProject());
    PsiClass baseClass = facade.findClass(className, moduleResolveScope());

    if (baseClass == null) {
      return false;
    }

    int api = getMinApiLevel();

    String[] baseClassTagNames = getTagNamesByClass(baseClass, api, className);
    for (String tagName : baseClassTagNames) {
      map.put(tagName, baseClass);
    }
    try {
      ClassInheritorsSearch.search(baseClass, scope, true).forEach(c -> {
        String[] tagNames = getTagNamesByClass(c, api, className);
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

  private int getMinApiLevel() {
    AndroidModuleInfo androidModuleInfo = StudioAndroidModuleInfo.getInstance(myModule);
    return androidModuleInfo == null ? 1 : androidModuleInfo.getModuleMinApi();
  }

  public void clearInitialClassMaps() {
    myInitialClassMaps.clear();
  }
}
