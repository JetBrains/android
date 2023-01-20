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
package com.android.tools.idea.navigator.nodes.apk;

import static com.android.SdkConstants.FN_ANDROID_MANIFEST_XML;
import static com.android.SdkConstants.REGEX_APK_CLASSES_DEX;
import static com.android.tools.idea.navigator.nodes.apk.SourceFolders.isInSourceFolder;
import static com.intellij.openapi.util.io.FileUtil.toSystemDependentName;
import static com.intellij.openapi.util.text.StringUtil.isNotEmpty;
import static com.intellij.openapi.vfs.VfsUtil.findFileByIoFile;
import static com.intellij.openapi.vfs.VfsUtilCore.isAncestor;

import com.android.tools.idea.apk.ApkFacet;
import com.android.tools.idea.apk.debugging.LibraryFolder;
import com.android.tools.idea.apk.viewer.ApkFileSystem;
import com.android.tools.idea.navigator.nodes.AndroidViewNodeProvider;
import com.android.tools.idea.navigator.nodes.android.AndroidManifestsGroupNode;
import com.android.tools.idea.navigator.nodes.apk.java.DexGroupNode;
import com.intellij.ide.projectView.ViewSettings;
import com.intellij.ide.projectView.impl.nodes.ProjectViewModuleNode;
import com.intellij.ide.projectView.impl.nodes.PsiFileNode;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Queryable;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.facet.AndroidRootUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ApkModuleNode extends ProjectViewModuleNode {
  @NotNull private final String myModuleName;
  @NotNull private final AndroidFacet myAndroidFacet;
  @NotNull private final ApkFacet myApkFacet;

  @Nullable private final PsiFile myApkPsiFile;
  @Nullable private final VirtualFile myApkFile;
  @Nullable private final VirtualFile myManifestFile;
  @NotNull private final List<VirtualFile> myDexFiles;

  @Nullable private DexGroupNode myDexGroupNode;

  public ApkModuleNode(@NotNull Project project,
                       @NotNull Module module,
                       @NotNull AndroidFacet androidFacet,
                       @NotNull ApkFacet apkFacet,
                       @NotNull ViewSettings settings) {
    super(project, module, settings);
    myModuleName = module.getName();
    myAndroidFacet = androidFacet;

    myApkFacet = apkFacet;
    myApkPsiFile = findApkPsiFile();
    myApkFile = myApkPsiFile != null ? myApkPsiFile.getVirtualFile() : null;
    VirtualFile apkRootFile = myApkFile != null ? ApkFileSystem.getInstance().getRootByLocal(myApkFile) : null;

    VirtualFile rootFolder = findModuleRootFolder();
    myManifestFile = rootFolder != null ? rootFolder.findChild(FN_ANDROID_MANIFEST_XML) : null;
    myDexFiles = new ArrayList<>();

    if (apkRootFile != null) {
      Pattern dexFilePattern = Pattern.compile(REGEX_APK_CLASSES_DEX);
      for (VirtualFile child : apkRootFile.getChildren()) {
        if (dexFilePattern.matcher(child.getName()).matches()) {
          myDexFiles.add(child);
        }
      }
    }
  }

  @Nullable
  private VirtualFile findModuleRootFolder() {
    File moduleRootFolderPath = AndroidRootUtil.findModuleRootFolderPath(getModule());
    if (moduleRootFolderPath == null) return null;
    return findFileByIoFile(moduleRootFolderPath, false /* do not refresh file system */);
  }

  @Nullable
  private PsiFile findApkPsiFile() {
    String apkPath = myApkFacet.getConfiguration().APK_PATH;
    if (isNotEmpty(apkPath)) {
      File apkFilePath = new File(toSystemDependentName(apkPath));
      if (apkFilePath.isFile()) {
        VirtualFile apkFile = findFileByIoFile(apkFilePath, true);
        if (apkFile != null) {
          assert myProject != null;
          return PsiManager.getInstance(myProject).findFile(apkFile);
        }
      }
    }
    return null;
  }

  @Override
  @NotNull
  public Collection<AbstractTreeNode<?>> getChildren() {
    assert myProject != null;

    ViewSettings settings = getSettings();
    List<AbstractTreeNode<?>> children = new ArrayList<>();
    if (myApkPsiFile != null) {
      children.add(new PsiFileNode(myProject, myApkPsiFile, settings));
    }
    // "manifests" folder
    children.add(createManifestGroupNode());

    // "java" folder
    if (myDexGroupNode == null) {
      myDexGroupNode = new DexGroupNode(myProject, settings, myDexFiles);
    }
    children.add(myDexGroupNode);

    children.addAll(
      AndroidViewNodeProvider.getProviders().stream()
        .flatMap(it -> {
          final var providedChildren = it.getApkModuleChildren(getModule(), settings);
          return providedChildren != null ? providedChildren.stream() : Stream.empty();
        })
        .collect(Collectors.toList())
    );
    return children;
  }

  @NotNull
  private AndroidManifestsGroupNode createManifestGroupNode() {
    assert myProject != null;
    Set<VirtualFile> manifestFiles = myManifestFile != null ? Collections.singleton(myManifestFile) : Collections.emptySet();
    return new AndroidManifestsGroupNode(myProject, myAndroidFacet, getSettings(), manifestFiles);
  }

  @Override
  @Nullable
  public Comparable<String> getSortKey() {
    return getModule().getName();
  }

  @Override
  @Nullable
  public Comparable<String> getTypeSortKey() {
    return getSortKey();
  }

  @Nullable
  @Override
  public String toTestString(@Nullable Queryable.PrintInfo printInfo) {
    return "APK Module";
  }

  @NotNull
  private Module getModule() {
    Module module = getValue();
    assert module != null;
    return module;
  }

  @Override
  public boolean contains(@NotNull VirtualFile file) {
    // This is needed when user selects option "Autoscroll from Source". When user selects manifest in editor, the manifest must be selected
    // automatically in the "Android" view.
    String path = file.getPath();
    if (myApkFile != null && Objects.equals(path, myApkFile.getPath())) {
      return true;
    }
    if (Objects.equals(path, getManifestPath())) {
      return true;
    }
    if (myDexGroupNode != null && myDexGroupNode.contains(file)) {
      return true;
    }
    VirtualFile found = LibraryFolder.findIn(myProject);
    if (found != null && isAncestor(found, file, false /* not strict */)) {
      return true;
    }

    return isInSourceFolder(file, myProject);
  }

  @Nullable
  private String getManifestPath() {
    return myManifestFile != null ? myManifestFile.getPath() : null;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof ApkModuleNode)) {
      return false;
    }
    ApkModuleNode node = (ApkModuleNode)o;
    return Objects.equals(myModuleName, node.myModuleName);
  }

  @Override
  public int hashCode() {
    return Objects.hash(super.hashCode(), myModuleName);
  }
}
