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
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.ModificationTracker;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.xml.XmlTag;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * A data class that keeps data binding related information that was extracted from a layout file.
 */
public interface DataBindingInfo extends ModificationTracker {
  AndroidFacet getFacet();

  String getClassName();

  String getPackageName();

  Project getProject();

  String getQualifiedName();

  PsiElement getNavigationElement();

  PsiFile getPsiFile();

  PsiClass getPsiClass();

  void setPsiClass(PsiClass psiClass);

  // true if this is a merged info, false if it refers to a layout file.
  boolean isMerged();

  // if this layout has variations in other configurations, it also has a merged info.
  @Nullable
  DataBindingInfo getMergedInfo();

  @NotNull
  List<PsiDataBindingResourceItem> getItems(DataBindingResourceType type);

  List<ViewWithId> getViewsWithIds();

  @Nullable
  Module getModule();

  @Override
  long getModificationCount();

  class ViewWithId {
    public final String name;
    public final XmlTag tag;

    public ViewWithId(String name, XmlTag tag) {
      this.name = name;
      this.tag = tag;
    }
  }
}
