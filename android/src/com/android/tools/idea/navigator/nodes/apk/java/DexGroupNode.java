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
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Queryable;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.android.facet.AndroidSourceType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;

import static com.intellij.icons.AllIcons.Modules.SourceRoot;
import static com.intellij.ui.SimpleTextAttributes.GRAY_ATTRIBUTES;
import static com.intellij.ui.SimpleTextAttributes.REGULAR_ATTRIBUTES;

public class DexGroupNode extends ProjectViewNode<VirtualFile> {
  @Nullable private final DexFileContents myDexFileContents;

  public DexGroupNode(@NotNull Project project, @NotNull ViewSettings settings, @Nullable VirtualFile dexFile) {
    super(project, dexFile, settings);
    if (dexFile != null) {
      myDexFileContents = new DexFileContents(dexFile);
    }
    else {
      myDexFileContents = null;
    }
  }

  @Override
  @NotNull
  public Collection<? extends AbstractTreeNode> getChildren() {
    if (myDexFileContents != null) {
      try {
        return getChildren(myDexFileContents.getPackages());
      }
      catch (Throwable e) {
        Logger.getInstance(getClass()).warn("Failed to parse dex file", e);
        // TODO: return an "error" node instead.
      }
    }
    return Collections.emptyList();
  }

  @NotNull
  private Collection<? extends AbstractTreeNode> getChildren(@NotNull Collection<ApkPackage> packages) {
    assert myProject != null;
    Collection<AbstractTreeNode> children = new ArrayList<>();
    ViewSettings settings = getSettings();
    if (settings.isFlattenPackages()) {
      // "Flat" package view.
      addPackagesAsFlatList(myProject, packages, children, settings);
    }
    else {
      // "Hierarchical" package view.
      addPackagesAsTree(packages, children, myProject, settings);
    }
    return children;
  }

  private static void addPackagesAsFlatList(@NotNull Project project,
                                            @NotNull Collection<ApkPackage> packages,
                                            @NotNull Collection<AbstractTreeNode> children,
                                            @NotNull ViewSettings settings) {
    for (ApkPackage apkPackage : packages) {
      boolean hideEmptyMiddlePackages = settings.isHideEmptyMiddlePackages();
      if (!hideEmptyMiddlePackages || !apkPackage.getClasses().isEmpty()) {
        children.add(new PackageNode(project, apkPackage, settings));
      }
      addPackagesAsFlatList(project, apkPackage.getSubpackages(), children, settings);
    }
  }

  private static void addPackagesAsTree(@NotNull Collection<ApkPackage> packages,
                                        @NotNull Collection<AbstractTreeNode> children,
                                        @NotNull Project project,
                                        @NotNull ViewSettings settings) {
    if (settings.isHideEmptyMiddlePackages()) {
      for (ApkPackage apkPackage : packages) {
        if (!apkPackage.getClasses().isEmpty() || apkPackage.doSubpackagesHaveClasses()) {
          children.add(new PackageNode(project, apkPackage, settings));
        }
        else {
          addPackagesAsTree(apkPackage.getSubpackages(), children, project, settings);
        }
      }
    }
    else {
      for (ApkPackage apkPackage : packages) {
        children.add(new PackageNode(project, apkPackage, settings));
      }
    }
  }

  @Override
  public boolean contains(@NotNull VirtualFile file) {
    return false;
  }

  @Override
  protected void update(@NotNull PresentationData presentation) {
    presentation.setIcon(SourceRoot);
    presentation.addText(getSourceType().getName(), REGULAR_ATTRIBUTES);
    VirtualFile dexFile = getValue();
    if (dexFile != null) {
      presentation.addText(" (" + dexFile.getName() + ")", GRAY_ATTRIBUTES);
    }
  }

  @Override
  @Nullable
  public Comparable getSortKey() {
    return getTypeSortKey();
  }

  @Override
  @Nullable
  public Comparable getTypeSortKey() {
    return getSourceType();
  }

  @NotNull
  private static AndroidSourceType getSourceType() {
    return AndroidSourceType.JAVA;
  }

  @Override
  @Nullable
  public String toTestString(@Nullable Queryable.PrintInfo printInfo) {
    String text = getSourceType().getName();
    VirtualFile dexFile = getValue();
    if (dexFile != null) {
      text = text + " (" + dexFile.getName() + ")";
    }

    return text;
  }
}
