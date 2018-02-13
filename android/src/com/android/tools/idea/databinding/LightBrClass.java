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
package com.android.tools.idea.databinding;

import com.android.ide.common.resources.DataBindingResourceType;
import com.android.tools.idea.res.DataBindingInfo;
import com.android.tools.idea.res.LocalResourceRepository;
import com.android.tools.idea.res.ModuleResourceRepository;
import com.android.tools.idea.res.PsiDataBindingResourceItem;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.ModificationTracker;
import com.intellij.psi.*;
import com.intellij.psi.impl.light.LightField;
import com.intellij.psi.impl.light.LightIdentifier;
import com.intellij.psi.search.searches.AnnotatedElementsSearch;
import com.intellij.psi.util.CachedValue;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.HashSet;
import org.jetbrains.android.augment.AndroidLightClassBase;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Map;
import java.util.Set;

/**
 * The light class that represents a data binding BR file
 */
public class LightBrClass extends AndroidLightClassBase {
  private static final String BINDABLE_QUALIFIED_NAME = "android.databinding.Bindable";
  private final AndroidFacet myFacet;
  private CachedValue<PsiField[]> myFieldCache;
  @NotNull
  private String[] myCachedFieldNames = new String[]{"_all"};
  private final String myQualifiedName;
  private PsiFile myContainingFile;
  private final Object myLock = new Object();

  public LightBrClass(@NotNull PsiManager psiManager, final AndroidFacet facet) {
    super(psiManager);
    myQualifiedName = DataBindingUtil.getBrQualifiedName(facet);
    myFacet = facet;
    myFieldCache =
      CachedValuesManager.getManager(facet.getModule().getProject()).createCachedValue(
        new ResourceCacheValueProvider<PsiField[]>(facet, myLock,
                                                   psiManager.getModificationTracker().getJavaStructureModificationTracker()) {
          @Override
          PsiField[] doCompute() {
            Project project = facet.getModule().getProject();
            PsiElementFactory elementFactory = PsiElementFactory.SERVICE.getInstance(project);
            LocalResourceRepository moduleResources = ModuleResourceRepository.findExistingInstance(facet);
            if (moduleResources == null) {
              return defaultValue();
            }
            Map<String, DataBindingInfo> dataBindingResourceFiles = moduleResources.getDataBindingResourceFiles();
            if (dataBindingResourceFiles == null) {
              return defaultValue();
            }
            Set<String> variableNames = new HashSet<>();
            for (DataBindingInfo info : dataBindingResourceFiles.values()) {
              for (PsiDataBindingResourceItem item : info.getItems(DataBindingResourceType.VARIABLE)) {
                variableNames.add(item.getName());
              }
            }
            Set<String> bindables = collectVariableNamesFromBindables();
            if (bindables != null) {
              variableNames.addAll(bindables);
            }
            PsiField[] result = new PsiField[variableNames.size() + 1];
            result[0] = createPsiField(project, elementFactory, "_all");
            int i = 1;
            for (String variable : variableNames) {
              result[i++] = createPsiField(project, elementFactory, variable);
            }
            myCachedFieldNames = ArrayUtil.toStringArray(variableNames);
            return result;
          }

          @Override
          PsiField[] defaultValue() {
            Project project = facet.getModule().getProject();
            return new PsiField[]{createPsiField(project, PsiElementFactory.SERVICE.getInstance(project), "_all")};
          }
        }, false);
  }

  private Set<String> collectVariableNamesFromBindables() {
    JavaPsiFacade facade = JavaPsiFacade.getInstance(myFacet.getModule().getProject());
    PsiClass aClass = facade.findClass(BINDABLE_QUALIFIED_NAME, myFacet.getModule().getModuleWithDependenciesAndLibrariesScope(false));
    if (aClass == null) {
      return null;
    }
    //noinspection unchecked
    final Collection<? extends PsiModifierListOwner> psiElements =
      AnnotatedElementsSearch.searchElements(aClass, myFacet.getModule().getModuleScope(), PsiMethod.class, PsiField.class).findAll();
    return BrUtil.collectIds(psiElements);
  }

  private PsiField createPsiField(Project project, PsiElementFactory factory, String id) {
    PsiField field = factory.createField(id, PsiType.INT);
    PsiUtil.setModifierProperty(field, PsiModifier.PUBLIC, true);
    PsiUtil.setModifierProperty(field, PsiModifier.STATIC, true);
    PsiUtil.setModifierProperty(field, PsiModifier.FINAL, true);
    return new LightBRField(PsiManager.getInstance(project), field, this);
  }

  @Override
  public String toString() {
    return "BR class for " + myFacet;
  }

  @Nullable
  @Override
  public String getQualifiedName() {
    return myQualifiedName;
  }

  @Override
  public String getName() {
    return DataBindingUtil.BR;
  }

  @NotNull
  public String[] getAllFieldNames() {
    return myCachedFieldNames;
  }

  @Nullable
  @Override
  public PsiClass getContainingClass() {
    return null;
  }

  @NotNull
  @Override
  public PsiField[] getFields() {
    return myFieldCache.getValue();
  }

  @NotNull
  @Override
  public PsiField[] getAllFields() {
    return getFields();
  }


  @Nullable
  @Override
  public PsiFile getContainingFile() {
    if (myContainingFile == null) {
      // TODO: using R file for now. Would be better if we create a real VirtualFile for this.
      PsiClass aClass = JavaPsiFacade.getInstance(myFacet.getModule().getProject())
        .findClass(DataBindingUtil.getGeneratedPackageName(myFacet) + ".R", myFacet.getModule().getModuleScope());
      if (aClass != null) {
        myContainingFile = aClass.getContainingFile();
      }
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

  @Override
  public boolean isValid() {
    return true;
  }

  @Override
  public boolean canNavigate() {
    return false;
  }

  @Override
  public boolean canNavigateToSource() {
    return false;
  }

  /**
   * The light field representing elements of BR class
   */
  static class LightBRField extends LightField implements ModificationTracker {

    public LightBRField(@NotNull PsiManager manager, @NotNull PsiField field, @NotNull PsiClass containingClass) {
      super(manager, field, containingClass);
    }

    @Override
    public long getModificationCount() {
      // See http://b.android.com/212766
      // The field can't change; it's computed on the fly.
      // Needed by the LightBrClass field cache.
      return 0;
    }
  }
}