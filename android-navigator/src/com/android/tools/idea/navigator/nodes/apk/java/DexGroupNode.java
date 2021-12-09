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

import static com.intellij.icons.AllIcons.Modules.SourceRoot;
import static com.intellij.openapi.util.io.FileUtil.isAncestor;
import static com.intellij.openapi.vfs.VfsUtilCore.virtualToIoFile;
import static com.intellij.ui.SimpleTextAttributes.GRAY_ATTRIBUTES;
import static com.intellij.ui.SimpleTextAttributes.REGULAR_ATTRIBUTES;

import com.android.tools.idea.apk.debugging.ApkPackage;
import com.android.tools.idea.apk.debugging.DexSourceFiles;
import com.intellij.ide.projectView.PresentationData;
import com.intellij.ide.projectView.ProjectViewNode;
import com.intellij.ide.projectView.ViewSettings;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Queryable;
import com.intellij.openapi.vfs.VirtualFile;
import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;
import org.jetbrains.android.facet.AndroidSourceType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A node in the file tree that represents the merged contents of one or more
 * dex files.
 */
public class DexGroupNode extends ProjectViewNode<DexGroupNode.DexGroupElement> {

  /**
   * A DexGroupNode may correspond to more than one classes.dex file, we use
   * this DexGroupElement object as the tree node type.
   */
  public static class DexGroupElement {
    // Empty.
  }

  @NotNull Project myProject;

  // All dex files represented by this node.
  @NotNull List<VirtualFile> myDexFiles;

  // Helper class for various dex/smali/java file operations.
  @NotNull private final DexSourceFiles myDexSourceFiles;

  // Represents the file hierarchy under this node.
  @NotNull private final DexFileStructure myDexFileStructure;

  // All known Java/Kotlin packages inside this node, coming from all classes.dex files.
  // Use {@link getPackages} to populate its contents lazily.
  @Nullable private Collection<ApkPackage> myPackages;

  public DexGroupNode(
    @NotNull Project project,
    @NotNull ViewSettings settings,
    @NotNull List<VirtualFile> dexFiles) {
    this(project, settings, DexSourceFiles.getInstance(project), dexFiles);
  }

  DexGroupNode(@NotNull Project project,
               @NotNull ViewSettings settings,
               @NotNull DexSourceFiles dexSourceFiles,
               @NotNull List<VirtualFile> dexFiles) {
    super(project, new DexGroupElement(), settings);
    myProject = project;
    myDexFiles = dexFiles;
    myDexSourceFiles = dexSourceFiles;
    myDexFileStructure = new DexFileStructure(dexFiles);
  }

  @Override
  @NotNull
  public Collection<? extends AbstractTreeNode<?>> getChildren() {
    try {
      return getChildren(getPackages());
    }
    catch (Throwable e) {
      Logger.getInstance(getClass()).warn("Failed to parse dex file", e);
      return Collections.emptyList();
    }
  }

  @NotNull
  private Collection<? extends AbstractTreeNode<?>> getChildren(@NotNull Collection<ApkPackage> packages) {
    List<AbstractTreeNode<?>> children = new ArrayList<>();
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

  private void addPackagesAsFlatList(@NotNull Collection<ApkPackage> packages, @NotNull List<AbstractTreeNode<?>> children) {
    for (ApkPackage apkPackage : packages) {
      boolean hideEmptyMiddlePackages = getSettings().isHideEmptyMiddlePackages();
      if (!hideEmptyMiddlePackages || !apkPackage.getClasses().isEmpty()) {
        children.add(createNode(apkPackage));
      }
      addPackagesAsFlatList(apkPackage.getSubpackages(), children);
    }
  }

  private void addPackagesAsTree(@NotNull Collection<ApkPackage> packages, @NotNull List<AbstractTreeNode<?>> children) {
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
    return new PackageNode(myProject, apkPackage, getSettings(), myDexSourceFiles);
  }

  @Override
  public boolean contains(@NotNull VirtualFile file) {
    if (myDexSourceFiles.isJavaFile(file)) {
      String foundPackage = myDexSourceFiles.findJavaPackageNameIn(file);
      for (ApkPackage apkPackage : getPackages()) {
        if (foundPackage != null && foundPackage.contains(apkPackage.getFqn())) {
          return true;
        }
      }
    }
    else if (myDexSourceFiles.isSmaliFile(file)) {
      File filePath = virtualToIoFile(file);
      for (ApkPackage apkPackage : getPackages()) {
        File packageFilePath = myDexSourceFiles.findSmaliFilePathForPackage(apkPackage.getFqn());
        if (isAncestor(packageFilePath, filePath, false)) {
          return true;
        }
      }
    }
    return false;
  }

  @Override
  protected void update(@NotNull PresentationData presentation) {
    presentation.setIcon(SourceRoot);
    presentation.addText(AndroidSourceType.JAVA.INSTANCE.getName(), REGULAR_ATTRIBUTES);
    if (myDexFiles.size() == 1) {
      // Example: java (classes.dex)
      presentation.addText(" (" + myDexFiles.get(0).getName() + ")", GRAY_ATTRIBUTES);
    }
    else {
      // Example: java (3 dex files)
      presentation.addText(" (" + myDexFiles.size() + " dex files)", GRAY_ATTRIBUTES);
    }
  }

  @Override
  @Nullable
  public Comparable<AndroidSourceType> getSortKey() {
    return getTypeSortKey();
  }

  @Override
  @Nullable
  public Comparable<AndroidSourceType> getTypeSortKey() {
    return getSourceType();
  }

  @NotNull
  private static AndroidSourceType getSourceType() {
    return AndroidSourceType.JAVA.INSTANCE;
  }

  @Override
  public boolean isAlwaysExpand() {
    return true;
  }

  @Override
  public boolean isAlwaysShowPlus() {
    return true;
  }

  @Override
  @Nullable
  public String toTestString(@Nullable Queryable.PrintInfo printInfo) {
    return getSourceType().getName() +
           " (" +
           myDexFiles.stream().map(VirtualFile::getName).collect(Collectors.joining(",")) +
           ")";
  }

  /**
   * Lazily populates and returns {@code myPackages}.
   */
  @NotNull
  private Collection<ApkPackage> getPackages() {
    if (myPackages == null) {
      try {
        myPackages = myDexFileStructure.getPackages();
      }
      catch (ExecutionException | InterruptedException e) {
        Logger.getInstance(getClass()).warn("Failed to parse dex file", e);
        myPackages = Collections.emptyList();
      }
    }

    return myPackages;
  }
}
