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
import com.android.resources.ResourceType;
import com.intellij.navigation.ItemPresentation;
import com.intellij.navigation.NavigationItem;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.xml.XmlAttribute;
import java.io.File;
import javax.swing.Icon;
import org.jetbrains.android.resourceManagers.ValueResourceInfoImpl;
import org.jetbrains.android.util.AndroidResourceUtil;
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
    NavigationItem target = getNavigationTarget();
    if (target != null) {
      target.navigate(requestFocus);
    }
  }

  @Nullable
  private NavigationItem getNavigationTarget() {
    if (myResource.isFileBased()) {
      return PsiManager.getInstance(myProject).findFile(myFile);
    }

    if (myResource.getType() == ResourceType.ID) {
      XmlAttribute xmlAttribute = AndroidResourceUtil.getIdDeclarationAttribute(myProject, myResource);
      return xmlAttribute == null ? null : (NavigationItem)xmlAttribute.getValueElement();
    }

    // TODO(sprigogin): Use AndroidResourceUtil.getDeclaringAttributeValue when ag/4888814 is submitted.
    return (NavigationItem)new ValueResourceInfoImpl(myResource, myFile, myProject).computeXmlElement();
  }

  @Override
  public boolean canNavigate() {
    NavigationItem target = getNavigationTarget();
    return target != null && target.canNavigate();
  }

  @Override
  public boolean canNavigateToSource() {
    NavigationItem target = getNavigationTarget();
    return target != null && target.canNavigateToSource();
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
    public String getLocationString() {
      return null;
    }

    @Override
    @Nullable
    public Icon getIcon(boolean open) {
      return null;
    }
  }
}
