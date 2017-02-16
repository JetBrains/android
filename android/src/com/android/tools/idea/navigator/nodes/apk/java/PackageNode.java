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

import com.intellij.ide.projectView.PresentationData;
import com.intellij.ide.projectView.ProjectViewNode;
import com.intellij.ide.projectView.ViewSettings;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Queryable;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.PlatformIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;

class PackageNode extends ProjectViewNode<ApkPackage> {
  @NotNull private final ApkPackage myApkPackage;

  PackageNode(@NotNull Project project, @NotNull ApkPackage apkPackage, @NotNull ViewSettings settings) {
    super(project, apkPackage, settings);
    // TODO show members (methods/fields) if ViewSettings are configured to show those.
    myApkPackage = apkPackage;
  }

  @Override
  @NotNull
  public Collection<? extends AbstractTreeNode> getChildren() {
    assert myProject != null;

    Collection<AbstractTreeNode> children = new ArrayList<>();
    ViewSettings settings = getSettings();
    if (!settings.isFlattenPackages()) {
      addSubpackagesAsTree(myApkPackage.getSubpackages(), children, myProject, settings);
    }
    for (ApkClass apkClass : myApkPackage.getClasses()) {
      children.add(new ClassNode(myProject, apkClass, settings));
    }
    return children;
  }

  private static void addSubpackagesAsTree(@NotNull Collection<ApkPackage> subpackages,
                                           @NotNull Collection<AbstractTreeNode> children,
                                           @NotNull Project project,
                                           @NotNull ViewSettings settings) {
    if (settings.isHideEmptyMiddlePackages()) {
      for (ApkPackage subpackage : subpackages) {
        if (!subpackage.getClasses().isEmpty() || subpackage.doSubpackagesHaveClasses()) {
          children.add(new PackageNode(project, subpackage, settings));
        }
        else {
          addSubpackagesAsTree(subpackage.getSubpackages(), children, project, settings);
        }
      }
    }
    else {
      for (ApkPackage subpackage : subpackages) {
        children.add(new PackageNode(project, subpackage, settings));
      }
    }
  }

  @Override
  public boolean contains(@NotNull VirtualFile file) {
    return false;
  }

  @Override
  protected void update(@NotNull PresentationData presentation) {
    presentation.setIcon(PlatformIcons.PACKAGE_ICON);
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
      text = myApkPackage.getFqn();
    }
    else if (settings.isHideEmptyMiddlePackages()) {
      ApkPackage parentPackage = myApkPackage.getParent();
      AbstractTreeNode parentNode = getParent();
      ApkPackage parentNodePackage = parentNode instanceof PackageNode ? ((PackageNode)parentNode).getValue() : null;
      text = parentPackage != parentNodePackage ? myApkPackage.getFqn() : myApkPackage.getName();
    }
    else {
      text = myApkPackage.getName();
    }
    return text;
  }
}
