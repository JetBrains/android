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
package com.android.tools.idea.rendering;

import com.android.annotations.NonNull;
import com.android.ide.common.res2.DataBindingResourceType;
import com.android.ide.common.res2.ResourceItem;
import com.android.resources.ResourceType;
import com.android.tools.idea.databinding.DataBindingUtil;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.ModificationTracker;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.xml.XmlTag;
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
public class DataBindingInfo implements ModificationTracker {
  private final Map<DataBindingResourceType, List<PsiDataBindingResourceItem>> myItems =
    Maps.newEnumMap(DataBindingResourceType.class);

  private String myClassName;
  private String myPackageName;
  private final PsiResourceFile myPsiResourceFile;
  private PsiClass myPsiClass;
  private long myModificationCount;
  private final AndroidFacet myFacet;

  public DataBindingInfo(AndroidFacet facet, PsiResourceFile psiResourceFile, String className, String packageName) {
    myFacet = facet;
    myClassName = className;
    myPackageName = packageName;
    myPsiResourceFile = psiResourceFile;
  }

  public AndroidFacet getFacet() {
    return myFacet;
  }

  void update(String className, String packageName, long modificationCount) {
    if (StringUtil.equals(myClassName, className) && StringUtil.equals(myPackageName, packageName)) {
      return;
    }
    this.myClassName = className;
    this.myPackageName = packageName;
    myModificationCount = modificationCount;
  }

  private boolean addItem(PsiDataBindingResourceItem item) {
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
        changed |= removed.size() > 0;
        for (PsiDataBindingResourceItem item : removed) {
          entry.getValue().remove(item);
        }
      }
      for (PsiDataBindingResourceItem item : items) {
        changed = addItem(item) | changed;
      }
    }
    if (changed) {
      myModificationCount = modificationCount;
    }
  }

  public String getClassName() {
    return myClassName;
  }

  public String getPackageName() {
    return myPackageName;
  }

  public Project getProject() {
    return myPsiResourceFile.getPsiFile().getProject();
  }
  public String getQualifiedName() {
    return myPackageName + "." + myClassName;
  }

  public PsiElement getNavigationElement() {
    return myPsiResourceFile.getPsiFile();
  }

  public PsiFile getPsiFile() {
    return myPsiResourceFile.getPsiFile();
  }

  public PsiClass getPsiClass() {
    return myPsiClass;
  }

  public void setPsiClass(PsiClass psiClass) {
    myPsiClass = psiClass;
  }

  @NotNull
  public List<PsiDataBindingResourceItem> getItems(DataBindingResourceType type) {
    List<PsiDataBindingResourceItem> items = myItems.get(type);
    return items != null ? items : Collections.<PsiDataBindingResourceItem>emptyList();
  }

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
      result.add(new ViewWithId(DataBindingUtil.convertToJavaFieldName(item.getName()), psiResourceItem.getTag()));
    }
    return result;
  }

  @Nullable
  public Module getModule() {
    return ModuleUtilCore.findModuleForPsiElement(myPsiResourceFile.getPsiFile());
  }

  @Override
  public long getModificationCount() {
    return myModificationCount;
  }

  public static class ViewWithId {
    public final String name;
    public final XmlTag tag;

    public ViewWithId(String name, XmlTag tag) {
      this.name = name;
      this.tag = tag;
    }
  }
}
