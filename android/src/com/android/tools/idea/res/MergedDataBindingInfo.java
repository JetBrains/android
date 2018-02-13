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
package com.android.tools.idea.res;

import com.android.annotations.NonNull;
import com.android.ide.common.resources.DataBindingResourceType;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.CachedValue;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.util.containers.HashSet;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Data Binding Info that merges Multiple DataBindingInfo classes from different configurations.
 */
class MergedDataBindingInfo implements DataBindingInfo {
  @NonNull
  final List<LayoutDataBindingInfo> myInfoList;
  @NonNull
  private LayoutDataBindingInfo myBaseInfo;

  private PsiClass myPsiClass;

  private final CachedValue<List<ViewWithId>> myViewWithIdsCache;

  private final CachedValue<List<PsiDataBindingResourceItem>>[] myResourceItemCache;

  public MergedDataBindingInfo(List<LayoutDataBindingInfo> infoList) {
    myInfoList = infoList;
    myBaseInfo = selectBaseInfo();
    CachedValuesManager cacheManager = CachedValuesManager.getManager(myBaseInfo.getProject());
    myViewWithIdsCache = cacheManager.createCachedValue(() -> {
      Set<String> used = new HashSet<>();
      List<ViewWithId> result = new ArrayList<>();
      for (DataBindingInfo info : myInfoList) {
        info.getViewsWithIds().forEach(viewWithId -> {
          if (used.add(viewWithId.name)) {
            result.add(viewWithId);
          }
        });
      }
      return CachedValueProvider.Result.create(result, myInfoList);
    }, false);
    //noinspection unchecked
    myResourceItemCache = new CachedValue[DataBindingResourceType.values().length];
    for (DataBindingResourceType type : DataBindingResourceType.values()) {
      myResourceItemCache[type.ordinal()] = cacheManager.createCachedValue(() -> {
        Set<String> used = new HashSet<>();
        List<PsiDataBindingResourceItem> result = new ArrayList<>();
        for (DataBindingInfo info : myInfoList) {
          List<PsiDataBindingResourceItem> items = info.getItems(type);
          for (PsiDataBindingResourceItem item : items) {
            if (used.add(item.getName())) {
              result.add(item);
            }
          }
        }
        return CachedValueProvider.Result.create(result, myInfoList);
      }, false);
    }
  }

  @SuppressWarnings("ConstantConditions")
  public LayoutDataBindingInfo selectBaseInfo() {
    LayoutDataBindingInfo best = null;
    for (LayoutDataBindingInfo info : myInfoList) {
      if (best == null ||
          best.getConfigurationName().length() > info.getConfigurationName().length()) {
        best = info;
      }
    }
    return best;
  }

  @Override
  public AndroidFacet getFacet() {
    return myBaseInfo.getFacet();
  }

  @Override
  public String getClassName() {
    return myBaseInfo.getNonConfigurationClassName();
  }

  @Override
  public String getPackageName() {
    return myBaseInfo.getPackageName();
  }

  @Override
  public Project getProject() {
    return myBaseInfo.getProject();
  }

  @Override
  public String getQualifiedName() {
    return myBaseInfo.getPackageName() + "." + getClassName();
  }

  @Override
  public PsiElement getNavigationElement() {
    return myBaseInfo.getNavigationElement();
  }

  @Override
  public PsiFile getPsiFile() {
    return myBaseInfo.getPsiFile();
  }

  @Override
  public PsiClass getPsiClass() {
    return myPsiClass;
  }

  @Override
  public void setPsiClass(PsiClass psiClass) {
    myPsiClass = psiClass;
  }

  @NotNull
  @Override
  public List<PsiDataBindingResourceItem> getItems(DataBindingResourceType type) {
    return myResourceItemCache[type.ordinal()].getValue();
  }

  @Override
  public List<ViewWithId> getViewsWithIds() {
    return myViewWithIdsCache.getValue();
  }

  @Nullable
  @Override
  public Module getModule() {
    return myBaseInfo.getModule();
  }

  @Override
  public long getModificationCount() {
    int total = myInfoList.size();
    for (DataBindingInfo info : myInfoList) {
      total += info.getModificationCount();
    }
    return total;
  }

  @Override
  public boolean isMerged() {
    return true;
  }

  @Nullable
  @Override
  public DataBindingInfo getMergedInfo() {
    return null;
  }
}
