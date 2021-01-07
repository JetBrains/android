/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.res.psi;

import com.android.ide.common.resources.ResourceItem;
import com.android.utils.HashCodes;
import com.intellij.ide.util.PsiNavigationSupport;
import com.intellij.navigation.ItemPresentation;
import com.intellij.navigation.NavigationItem;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.Navigatable;
import com.intellij.psi.PsiElement;
import java.io.File;
import javax.swing.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A {@link NavigationItem} that navigates to definition of an Android resource.
 */
public final class ResourceNavigationItem implements NavigationItem {
  @NotNull private final ResourceItem myResource;
  @NotNull private final VirtualFile myFile;
  @NotNull private final Project myProject;

  /**
   * Initializes the navigation item.
   */
  public ResourceNavigationItem(@NotNull ResourceItem resourceItem, @NotNull VirtualFile file, @NotNull Project project) {
    myResource = resourceItem;
    myFile = file;
    myProject = project;
  }

  @Override
  @NotNull
  public String getName() {
    return myResource.getName();
  }

  @Override
  @NotNull
  public ItemPresentation getPresentation() {
    return new ResourceItemPresentation(myResource, myFile);
  }

  @Override
  public void navigate(boolean requestFocus) {
    Navigatable target = getNavigationTarget();
    if (target != null) {
      target.navigate(requestFocus);
    }
  }

  @Nullable
  private Navigatable getNavigationTarget() {
    PsiElement psiElement = AndroidResourceToPsiResolver.getInstance().resolveToDeclaration(myResource, myProject);
    return psiElement == null ? null : PsiNavigationSupport.getInstance().getDescriptor(psiElement);
  }

  @Override
  public boolean canNavigate() {
    Navigatable target = getNavigationTarget();
    return target != null && target.canNavigate();
  }

  @Override
  public boolean canNavigateToSource() {
    Navigatable target = getNavigationTarget();
    return target != null && target.canNavigateToSource();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    ResourceNavigationItem item = (ResourceNavigationItem)o;
    return myResource.equals(item.myResource) &&
           myFile.equals(item.myFile) &&
           myProject.equals(item.myProject);
  }

  @Override
  public int hashCode() {
    return HashCodes.mix(myResource.hashCode(), myFile.hashCode(), myProject.hashCode());
  }

  public static class ResourceItemPresentation implements ItemPresentation {
    @NotNull private final ResourceItem myResource;
    @NotNull private final VirtualFile myFile;

    public ResourceItemPresentation(@NotNull ResourceItem resourceItem, @NotNull VirtualFile file) {
      myResource = resourceItem;
      myFile = file;
    }

    @Override
    @NotNull
    public String getPresentableText() {
      VirtualFile parentDir = myFile.getParent();
      if (parentDir == null) {
        return myResource.getName();
      }

      if (myResource.isFileBased()) {
        return myResource.getName() + " (" + parentDir.getName() + ')';
      }

      return myResource.getName() + " (..." + File.separatorChar + parentDir.getName() + File.separatorChar + myFile.getName() + ')';
    }

    @Override
    @Nullable
    public Icon getIcon(boolean open) {
      return ResourceReferencePsiElement.RESOURCE_ICON;
    }
  }
}
