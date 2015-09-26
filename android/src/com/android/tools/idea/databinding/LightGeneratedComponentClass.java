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
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.impl.light.LightIdentifier;
import com.intellij.psi.impl.light.LightMethod;
import com.intellij.psi.impl.light.LightModifierList;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.searches.AnnotatedElementsSearch;
import com.intellij.psi.util.CachedValue;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.PsiUtil;
import org.jetbrains.android.augment.AndroidLightClassBase;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * Virtual class for DataBinding that represents the generated BindingComponent class.
 */
public class LightGeneratedComponentClass extends AndroidLightClassBase {
  private final AndroidFacet myFacet;
  private CachedValue<PsiMethod[]> myMethodCache;
  private PsiFile myContainingFile;
  private final PsiModifierList myPsiModifierList;

  public LightGeneratedComponentClass(@NotNull PsiManager psiManager, final AndroidFacet facet) {
    super(psiManager);
    myFacet = facet;
    myPsiModifierList = new LightModifierList(myManager, getLanguage(), PsiModifier.PUBLIC);
    myMethodCache =
      CachedValuesManager.getManager(facet.getModule().getProject()).createCachedValue(
        new CachedValueProvider<PsiMethod[]>() {
          @Nullable
          @Override
          public Result<PsiMethod[]> compute() {
            Project project = facet.getModule().getProject();

            Map<String, Set<String>> instanceAdapterClasses = Maps.newHashMap();
            JavaPsiFacade facade = JavaPsiFacade.getInstance(myFacet.getModule().getProject());
            PsiClass aClass = facade
              .findClass(SdkConstants.BINDING_ADAPTER_ANNOTATION, myFacet.getModule().getModuleWithDependenciesAndLibrariesScope(false));
            if (aClass == null) {
              return Result.create(PsiMethod.EMPTY_ARRAY, myManager.getModificationTracker().getJavaStructureModificationTracker());
            }

            @SuppressWarnings("unchecked")
            final Collection<? extends PsiModifierListOwner> psiElements =
              AnnotatedElementsSearch.searchElements(aClass, myFacet.getModule().getModuleScope(), PsiMethod.class).findAll();
            int methodCount = 0;

            for (PsiModifierListOwner owner : psiElements) {
              if (owner instanceof PsiMethod && !owner.hasModifierProperty(PsiModifier.STATIC)) {
                PsiClass containingClass = ((PsiMethod)owner).getContainingClass();
                if (containingClass == null) {
                  continue;
                }
                String className = containingClass.getName();
                Set<String> set = instanceAdapterClasses.get(className);
                if (set == null) {
                  set = new TreeSet<String>();
                  instanceAdapterClasses.put(className, set);
                }
                if (set.add(containingClass.getQualifiedName())) {
                  methodCount++;
                }
              }
            }
            if (methodCount == 0) {
              return Result.create(PsiMethod.EMPTY_ARRAY, myManager.getModificationTracker().getJavaStructureModificationTracker());
            }
            PsiElementFactory elementFactory = PsiElementFactory.SERVICE.getInstance(project);
            PsiMethod[] result = new PsiMethod[methodCount];
            int methodIndex = 0;
            GlobalSearchScope scope = GlobalSearchScope.allScope(project);
            for (Map.Entry<String, Set<String>> methods : instanceAdapterClasses.entrySet()) {
              if (methods.getValue().size() == 1) {
                result[methodIndex] =
                  createPsiMethod(elementFactory, "get" + methods.getKey(), Iterables.getFirst(methods.getValue(), ""), project, scope);
                methodIndex ++;
              }
              else {
                int suffix = 1;
                for (String item : methods.getValue()) {
                  final String name = "get" + methods.getKey() + suffix;
                  result[methodIndex] = createPsiMethod(elementFactory, name, item, project, scope);
                  suffix++;
                  methodIndex ++;
                }
              }
            }
            return Result.create(result, myManager.getModificationTracker().getJavaStructureModificationTracker());
          }
        }
      );
  }

  @Override
  public boolean isInterface() {
    return true;
  }

  @Override
  public PsiModifierList getModifierList() {
    return myPsiModifierList;
  }

  @Override
  public boolean isAnnotationType() {
    return false;
  }

  private PsiMethod createPsiMethod(PsiElementFactory factory, String name, String type, Project project, GlobalSearchScope scope) {
    PsiMethod method = factory.createMethod(name, PsiType.getTypeByName(type, project, scope));
    PsiUtil.setModifierProperty(method, PsiModifier.PUBLIC, true);
    PsiUtil.setModifierProperty(method, PsiModifier.ABSTRACT, true);
    return new LightMethod(PsiManager.getInstance(project), method, this);
  }

  @Override
  public String toString() {
    return "DATA binding component class";
  }

  @Nullable
  @Override
  public String getQualifiedName() {
    return SdkConstants.CLASS_DATA_BINDING_COMPONENT;
  }

  @Override
  public String getName() {
    return SdkConstants.CLASS_NAME_DATA_BINDING_COMPONENT;
  }

  @Nullable
  @Override
  public PsiClass getContainingClass() {
    return null;
  }

  @NotNull
  @Override
  public PsiField[] getFields() {
    return PsiField.EMPTY_ARRAY;
  }

  @NotNull
  @Override
  public PsiField[] getAllFields() {
    return getFields();
  }

  @NotNull
  @Override
  public PsiMethod[] getMethods() {
    return myMethodCache.getValue();
  }

  @NotNull
  @Override
  public PsiMethod[] getAllMethods() {
    return getMethods();
  }

  @NotNull
  @Override
  public PsiMethod[] findMethodsByName(@NonNls String name, boolean checkBases) {
    List<PsiMethod> result = Lists.newArrayList();
    for (PsiMethod method : myMethodCache.getValue()) {
      if (method.getName().equals(name)) {
        result.add(method);
      }
    }
    return result.size() == 0 ? PsiMethod.EMPTY_ARRAY : result.toArray(new PsiMethod[result.size()]);
  }

  @Nullable
  @Override
  public PsiFile getContainingFile() {
    if (myContainingFile == null) {
      myContainingFile = PsiFileFactory.getInstance(myFacet.getModule().getProject()).createFileFromText(
        SdkConstants.CLASS_NAME_DATA_BINDING_COMPONENT + ".java", JavaLanguage.INSTANCE,
        "package android.databinding;\n"
        + "public interface DataBindingComponent {}"
      , false, true, true, myFacet.getModule().getProject().getBaseDir());

    }
    return myContainingFile;
  }

  @Override
  public PsiIdentifier getNameIdentifier() {
    return new LightIdentifier(getManager(), getName());
  }

  @NotNull
  @Override
  public PsiElement getNavigationElement() {
    PsiFile containingFile = getContainingFile();
    return containingFile == null ? super.getNavigationElement() : containingFile;
  }
}
