/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.navigator.nodes;

import com.android.tools.idea.gradle.NativeAndroidGradleModel;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.intellij.ide.projectView.PresentationData;
import com.intellij.ide.projectView.ProjectViewNode;
import com.intellij.ide.projectView.ViewSettings;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Queryable;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiManager;
import com.intellij.ui.SimpleTextAttributes;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.io.File;
import java.util.Collection;
import java.util.List;

import static com.android.tools.idea.navigator.nodes.NativeAndroidModuleNode.getNativeSourceNodes;
import static com.intellij.openapi.vfs.VfsUtilCore.isAncestor;
import static org.jetbrains.android.facet.AndroidSourceType.JNI;

public class AndroidJniFolderNode extends ProjectViewNode<NativeAndroidGradleModel> implements DirectoryGroupNode {

  protected AndroidJniFolderNode(@NotNull Project project,
                                 @NotNull NativeAndroidGradleModel nativeAndroidModel,
                                 @NotNull ViewSettings viewSettings) {
    super(project, nativeAndroidModel, viewSettings);
  }

  @NotNull
  @Override
  public Collection<? extends AbstractTreeNode> getChildren() {
    Collection<AbstractTreeNode> nativeSourceNodes = getNativeSourceNodes(myProject, getModel(), getSettings());
    if (nativeSourceNodes.size() == 1) {
      AbstractTreeNode sourceNode = Iterables.getOnlyElement(nativeSourceNodes);
      if (sourceNode instanceof NativeAndroidSourceDirectoryNode) {
        return ((NativeAndroidSourceDirectoryNode)sourceNode).getChildren();
      }
    }
    return nativeSourceNodes;
  }

  @NotNull
  @Override
  public PsiDirectory[] getDirectories() {
    Collection<File> sourceFolders = getModel().getSelectedVariant().getSourceFolders();
    List<PsiDirectory> psiDirectories = Lists.newArrayListWithExpectedSize(sourceFolders.size());

    LocalFileSystem fileSystem = LocalFileSystem.getInstance();
    PsiManager psiManager = PsiManager.getInstance(myProject);

    for (File folder : sourceFolders) {
      VirtualFile virtualFile = fileSystem.findFileByIoFile(folder);
      if (virtualFile != null) {
        PsiDirectory dir = psiManager.findDirectory(virtualFile);
        if (dir != null) {
          psiDirectories.add(dir);
        }
      }
    }

    return psiDirectories.toArray(new PsiDirectory[psiDirectories.size()]);
  }

  @Override
  public boolean contains(@NotNull VirtualFile file) {
    Collection<File> sourceFolders = getModel().getSelectedVariant().getSourceFolders();
    LocalFileSystem fileSystem = LocalFileSystem.getInstance();

    for (File folder : sourceFolders) {
      VirtualFile virtualFile = fileSystem.findFileByIoFile(folder);
      if (virtualFile != null && isAncestor(virtualFile, file, false)) {
        return true;
      }
    }

    return false;
  }

  @Override
  protected void update(PresentationData presentation) {
    presentation.addText(JNI.getName(), SimpleTextAttributes.REGULAR_ATTRIBUTES);

    Icon icon = JNI.getIcon();
    if (icon != null) {
      presentation.setIcon(icon);
    }
    presentation.setPresentableText(JNI.getName());
  }

  @Nullable
  @Override
  public String toTestString(@Nullable Queryable.PrintInfo printInfo) {
    return JNI.getName();
  }

  @Nullable
  @Override
  public Comparable getSortKey() {
    return JNI;
  }

  @Nullable
  @Override
  public Comparable getTypeSortKey() {
    return JNI;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    if (!super.equals(o)) return false;

    AndroidJniFolderNode that = (AndroidJniFolderNode)o;
    return getValue() == that.getValue();
  }

  @Override
  public int hashCode() {
    int result = super.hashCode();
    NativeAndroidGradleModel model = getModel();
    return 31 * result + model.hashCode();
  }

  @NotNull
  private NativeAndroidGradleModel getModel() {
    NativeAndroidGradleModel value = getValue();
    assert value != null;
    return value;
  }
}
