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

import com.android.SdkConstants;
import com.google.common.collect.Maps;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.ModificationTracker;
import com.intellij.psi.*;
import com.intellij.psi.impl.file.PsiPackageImpl;
import com.intellij.psi.search.ProjectScope;
import com.intellij.psi.search.searches.AnnotatedElementsSearch;
import com.intellij.psi.util.*;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;

/**
 * Keeps data binding information related to a project
 */
public class DataBindingProjectComponent implements ModificationTracker {
  final CachedValue<AndroidFacet[]> myDataBindingEnabledModules;
  final ParameterizedCachedValue<Collection<? extends PsiModifierListOwner>, Module> myBindingAdapterAnnotations;
  final Project myProject;
  private AtomicLong myModificationCount = new AtomicLong(0);
  private Map<String, PsiPackage> myDataBindingPsiPackages = Maps.newConcurrentMap();

  public DataBindingProjectComponent(final Project project) {
    myProject = project;
    myDataBindingEnabledModules = CachedValuesManager.getManager(project).createCachedValue(() -> {
      Module[] modules = ModuleManager.getInstance(myProject).getModules();
      List<AndroidFacet> facets = new ArrayList<>();
      for (Module module : modules) {
        AndroidFacet facet = AndroidFacet.getInstance(module);
        if (facet == null) {
          continue;
        }
        if (ModuleDataBinding.isEnabled(facet)) {
          facets.add(facet);
        }
      }

      myModificationCount.incrementAndGet();
      return CachedValueProvider.Result.create(facets.toArray(new AndroidFacet[facets.size()]),
                                               DataBindingUtil.DATA_BINDING_ENABLED_TRACKER, ModuleManager.getInstance(project));
    }, false);

    myBindingAdapterAnnotations = CachedValuesManager.getManager(project).createParameterizedCachedValue(module -> {
      JavaPsiFacade facade = JavaPsiFacade.getInstance(myProject);
      PsiClass aClass = facade
        .findClass(SdkConstants.BINDING_ADAPTER_ANNOTATION, module.getModuleWithDependenciesAndLibrariesScope(false));

      Collection<? extends PsiModifierListOwner> psiElements = null;
      if (aClass == null) {
        psiElements = Collections.emptyList();
      }
      else {
        // ProjectScope used. ModuleWithDepencies does not seem to work
        psiElements = AnnotatedElementsSearch.searchElements(aClass, ProjectScope.getAllScope(myProject), PsiMethod.class).findAll();
      }

      // Cached value that will be refreshed in every Java change
      return CachedValueProvider.Result
        .create(psiElements, PsiModificationTracker.JAVA_STRUCTURE_MODIFICATION_COUNT, ModuleManager.getInstance(project));
    }, false);
  }

  @NotNull
  public Project getProject() {
    return myProject;
  }

  public boolean hasAnyDataBindingEnabledFacet() {
    return getDataBindingEnabledFacets().length > 0;
  }

  public AndroidFacet[] getDataBindingEnabledFacets() {
    return myDataBindingEnabledModules.getValue();
  }

  @Override
  public long getModificationCount() {
    return myModificationCount.longValue();
  }

  /**
   * Returns a {@linkplain PsiPackage} instance for the given package name.
   * <p>
   * If it does not exist in the cache, a new one is created.
   *
   * @param packageName The qualified package name
   * @return A {@linkplain PsiPackage} that represents the given qualified name
   */
  public synchronized PsiPackage getOrCreateDataBindingPsiPackage(String packageName) {
    PsiPackage pkg = myDataBindingPsiPackages.get(packageName);
    if (pkg == null) {
      pkg = new PsiPackageImpl(PsiManager.getInstance(myProject), packageName) {
        @Override
        public boolean isValid() {
          return true;
        }
      };
      myDataBindingPsiPackages.put(packageName, pkg);
    }
    return pkg;
  }

  /**
   * Convert the passed annotation initialization into a {@link Stream} of {@link PsiLiteral} values
   */
  @NotNull
  private static Stream<PsiLiteral> getPsiLiterals(@NotNull PsiAnnotationMemberValue annotationMemberValue) {
    if (annotationMemberValue instanceof PsiArrayInitializerMemberValue) {
      return Arrays.stream(((PsiArrayInitializerMemberValue)annotationMemberValue).getInitializers())
        .filter(PsiLiteral.class::isInstance)
        .map(PsiLiteral.class::cast);
    }
    if (annotationMemberValue instanceof PsiLiteral) {
      return Stream.of((PsiLiteral)annotationMemberValue);
    }

    return Stream.empty();
  }

  /**
   * Returns the stream of attributes defined by {@code @BindingAdapter} annotations
   */
  @NotNull
  public Stream<String> getBindingAdapterAttributes(@NotNull Module module) {
    return myBindingAdapterAnnotations.getValue(module).stream().map(PsiModifierListOwner::getModifierList)
      .filter(Objects::nonNull)
      .flatMap(modifierList -> Stream.of(modifierList.getAnnotations()))
      .map(annotation -> annotation.findAttributeValue("value"))
      .filter(Objects::nonNull)
      .flatMap(DataBindingProjectComponent::getPsiLiterals)
      .map(PsiLiteral::getValue)
      .filter(Objects::nonNull)
      .map(Object::toString);
  }
}
