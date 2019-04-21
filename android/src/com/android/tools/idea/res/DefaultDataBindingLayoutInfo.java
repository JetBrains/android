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

import com.android.ide.common.resources.DataBindingResourceType;
import com.android.ide.common.resources.ResourceItem;
import com.android.resources.ResourceType;
import com.android.tools.idea.databinding.DataBindingUtil;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.xml.XmlTag;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * Data Binding info for a single, target layout XML file.
 *
 * See also: {@link MergedDataBindingLayoutInfo}
 */
public class DefaultDataBindingLayoutInfo implements DataBindingLayoutInfo {
  @NotNull private final Map<DataBindingResourceType, Map<String, PsiDataBindingResourceItem>> myItems =
      new EnumMap<>(DataBindingResourceType.class);

  private String myClassName;
  private String myNonConfigurationClassName;
  private String myPackageName;
  private final PsiResourceFile myPsiResourceFile;
  private PsiClass myPsiClass;
  private long myLayoutModificationCount;
  private long myBindingModificationCount;
  private boolean myClassNameSpecifiedByUser;
  private final AndroidFacet myFacet;
  @NotNull private final String myConfigurationName;
  private MergedDataBindingLayoutInfo myMergedInfo;

  public DefaultDataBindingLayoutInfo(@NotNull AndroidFacet facet, @NotNull PsiResourceFile psiResourceFile, @NotNull String className,
                                      @NotNull String packageName, boolean classNameSpecifiedByUser) {
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
    updateClassName();
    myLayoutModificationCount = modificationCount;
  }

  public void replaceItems(@NotNull List<PsiDataBindingResourceItem> newItems, long modificationCount) {
    Map<DataBindingResourceType, Map<String, PsiDataBindingResourceItem>> itemsByType = new EnumMap<>(DataBindingResourceType.class);
    for (PsiDataBindingResourceItem item : newItems) {
      DataBindingResourceType type = item.getType();
      Map<String, PsiDataBindingResourceItem> itemsByName = itemsByType.computeIfAbsent(type, t -> new HashMap<>());
      itemsByName.put(item.getName(), item);
    }

    if (!itemsByType.equals(myItems)) {
      myItems.clear();
      myItems.putAll(itemsByType);
      myLayoutModificationCount = modificationCount;
    }
  }

  public void setMergedInfo(MergedDataBindingLayoutInfo mergedInfo) {
    if (myMergedInfo == mergedInfo) {
      return;
    }
    myBindingModificationCount++;
    myMergedInfo = mergedInfo;
    updateClassName();
  }

  private void updateClassName() {
    if (myMergedInfo != null) {
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
    return new DataBindingLayoutInfoFile(this);
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
  public Map<String, PsiDataBindingResourceItem> getItems(@NotNull DataBindingResourceType type) {
    Map<String, PsiDataBindingResourceItem> itemsByName = myItems.get(type);
    return itemsByName == null ? Collections.emptyMap() : itemsByName;
  }

  @Override
  @NotNull
  public List<ViewWithId> getViewsWithIds() {
    List<ViewWithId> result = new ArrayList<>();
    for (ResourceItem item : myPsiResourceFile) {
      if (!ResourceType.ID.equals(item.getType())) {
        continue;
      }
      if (!(item instanceof PsiResourceItem)) {
        continue;
      }
      PsiResourceItem psiResourceItem = (PsiResourceItem)item;
      XmlTag tag = psiResourceItem.getTag();
      if (tag == null) {
        continue;
      }
      String name = item.getName();
      if (StringUtil.isEmpty(name)) {
        continue;
      }
      result.add(new ViewWithId(DataBindingUtil.convertToJavaFieldName(name.trim()), tag));
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
  public DataBindingLayoutInfo getMergedInfo() {
    return myMergedInfo;
  }

  @NotNull
  public String getConfigurationName() {
    return myConfigurationName;
  }
}
