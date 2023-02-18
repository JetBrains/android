/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.navigator.nodes.apk.java;

import com.android.tools.idea.apk.debugging.*;
import com.intellij.ide.projectView.PresentationData;
import com.intellij.ide.projectView.ProjectViewNode;
import com.intellij.ide.projectView.ViewSettings;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Queryable;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.IconManager;
import com.intellij.ui.PlatformIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;

import static com.intellij.openapi.util.io.FileUtil.isAncestor;
import static com.intellij.openapi.vfs.VfsUtilCore.virtualToIoFile;

class PackageNode extends ProjectViewNode<ApkPackage> {
  @NotNull private final ApkPackage myPackage;
  @NotNull private final DexSourceFiles myDexSourceFiles;

  PackageNode(@NotNull Project project,
              @NotNull ApkPackage apkPackage,
              @NotNull ViewSettings settings,
              @NotNull DexSourceFiles dexSourceFiles) {
    super(project, apkPackage, settings);
    // TODO show members (methods/fields) if ViewSettings are configured to show those.
    myPackage = apkPackage;
    myDexSourceFiles = dexSourceFiles;
  }

  @Override
  @NotNull
  public Collection<? extends AbstractTreeNode<?>> getChildren() {
    assert myProject != null;

    Collection<AbstractTreeNode<?>> children = new ArrayList<>();
    ViewSettings settings = getSettings();
    if (!settings.isFlattenPackages()) {
      addSubpackagesAsTree(myPackage.getSubpackages(), children);
    }
    for (ApkClass apkClass : myPackage.getClasses()) {
      children.add(new ClassNode(myProject, apkClass, settings, myDexSourceFiles));
    }
    return children;
  }

  private void addSubpackagesAsTree(@NotNull Collection<ApkPackage> subpackages, @NotNull Collection<AbstractTreeNode<?>> children) {
    if (getSettings().isHideEmptyMiddlePackages()) {
      for (ApkPackage subpackage : subpackages) {
        if (!subpackage.getClasses().isEmpty() || subpackage.doSubpackagesHaveClasses()) {
          children.add(createChildNode(subpackage));
        }
        else {
          addSubpackagesAsTree(subpackage.getSubpackages(), children);
        }
      }
    }
    else {
      for (ApkPackage subpackage : subpackages) {
        children.add(createChildNode(subpackage));
      }
    }
  }

  @NotNull
  private PackageNode createChildNode(@NotNull ApkPackage subpackage) {
    assert myProject != null;
    return new PackageNode(myProject, subpackage, getSettings(), myDexSourceFiles);
  }

  @Override
  public boolean contains(@NotNull VirtualFile file) {
    String fqn = myPackage.getFqn();
    if (myDexSourceFiles.isJavaFile(file)) {
      assert myProject != null;
      String foundPackage = myDexSourceFiles.findJavaPackageNameIn(file);
      if (foundPackage != null && foundPackage.contains(fqn)) {
        return true;
      }
    }
    else if (myDexSourceFiles.isSmaliFile(file)) {
      File filePath = virtualToIoFile(file);
      File packageFilePath = myDexSourceFiles.findSmaliFilePathForPackage(fqn);
      return isAncestor(packageFilePath, filePath, false);
    }
    return false;
  }

  @Override
  protected void update(@NotNull PresentationData presentation) {
    presentation.setIcon(IconManager.getInstance().getPlatformIcon(PlatformIcons.Package));
    presentation.setPresentableText(getText());
  }

  @Override
  @Nullable
  public String toTestString(@Nullable Queryable.PrintInfo printInfo) {
    return getText();
  }

  @NotNull
  private String getText() {
    ViewSettings settings = getSettings();
    String text;
    if (settings.isFlattenPackages()) {
      text = myPackage.getFqn();
    }
    else if (settings.isHideEmptyMiddlePackages()) {
      ApkPackage parentPackage = myPackage.getParent();
      AbstractTreeNode parentNode = getParent();
      ApkPackage parentNodePackage = parentNode instanceof PackageNode ? ((PackageNode)parentNode).getValue() : null;
      text = parentPackage != parentNodePackage ? myPackage.getFqn() : myPackage.getName();
    }
    else {
      text = myPackage.getName();
    }
    return text;
  }
}
