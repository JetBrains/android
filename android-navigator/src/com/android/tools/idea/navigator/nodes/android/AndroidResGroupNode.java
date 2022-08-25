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
package com.android.tools.idea.navigator.nodes.android;

import static com.intellij.ui.SimpleTextAttributes.GRAY_ATTRIBUTES;
import static com.intellij.ui.SimpleTextAttributes.REGULAR_ATTRIBUTES;

import com.android.ide.common.resources.configuration.FolderConfiguration;
import com.android.tools.idea.navigator.nodes.FileGroupNode;
import com.android.tools.idea.navigator.nodes.FolderGroupNode;
import com.android.tools.idea.navigator.nodes.GroupNodes;
import com.intellij.ide.projectView.PresentationData;
import com.intellij.ide.projectView.ProjectViewNode;
import com.intellij.ide.projectView.ViewSettings;
import com.intellij.ide.projectView.impl.nodes.PsiFileNode;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Queryable;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiFile;
import com.intellij.ui.IconManager;
import com.intellij.ui.PlatformIcons;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * {@link AndroidResGroupNode} groups together all the configuration specific alternatives of a single resource.
 */
@SuppressWarnings("ComparableType")  // b/180537631
public class AndroidResGroupNode extends ProjectViewNode<List<PsiFile>> implements FolderGroupNode, FileGroupNode, Comparable {
  @NotNull private final String myResName;
  @NotNull private final AndroidFacet myFacet;
  @NotNull private final List<PsiFile> myFiles;

  AndroidResGroupNode(@NotNull Project project,
                      @NotNull AndroidFacet androidFacet,
                      @NotNull List<PsiFile> files,
                      @NotNull String resName,
                      @NotNull ViewSettings settings) {
    super(project, files, settings);
    myResName = resName;
    myFacet = androidFacet;
    myFiles = files;
  }

  @Override
  @NotNull
  public List<PsiDirectory> getFolders() {
    List<PsiFile> resFiles = getResFiles();
    List<PsiDirectory> folders = new ArrayList<>(resFiles.size());
    for (PsiFile file : resFiles) {
      folders.add(file.getParent());
    }
    return folders;
  }

  @Override
  @NotNull
  public List<PsiFile> getFiles() {
    return getResFiles();
  }

  @NotNull
  private List<PsiFile> getResFiles() {
    List<PsiFile> files = getValue();
    assert files != null;
    return files;
  }

  @Override
  public boolean contains(@NotNull VirtualFile file) {
    for (PsiFile psiFile : myFiles) {
      if (psiFile.getVirtualFile().equals(file)) {
        return true;
      }
    }
    return false;
  }

  @Override
  public boolean canRepresent(Object element) {
    return GroupNodes.canRepresent((FileGroupNode)this, element) || GroupNodes.canRepresent((FolderGroupNode)this, element);
  }

  @Override
  @NotNull
  public Collection<? extends AbstractTreeNode<?>> getChildren() {
    List<PsiFileNode> children = new ArrayList<>(myFiles.size());
    assert myProject != null;
    for (PsiFile file : myFiles) {
      children.add(new AndroidResFileNode(myProject, file, getSettings(), myFacet));
    }
    return children;
  }

  @Override
  public int getWeight() {
    return 20; // same as PsiFileNode so that res group nodes are compared with resources only alphabetically
  }

  @Override
  @Nullable
  public Comparable getSortKey() {
    return this;
  }

  @Override
  @Nullable
  public Comparable getTypeSortKey() {
    return this;
  }

  @Override
  public int compareTo(@NotNull Object obj) {
    return AndroidResComparator.INSTANCE.compare(this, obj);
  }

  @NotNull
  public String getResName() {
    return myResName;
  }

  @Override
  public boolean expandOnDoubleClick() {
    return false;
  }

  @Override
  public boolean canNavigate() {
    return true;
  }

  @Override
  public void navigate(boolean requestFocus) {
    if (!myFiles.isEmpty()) {
      PsiFile fileToOpen = findFileToOpen(myFiles);
      if (fileToOpen != null) {
        assert myProject != null;
        new OpenFileDescriptor(myProject, fileToOpen.getVirtualFile()).navigate(requestFocus);
      }
    }
  }

  /**
   * Returns the best configuration of a particular resource given a set of multiple configurations of the same resource.
   */
  @Nullable
  private static PsiFile findFileToOpen(@NotNull List<PsiFile> files) {
    PsiFile bestFile = null;
    FolderConfiguration bestConfig = null;

    for (PsiFile file : files) {
      PsiDirectory qualifiedDirectory = file.getParent();
      assert qualifiedDirectory != null : "Resource file's parent directory cannot be null";
      FolderConfiguration config = FolderConfiguration.getConfigForFolder(qualifiedDirectory.getName());

      if (bestConfig == null // first time through the loop
          || config == null  // FolderConfiguration is null for default configurations
          || config.compareTo(bestConfig) < 0) { // lower FolderConfiguration value than current best
        bestConfig = config;
        bestFile = file;
      }
    }

    return bestFile;
  }

  @Override
  protected void update(@NotNull PresentationData presentation) {
    presentation.setPresentableText(myResName);
    presentation.addText(myResName, REGULAR_ATTRIBUTES);
    if (myFiles.size() > 1) {
      presentation.addText(String.format(Locale.US, " (%1$d)", myFiles.size()), GRAY_ATTRIBUTES);
    }
    presentation.setIcon(IconManager.getInstance().getPlatformIcon(PlatformIcons.Package));
  }

  @Override
  @Nullable
  public String toTestString(@Nullable Queryable.PrintInfo printInfo) {
    return myResName + " (" + myFiles.size() + ")";
  }
}
