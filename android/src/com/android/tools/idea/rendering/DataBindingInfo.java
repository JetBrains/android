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

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.ide.common.res2.DataBindingResourceItem;
import com.android.ide.common.res2.DataBindingResourceType;
import com.android.ide.common.res2.ResourceItem;
import com.android.resources.ResourceType;
import com.android.tools.idea.databinding.DataBindingUtil;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.ModificationTracker;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.xml.XmlTag;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * A data class that keeps data binding related information that was extracted from a layout file.
 */
public class DataBindingInfo implements ModificationTracker {
  private final Map<DataBindingResourceType, List<PsiDataBindingResourceItem>> myItems =
    Maps.newEnumMap(DataBindingResourceType.class);

  private static List<String> VIEW_PACKAGE_ELEMENTS = Arrays.asList(SdkConstants.VIEW, SdkConstants.VIEW_GROUP, SdkConstants.VIEW_STUB,
                                                                    SdkConstants.TEXTURE_VIEW, SdkConstants.SURFACE_VIEW);
  private String myClassName;
  private String myPackageName;
  private final PsiResourceFile myPsiResourceFile;
  private PsiClass myPsiClass;
  private long myModificationCount;

  public DataBindingInfo(PsiResourceFile psiResourceFile, String className, String packageName) {
    this.myClassName = className;
    this.myPackageName = packageName;
    myPsiResourceFile = psiResourceFile;
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
      List<DataBindingResourceItem> removed = Lists.newArrayList();
      for (Map.Entry<DataBindingResourceType, List<PsiDataBindingResourceItem>> entry : myItems.entrySet()) {
        for (PsiDataBindingResourceItem item : entry.getValue()) {
          if (!Iterables.contains(items, item)) {
            removed.add(item);
          }
        }
        changed |= removed.size() > 0;
        for (DataBindingResourceItem item : removed) {
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

  public List<PsiDataBindingResourceItem> getItems(DataBindingResourceType type) {
    return myItems.get(type);
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
      String viewName = getViewName(psiResourceItem.getTag());
      if (viewName == null) {
        continue;
      }
      String viewClass = getViewClass(viewName);
      result.add(new ViewWithId(DataBindingUtil.convertToJavaFieldName(item.getName()), viewClass, psiResourceItem.getTag()));
    }
    return result;
  }

  private static String getViewName(XmlTag tag) {
    // TODO handle include and merge
    String viewName = tag.getName();
    if (SdkConstants.VIEW_TAG.equals(viewName)) {
      viewName = tag.getAttributeValue(SdkConstants.ATTR_CLASS, SdkConstants.ANDROID_URI);
    }
    return viewName;
  }

  private static String getViewClass(String elementName) {
    if (elementName.indexOf('.') == -1) {
      if (VIEW_PACKAGE_ELEMENTS.contains(elementName)) {
        return SdkConstants.VIEW_PKG_PREFIX + elementName;
      } else if (SdkConstants.WEB_VIEW.equals(elementName)) {
        return SdkConstants.ANDROID_WEBKIT_PKG + elementName;
      }
      return SdkConstants.WIDGET_PKG_PREFIX + elementName;
    } else {
      return elementName;
    }
  }

  @Nullable
  public Module getModule() {
    return ModuleUtil.findModuleForPsiElement(myPsiResourceFile.getPsiFile());
  }

  @Override
  public long getModificationCount() {
    return myModificationCount;
  }

  public static class ViewWithId {
    public final String name;
    public final String className;
    public final XmlTag tag;

    public ViewWithId(String name, String className, XmlTag tag) {
      this.name = name;
      this.className = className;
      this.tag = tag;
    }
  }
}
