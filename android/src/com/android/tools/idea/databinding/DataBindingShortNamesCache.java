/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.tools.idea.databinding;

import com.android.tools.idea.rendering.DataBindingInfo;
import com.android.tools.idea.rendering.LocalResourceRepository;
import com.android.tools.idea.rendering.ProjectResourceRepository;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.ModificationTracker;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.PsiShortNamesCache;
import com.intellij.psi.util.CachedValue;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.util.ArrayUtil;
import com.intellij.util.Function;
import com.intellij.util.Processor;
import com.intellij.util.containers.HashSet;
import com.intellij.util.indexing.IdFilter;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Array;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * PsiShortNames cache that finds classes generated for layout files.
 */
public class DataBindingShortNamesCache extends PsiShortNamesCache {
  private AndroidFacet myFacet;
  private CachedValue<Map<String, List<DataBindingInfo>>> myNameCache;
  private CachedValue<String[]> myAllClassNamesCache;
  private CachedValue<Map<String, List<PsiMethod>>> myMethodsByNameCache;
  private CachedValue<Map<String, List<PsiField>>> myFieldsByNameCache;
  private CachedValue<String[]> myAllMethodNamesCache;
  private CachedValue<String[]> myAllFieldNamesCache;


  public DataBindingShortNamesCache(final AndroidFacet facet) {
    myFacet = facet;
    NameCacheProvider provider = new NameCacheProvider(myFacet);
    Project project = facet.getModule().getProject();
    myNameCache = CachedValuesManager.getManager(project).createCachedValue(provider, false);
    myAllClassNamesCache = CachedValuesManager.getManager(project).createCachedValue(
        new ResourceCacheValueProvider<String[]>(myFacet) {
          @Override
          String[] doCompute() {
            Set<String> keySet = myNameCache.getValue().keySet();
            return keySet.toArray(new String[keySet.size()]);
          }

          @Override
          String[] defaultValue() {
            return ArrayUtil.EMPTY_STRING_ARRAY;
          }
        }, false);

    myMethodsByNameCache = CachedValuesManager.getManager(project).createCachedValue(
        new ResourceCacheValueProvider<Map<String, List<PsiMethod>>>(myFacet) {
          @Override
          Map<String, List<PsiMethod>> doCompute() {
            final Map<String, List<PsiMethod>> result = Maps.newHashMap();
            traverseAllClasses(new Function<PsiClass, Void>() {
              @Override
              public Void fun(PsiClass psiClass) {
                for (PsiMethod method : psiClass.getMethods()) {
                  List<PsiMethod> psiMethods = result.get(method.getName());
                  if (psiMethods == null) {
                    psiMethods = Lists.newArrayList();
                    result.put(method.getName(), psiMethods);
                  }
                  psiMethods.add(method);
                }
                return null;
              }
            });
            return result;
          }

          @Override
          Map<String, List<PsiMethod>> defaultValue() {
            return Maps.newHashMap();
          }
        }, false);

    myFieldsByNameCache = CachedValuesManager.getManager(project).createCachedValue(
        new ResourceCacheValueProvider<Map<String, List<PsiField>>>(myFacet) {
          @Override
          Map<String, List<PsiField>> doCompute() {
            final Map<String, List<PsiField>> result = Maps.newHashMap();
            traverseAllClasses(new Function<PsiClass, Void>() {
              @Override
              public Void fun(PsiClass psiClass) {
                for (PsiField field : psiClass.getFields()) {
                  List<PsiField> psiFields = result.get(field.getName());
                  if (psiFields == null) {
                    psiFields = Lists.newArrayList();
                    result.put(field.getName(), psiFields);
                  }
                  psiFields.add(field);
                }
                return null;
              }
            });
            return result;
          }

          @Override
          Map<String, List<PsiField>> defaultValue() {
            return Maps.newHashMap();
          }
        }, false);

    myAllMethodNamesCache = CachedValuesManager.getManager(project).createCachedValue(
        new ResourceCacheValueProvider<String[]>(myFacet) {
          @Override
          String[] doCompute() {
            Set<String> names = myMethodsByNameCache.getValue().keySet();
            return names.toArray(new String[names.size()]);
          }

          @Override
          String[] defaultValue() {
            return ArrayUtil.EMPTY_STRING_ARRAY;
          }
        }, false);

    myAllFieldNamesCache = CachedValuesManager.getManager(project).createCachedValue(
        new ResourceCacheValueProvider<String[]>(myFacet) {
          @Override
          String[] doCompute() {
            Set<String> names = myFieldsByNameCache.getValue().keySet();
            return names.toArray(new String[names.size()]);
          }

          @Override
          String[] defaultValue() {
            return ArrayUtil.EMPTY_STRING_ARRAY;
          }
        }, false);
  }

  private void traverseAllClasses(Function<PsiClass, Void> receiver) {
    for (List<DataBindingInfo> infoList : myNameCache.getValue().values()) {
      for (DataBindingInfo info : infoList) {
        PsiClass psiClass = DataBindingUtil.getOrCreatePsiClass(myFacet, info);
        receiver.fun(psiClass);
      }
    }
  }

