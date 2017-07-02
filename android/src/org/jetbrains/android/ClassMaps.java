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
import com.google.common.collect.Maps;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.IndexNotReadyException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Key;
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
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.facet.AndroidFacetScopedService;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static com.intellij.util.ArrayUtilRt.find;
import static org.jetbrains.android.facet.LayoutViewClassUtils.getTagNamesByClass;

public class ClassMaps extends AndroidFacetScopedService {
  private static final Key<ClassMaps> KEY = Key.create(ClassMaps.class.getName());

  private final Map<String, Map<String, SmartPsiElementPointer<PsiClass>>> myInitialClassMaps = new HashMap<>();

  private final Map<String, CachedValue<Map<String, PsiClass>>> myClassMaps = Maps.newConcurrentMap();

  @NotNull
  public static ClassMaps getInstance(@NotNull AndroidFacet facet) {
    ClassMaps classMaps = facet.getUserData(KEY);
    if (classMaps == null) {
      classMaps = new ClassMaps(facet);
      facet.putUserData(KEY, classMaps);
    }
    return classMaps;
  }

  private ClassMaps(@NotNull AndroidFacet facet) {
    super(facet);
  }

  /**
   * Returns all the classes inheriting from {@code className} that can be accessed from the current module.
   */
  // TODO: correctly support classes from external non-platform jars
  @NotNull
  public Map<String, PsiClass> getClassMap(@NotNull String className) {
    CachedValue<Map<String, PsiClass>> value = myClassMaps.get(className);

    if (value == null) {
      value = CachedValuesManager.getManager(getProject()).createCachedValue(() -> {
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
    fillMap(className, getModule().getModuleWithDependenciesAndLibrariesScope(true), result, false);
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

    if (fillMap(className, getModule().getModuleWithDependenciesAndLibrariesScope(true), map, true)) {
      viewClassMap = new HashMap<>(map.size());
      SmartPointerManager manager = SmartPointerManager.getInstance(getProject());

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
    JavaPsiFacade facade = JavaPsiFacade.getInstance(getProject());
    PsiClass baseClass = ApplicationManager.getApplication().runReadAction((Computable<PsiClass>)() -> {
      PsiClass aClass;
      // facade.findClass uses index to find class by name, which might throw an IndexNotReadyException in dumb mode
      try {
        aClass = facade.findClass(className, getModule().getModuleWithDependenciesAndLibrariesScope(true));
      }
      catch (IndexNotReadyException e) {
        aClass = null;
      }
      return aClass;
    });
    AndroidModuleInfo androidModuleInfo = AndroidModuleInfo.getInstance(getFacet());
    if (baseClass != null) {
      String[] baseClassTagNames = getTagNamesByClass(baseClass, androidModuleInfo.getModuleMinApi());
      for (String tagName : baseClassTagNames) {
        map.put(tagName, baseClass);
      }
      try {
        ClassInheritorsSearch.search(baseClass, scope, true).forEach(c -> {
          if (libClassesOnly && c.getManager().isInProject(c)) {
            return true;
          }
          String[] tagNames = getTagNamesByClass(c, androidModuleInfo.getModuleMinApi());
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
    }
    return !map.isEmpty();
  }

  @NotNull
  private Project getProject() {
    return getModule().getProject();
  }

  public void clear() {
    myInitialClassMaps.clear();
  }

  @Override
  protected void onServiceDisposal(@NotNull AndroidFacet facet) {
    facet.putUserData(KEY, null);
  }
}
