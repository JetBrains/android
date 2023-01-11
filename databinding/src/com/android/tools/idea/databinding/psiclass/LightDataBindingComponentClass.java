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
package com.android.tools.idea.databinding.psiclass;

import com.android.SdkConstants;
import com.android.tools.idea.AndroidPsiUtils;
import com.android.tools.idea.databinding.DataBindingMode;
import com.android.tools.idea.databinding.module.LayoutBindingModuleCache;
import com.android.tools.idea.projectsystem.ProjectSystemUtil;
import com.android.tools.idea.projectsystem.ScopeType;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.ModificationTracker;
import com.intellij.openapi.util.NotNullLazyValue;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementFactory;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileFactory;
import com.intellij.psi.PsiIdentifier;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiModifierListOwner;
import com.intellij.psi.PsiType;
import com.intellij.psi.impl.light.LightIdentifier;
import com.intellij.psi.impl.light.LightMethod;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.searches.AnnotatedElementsSearch;
import com.intellij.psi.util.CachedValue;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.PsiUtil;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.jetbrains.android.augment.AndroidLightClassBase;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * In-memory PSI for the generated DataBindingComponent class.
 *
 * Note: A DataBindingComponent's purpose is to be (optionally) subclassed in tests, to allow
 * overriding BindingAdapters with test-specific implementations if necessary. It is not expected
 * that a user would grab an instance of and interact with one, so its PSI fields and methods are
 * not implemented here.
 *
 * See also: https://developer.android.com/reference/android/databinding/DataBindingComponent
 */
public class LightDataBindingComponentClass extends AndroidLightClassBase implements ModificationTracker {
  private final AndroidFacet myFacet;
  private final CachedValue<PsiMethod[]> myMethodCache;
  private final NotNullLazyValue<PsiFile> myContainingFile;
  private final DataBindingMode myMode;

  public LightDataBindingComponentClass(@NotNull PsiManager psiManager, final AndroidFacet facet) {
    super(psiManager, ImmutableSet.of(PsiModifier.PUBLIC));
    myMode = LayoutBindingModuleCache.getInstance(facet).getDataBindingMode();
    myFacet = facet;

    Project project = facet.getModule().getProject();
    ModificationTracker modificationTracker = AndroidPsiUtils.getPsiModificationTrackerIgnoringXml(project);

    myMethodCache =
      CachedValuesManager.getManager(project).createCachedValue(
        () -> {
          Map<String, Set<String>> instanceAdapterClasses = new HashMap<>();
          JavaPsiFacade facade = JavaPsiFacade.getInstance(myFacet.getModule().getProject());
          GlobalSearchScope moduleScope = ProjectSystemUtil.getModuleSystem(myFacet).getResolveScope(ScopeType.MAIN);
          PsiClass aClass = facade.findClass(myMode.bindingAdapter, moduleScope);
          if (aClass == null) {
            return CachedValueProvider.Result.create(PsiMethod.EMPTY_ARRAY, modificationTracker);
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
                set = new TreeSet<>();
                instanceAdapterClasses.put(className, set);
              }
              if (set.add(containingClass.getQualifiedName())) {
                methodCount++;
              }
            }
          }
          if (methodCount == 0) {
            return CachedValueProvider.Result.create(PsiMethod.EMPTY_ARRAY, modificationTracker);
          }
          PsiElementFactory elementFactory = PsiElementFactory.getInstance(project);
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
          return CachedValueProvider.Result.create(result, modificationTracker);
        }
      , false);

    myContainingFile = NotNullLazyValue.atomicLazy(() -> {
      String packageName = myMode.packageName;
      if (packageName.endsWith(".")) {
        packageName = packageName.substring(0, packageName.length() - 1);
      }
      return PsiFileFactory.getInstance(myFacet.getModule().getProject()).createFileFromText(
        SdkConstants.CLASS_NAME_DATA_BINDING_COMPONENT + ".java", JavaLanguage.INSTANCE,
        "package " + packageName + ";\n"
        + "public interface DataBindingComponent {}"
        , false, true, true);
    });

    setModuleInfo(facet.getModule(), false);
  }

  @Override
  public boolean isInterface() {
    return true;
  }

  @Override
  public boolean isAnnotationType() {
    return super.isAnnotationType();
  }

  private PsiMethod createPsiMethod(PsiElementFactory factory, String name, String type, Project project, GlobalSearchScope scope) {
    PsiMethod method = factory.createMethod(name, PsiType.getTypeByName(type, project, scope));
    PsiUtil.setModifierProperty(method, PsiModifier.PUBLIC, true);
    PsiUtil.setModifierProperty(method, PsiModifier.ABSTRACT, true);
    return new LightMethod(PsiManager.getInstance(project), method, this);
  }

  @Nullable
  @Override
  public String getQualifiedName() {
    return myMode.dataBindingComponent;
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
    return super.getFields();
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
    List<PsiMethod> result = new ArrayList<>();
    for (PsiMethod method : myMethodCache.getValue()) {
      if (method.getName().equals(name)) {
        result.add(method);
      }
    }
    return result.isEmpty() ? PsiMethod.EMPTY_ARRAY : result.toArray(PsiMethod.EMPTY_ARRAY);
  }

  @Nullable
  @Override
  public PsiFile getContainingFile() {
    return myContainingFile.getValue();
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

  @Override
  public long getModificationCount() {
    return 0;
  }
}