  @NotNull
  @Override
  public PsiClass[] getClassesByName(@NotNull @NonNls String name, @NotNull GlobalSearchScope scope) {
    List<DataBindingInfo> infoList = myNameCache.getValue().get(name);
    if (infoList == null || infoList.size() == 0) {
      return PsiClass.EMPTY_ARRAY;
    }
    List<PsiClass> selected = Lists.newArrayList();
    for (DataBindingInfo info : infoList) {
      if (scope.accept(info.getPsiFile().getVirtualFile())) {
        selected.add(DataBindingUtil.getOrCreatePsiClass(myFacet, info));
      }
    }
    if (selected.isEmpty()) {
      return PsiClass.EMPTY_ARRAY;
    }
    return selected.toArray(new PsiClass[selected.size()]);
  }

  @NotNull
  @Override
  public String[] getAllClassNames() {
    return myAllClassNamesCache.getValue();
  }

  @Override
  public void getAllClassNames(@NotNull HashSet<String> dest) {
    Collections.addAll(dest, getAllClassNames());
  }

  @NotNull
  @Override
  public PsiMethod[] getMethodsByName(@NonNls @NotNull String name, @NotNull GlobalSearchScope scope) {
    List<PsiMethod> methods = myMethodsByNameCache.getValue().get(name);
    return filterByScope(methods, scope, PsiMethod.class, PsiMethod.EMPTY_ARRAY);
  }

  @NotNull
  @Override
  public PsiMethod[] getMethodsByNameIfNotMoreThan(@NonNls @NotNull String name, @NotNull GlobalSearchScope scope, int maxCount) {
    PsiMethod[] methods = getMethodsByName(name, scope);
    if (methods.length > maxCount) {
      return PsiMethod.EMPTY_ARRAY;
    }
    return methods;
  }

  @NotNull
  @Override
  public PsiField[] getFieldsByNameIfNotMoreThan(@NonNls @NotNull String name, @NotNull GlobalSearchScope scope, int maxCount) {
    List<PsiField> psiFields = myFieldsByNameCache.getValue().get(name);
    PsiField[] selected = filterByScope(psiFields, scope, PsiField.class, PsiField.EMPTY_ARRAY);
    if (selected.length > maxCount) {
      return PsiField.EMPTY_ARRAY;
    }
    return selected;
  }

  private <T extends PsiElement> T[] filterByScope(List<T> items, @NotNull GlobalSearchScope scope, Class<T> klass, T[] defaultValue) {
    if (items == null || items.isEmpty()) {
      return defaultValue;
    }
    List<T> selected = Lists.newArrayList();
    for (T item : items) {
      if (item.getContainingFile() != null && scope.accept(item.getContainingFile().getVirtualFile())) {
        selected.add(item);
      }
    }
    return selected.toArray((T[])Array.newInstance(klass, selected.size()));
  }

  @Override
  public boolean processMethodsWithName(@NonNls @NotNull String name,
                                        @NotNull GlobalSearchScope scope,
                                        @NotNull Processor<PsiMethod> processor) {
    for (PsiMethod method : getMethodsByName(name, scope)) {
      if (!processor.process(method)) {
        return false;
      }
    }
    return true;
  }

  @NotNull
  @Override
  public String[] getAllMethodNames() {
    return myAllMethodNamesCache.getValue();
  }

  @Override
  public void getAllMethodNames(@NotNull HashSet<String> set) {
    for (String method : getAllClassNames()) {
      set.add(method);
    }
  }

  @NotNull
  @Override
  public PsiField[] getFieldsByName(@NotNull @NonNls String name, @NotNull GlobalSearchScope scope) {
    List<PsiField> psiFields = myFieldsByNameCache.getValue().get(name);
    return filterByScope(psiFields, scope, PsiField.class, PsiField.EMPTY_ARRAY);
  }

  @NotNull
  @Override
  public String[] getAllFieldNames() {
    return myAllFieldNamesCache.getValue();
  }

  @Override
  public void getAllFieldNames(@NotNull HashSet<String> set) {
    for (String field : getAllFieldNames()) {
      set.add(field);
    }
  }

  private class NameCacheProvider extends ResourceCacheValueProvider<Map<String, List<DataBindingInfo>>> {
    public NameCacheProvider(AndroidFacet facet) {
      super(facet);
    }

    @Override
    Map<String, List<DataBindingInfo>> doCompute() {
      ProjectResourceRepository projectResources = ProjectResourceRepository.getProjectResources(myFacet, true);
      Map<String, DataBindingInfo> dataBindingResourceFiles = projectResources.getDataBindingResourceFiles();
      if (dataBindingResourceFiles == null) {
        return Maps.newHashMap();
      }
      Map<String, List<DataBindingInfo>> cache = Maps.newHashMap();
      for (DataBindingInfo info : dataBindingResourceFiles.values()) {
        List<DataBindingInfo> infoList = cache.get(info.getClassName());
        if (infoList == null) {
          infoList = Lists.newArrayList();
          cache.put(info.getClassName(), infoList);
        }
        infoList.add(info);
      }
      return cache;
    }

    @Override
    Map<String, List<DataBindingInfo>> defaultValue() {
      return Maps.newHashMap();
    }
  }
}
