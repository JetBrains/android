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
import com.android.ide.common.resources.ResourceItem;
import com.android.resources.ResourceType;
import com.android.tools.idea.databinding.DataBindingUtil;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * A data class that keeps data binding related information that was extracted from a layout file.
 */
class LayoutDataBindingInfo implements DataBindingInfo {
  private final Map<DataBindingResourceType, List<PsiDataBindingResourceItem>> myItems =
    Maps.newEnumMap(DataBindingResourceType.class);

  private String myClassName;
  private String myNonConfigurationClassName;
  private String myPackageName;
  private final PsiResourceFile myPsiResourceFile;
  private PsiClass myPsiClass;
  private long myLayoutModificationCount;
  private long myBindingModificationCount;
  private boolean myClassNameSpecifiedByUser;
  private final AndroidFacet myFacet;
  @NonNull
  private String myConfigurationName;
  private MergedDataBindingInfo myMergedInfo;

  public LayoutDataBindingInfo(@NonNull AndroidFacet facet, @NonNull PsiResourceFile psiResourceFile, @NonNull String className,
                               @NonNull String packageName, boolean classNameSpecifiedByUser) {
    myFacet = facet;
    myNonConfigurationClassName = className;
    myClassName = className;
    myPackageName = packageName;
    myClassNameSpecifiedByUser = classNameSpecifiedByUser;
    myPsiResourceFile = psiResourceFile;
    PsiDirectory parent = myPsiResourceFile.getPsiFile().getParent();
    if (parent != null) {
      myConfigurationName = parent.getName();
    } else {
      myConfigurationName = "";
    }
  }

  @Override
  public AndroidFacet getFacet() {
    return myFacet;
  }

  void update(String className, String packageName, boolean classNameSpecifiedByUser, long modificationCount) {
    if (StringUtil.equals(myNonConfigurationClassName, className) && StringUtil.equals(myPackageName, packageName) &&
        classNameSpecifiedByUser == myClassNameSpecifiedByUser) {
      return;
    }
    this.myNonConfigurationClassName = className;
    this.myPackageName = packageName;
    myLayoutModificationCount = modificationCount;
  }

  protected boolean addItem(PsiDataBindingResourceItem item) {
    List<PsiDataBindingResourceItem> items = myItems.get(item.getType());
    if (items == null) {
      items = Lists.newArrayList();
      myItems.put(item.getType(), items);
    }
    boolean newItem = !items.contains(item);
    if (newItem) {
      items.add(item);
    }
    return newItem;
  }

  public void replaceItems(@NonNull Iterable<PsiDataBindingResourceItem> items, long modificationCount) {
    boolean changed = myItems == null;
    if (myItems == null) {
      changed = true;
    } else {
      List<PsiDataBindingResourceItem> removed = Lists.newArrayList();
      for (Map.Entry<DataBindingResourceType, List<PsiDataBindingResourceItem>> entry : myItems.entrySet()) {
        for (PsiDataBindingResourceItem item : entry.getValue()) {
          if (!Iterables.contains(items, item)) {
            removed.add(item);
          }
        }
        changed |= !removed.isEmpty();
        for (PsiDataBindingResourceItem item : removed) {
          entry.getValue().remove(item);
        }
      }
      for (PsiDataBindingResourceItem item : items) {
        changed = addItem(item) | changed;
      }
    }
    if (changed) {
      myLayoutModificationCount = modificationCount;
    }
  }

  public void setMergedInfo(MergedDataBindingInfo mergedInfo) {
    if (myMergedInfo == mergedInfo) {
      return;
    }
    myBindingModificationCount ++;
    myMergedInfo = mergedInfo;
    if (mergedInfo != null) {
      myClassName = calculateConfigurationName(myConfigurationName, myNonConfigurationClassName);
    } else {
      myClassName = myNonConfigurationClassName;
    }
  }

  private static String calculateConfigurationName(String configurationName, String nonConfigurationClassName) {
    if (StringUtil.isEmpty(configurationName)) {
      return nonConfigurationClassName + "Impl";
    }
    if (configurationName.startsWith("layout-")) {
      return nonConfigurationClassName + DataBindingUtil.convertToJavaClassName(configurationName.substring("layout-".length())) + "Impl";
    } else if (configurationName.startsWith("layout")) {
      return nonConfigurationClassName + "Impl";
    } else {
      return nonConfigurationClassName + DataBindingUtil.convertToJavaClassName(configurationName) + "Impl";
    }
  }

  @Override
  public String getClassName() {
    return myClassName;
  }

  @Override
  public String getPackageName() {
    return myPackageName;
  }

  @Override
  public Project getProject() {
    return myPsiResourceFile.getPsiFile().getProject();
  }
  @Override
  public String getQualifiedName() {
    return myPackageName + "." + myClassName;
  }

  String getNonConfigurationClassName() {
    return myNonConfigurationClassName;
  }

  String getFileName() {
    return myPsiResourceFile.getName();
  }

  @Override
  public PsiElement getNavigationElement() {
    return myPsiResourceFile.getPsiFile();
  }

  @Override
  public PsiFile getPsiFile() {
    return myPsiResourceFile.getPsiFile();
  }

  @Override
  public PsiClass getPsiClass() {
    return myPsiClass;
  }

  @Override
  public void setPsiClass(PsiClass psiClass) {
    myPsiClass = psiClass;
  }

  @Override
  @NotNull
  public List<PsiDataBindingResourceItem> getItems(DataBindingResourceType type) {
    List<PsiDataBindingResourceItem> items = myItems.get(type);
    return items != null ? items : Collections.emptyList();
  }

  @Override
  public List<ViewWithId> getViewsWithIds() {
    Collection<ResourceItem> resourceItems = myPsiResourceFile.getItems();
    List<ViewWithId> result = Lists.newArrayList();
    for (ResourceItem item : resourceItems) {
      if (!ResourceType.ID.equals(item.getType())) {
        continue;
      }
      if (!(item instanceof PsiResourceItem)) {
        continue;
      }
      PsiResourceItem psiResourceItem = (PsiResourceItem)item;
      if (psiResourceItem.getTag() == null) {
        continue;
      }
      String name = item.getName();
      if (StringUtil.isEmpty(name)) {
        continue;
      }
      result.add(new ViewWithId(DataBindingUtil.convertToJavaFieldName(name.trim()), psiResourceItem.getTag()));
    }
    return result;
  }

  @Override
  @Nullable
  public Module getModule() {
    return ModuleUtilCore.findModuleForPsiElement(myPsiResourceFile.getPsiFile());
  }

  @Override
  public long getModificationCount() {
    return myLayoutModificationCount + myBindingModificationCount;
  }

  @Override
  public boolean isMerged() {
    return false;
  }

  @Nullable
  @Override
  public DataBindingInfo getMergedInfo() {
    return myMergedInfo;
  }

  @NonNull
  public String getConfigurationName() {
    return myConfigurationName;
  }
}
