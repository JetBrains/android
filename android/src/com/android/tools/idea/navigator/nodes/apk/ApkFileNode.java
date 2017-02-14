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

import com.android.tools.idea.apk.ApkFacet;
import com.android.tools.idea.apk.ApkFileType;
import com.android.tools.idea.navigator.AndroidProjectTreeBuilder;
import com.android.tools.idea.navigator.AndroidProjectViewPane;
import com.android.tools.idea.navigator.nodes.android.AndroidManifestsGroupNode;
import com.intellij.ide.projectView.PresentationData;
import com.intellij.ide.projectView.ProjectViewNode;
import com.intellij.ide.projectView.ViewSettings;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

import static com.android.SdkConstants.FN_ANDROID_MANIFEST_XML;
import static com.android.tools.idea.gradle.util.Projects.findModuleRootFolder;

public class ApkFileNode extends ProjectViewNode<PsiFile> {
  @NotNull private final AndroidFacet myAndroidFacet;
  @NotNull private final ApkFacet myApkFacet;
  @NotNull private final AndroidProjectViewPane myProjectViewPane;

  @Nullable private final VirtualFile myManifestFile;

  public ApkFileNode(@NotNull Project project,
                     @NotNull PsiFile apkFile,
                     @NotNull AndroidFacet androidFacet,
                     @NotNull ApkFacet apkFacet,
                     @NotNull ViewSettings settings,
                     @NotNull AndroidProjectViewPane projectViewPane) {
    super(project, apkFile, settings);
    myAndroidFacet = androidFacet;
    myApkFacet = apkFacet;
    myProjectViewPane = projectViewPane;
    VirtualFile rootFolder = findModuleRootFolder(getModule());
    myManifestFile = rootFolder != null ? rootFolder.findChild(FN_ANDROID_MANIFEST_XML) : null;
  }

  @Override
  @NotNull
  public Collection<? extends AbstractTreeNode> getChildren() {
    Collection<AbstractTreeNode> children = new ArrayList<>();
    AndroidManifestsGroupNode manifestsGroupNode = createManifestGroupNode();
    children.add(manifestsGroupNode);
    return children;
  }

  @NotNull
  private AndroidManifestsGroupNode createManifestGroupNode() {
    assert myProject != null;
    Set<VirtualFile> manifestFiles = myManifestFile != null ? Collections.singleton(myManifestFile) : Collections.emptySet();
    return new AndroidManifestsGroupNode(myProject, myAndroidFacet, getSettings(), manifestFiles);
  }

  @NotNull
  private AndroidProjectTreeBuilder getTreeBuilder() {
    return (AndroidProjectTreeBuilder)myProjectViewPane.getTreeBuilder();
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
  public Comparable getSortKey() {
    return getTypeSortKey();
  }

  @Override
  public boolean contains(@NotNull VirtualFile file) {
    // This is needed when user selects option "Autoscroll from Source". When user selects manifest in editor, the manifest must be selected
    // automatically in the "Android" view.
    if (Objects.equals(file.getPath(), getManifestPath())) {
      return true;
    }
    return false;
  }

  @Nullable
  private String getManifestPath() {
    return myManifestFile != null ? myManifestFile.getPath() : null;
  }

  @Override
  @Nullable
  public Comparable getTypeSortKey() {
    return getModule().getName();
  }

  @NotNull
  private Module getModule() {
    return myApkFacet.getModule();
  }

  @Override
  protected void update(@NotNull PresentationData presentation) {
    presentation.setIcon(ApkFileType.INSTANCE.getIcon());
    presentation.setPresentableText(getApkFile().getName());
  }

  @Override
  @Nullable
  public VirtualFile getVirtualFile() {
    return getApkFile().getVirtualFile();
  }

  @NotNull
  private PsiFile getApkFile() {
    PsiFile file = getValue();
    assert file != null;
    return file;
  }
}
