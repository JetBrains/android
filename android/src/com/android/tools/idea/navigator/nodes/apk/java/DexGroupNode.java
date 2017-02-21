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

import com.android.tools.idea.apk.debugging.ApkPackage;
import com.android.tools.idea.apk.debugging.JavaFiles;
import com.android.tools.idea.apk.debugging.SmaliFiles;
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

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import static com.android.tools.idea.apk.debugging.JavaFiles.isJavaFile;
import static com.android.tools.idea.apk.debugging.SmaliFiles.isSmaliFile;
import static com.intellij.icons.AllIcons.Modules.SourceRoot;
import static com.intellij.openapi.util.io.FileUtil.isAncestor;
import static com.intellij.openapi.vfs.VfsUtilCore.virtualToIoFile;
import static com.intellij.ui.SimpleTextAttributes.GRAY_ATTRIBUTES;
import static com.intellij.ui.SimpleTextAttributes.REGULAR_ATTRIBUTES;

public class DexGroupNode extends ProjectViewNode<VirtualFile> {
  @NotNull private final JavaFiles myJavaFiles;
  @NotNull private final SmaliFiles mySmaliFiles;

  @Nullable private final DexFileContents myDexFileContents;

  private Collection<ApkPackage> myPackages = Collections.emptyList();

  public DexGroupNode(@NotNull Project project, @NotNull ViewSettings settings, @Nullable VirtualFile dexFile) {
    super(project, dexFile, settings);
    myJavaFiles = new JavaFiles();
    mySmaliFiles = new SmaliFiles(project);
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
        myPackages = myDexFileContents.getPackages();
        return getChildren(myPackages);
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
    List<AbstractTreeNode> children = new ArrayList<>();
    ViewSettings settings = getSettings();
    if (settings.isFlattenPackages()) {
      // "Flat" package view.
      addPackagesAsFlatList(packages, children);
    }
    else {
      // "Hierarchical" package view.
      addPackagesAsTree(packages, children);
    }
    return children;
  }

  private void addPackagesAsFlatList(@NotNull Collection<ApkPackage> packages, @NotNull List<AbstractTreeNode> children) {
    for (ApkPackage apkPackage : packages) {
      boolean hideEmptyMiddlePackages = getSettings().isHideEmptyMiddlePackages();
      if (!hideEmptyMiddlePackages || !apkPackage.getClasses().isEmpty()) {
        children.add(createNode(apkPackage));
      }
      addPackagesAsFlatList(apkPackage.getSubpackages(), children);
    }
  }

  private void addPackagesAsTree(@NotNull Collection<ApkPackage> packages, @NotNull List<AbstractTreeNode> children) {
    if (getSettings().isHideEmptyMiddlePackages()) {
      for (ApkPackage apkPackage : packages) {
        if (!apkPackage.getClasses().isEmpty() || apkPackage.doSubpackagesHaveClasses()) {
          children.add(createNode(apkPackage));
        }
        else {
          addPackagesAsTree(apkPackage.getSubpackages(), children);
        }
      }
    }
    else {
      for (ApkPackage apkPackage : packages) {
        children.add(createNode(apkPackage));
      }
    }
  }

  @NotNull
  private PackageNode createNode(ApkPackage apkPackage) {
    assert myProject != null;
    return new PackageNode(myProject, apkPackage, getSettings(), myJavaFiles, mySmaliFiles);
  }

  @Override
  public boolean contains(@NotNull VirtualFile file) {
    if (myDexFileContents != null) {
      if (isJavaFile(file)) {
        assert myProject != null;
        String foundPackage = myJavaFiles.findPackage(file, myProject);
        for (ApkPackage apkPackage : myPackages) {
          if (foundPackage != null && foundPackage.contains(apkPackage.getFqn())) {
            return true;
          }
        }
      }
      else if (isSmaliFile(file)) {
        File filePath = virtualToIoFile(file);
        for (ApkPackage apkPackage : myPackages) {
          File packageFilePath = mySmaliFiles.findPackageFilePath(apkPackage.getFqn());
          if (isAncestor(packageFilePath, filePath, false)) {
            return true;
          }
        }
      }
    }
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
