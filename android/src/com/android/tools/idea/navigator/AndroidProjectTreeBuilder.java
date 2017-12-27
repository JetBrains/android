/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.android.tools.idea.navigator;

import com.android.tools.idea.navigator.nodes.FolderGroupNode;
import com.intellij.ide.projectView.impl.ProjectAbstractTreeStructureBase;
import com.intellij.ide.projectView.impl.ProjectTreeBuilder;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.ide.util.treeView.AbstractTreeStructure;
import com.intellij.ide.util.treeView.AbstractTreeUpdater;
import com.intellij.ide.util.treeView.NodeDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.newvfs.BulkFileListener;
import com.intellij.openapi.vfs.newvfs.events.VFileDeleteEvent;
import com.intellij.openapi.vfs.newvfs.events.VFileEvent;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiFile;
import com.intellij.util.messages.MessageBusConnection;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import java.util.*;

public class AndroidProjectTreeBuilder extends ProjectTreeBuilder {
  private Map<VirtualFile, AbstractTreeNode> myFileToNodeMap = new HashMap<>();

  public AndroidProjectTreeBuilder(@NotNull Project project,
                                   @NotNull JTree tree,
                                   @NotNull DefaultTreeModel treeModel,
                                   @NotNull ProjectAbstractTreeStructureBase treeStructure,
                                   @Nullable Comparator<NodeDescriptor> comparator) {
    super(project, tree, treeModel, comparator, treeStructure);

    MessageBusConnection connection = project.getMessageBus().connect(project);
    connection.subscribe(VirtualFileManager.VFS_CHANGES, new BulkFileListener() {
      @Override
      public void after(@NotNull List<? extends VFileEvent> events) {
        for (VFileEvent e : events) {
          if (e instanceof VFileDeleteEvent) {
            removeMapping(e.getFile());
          }
        }
      }
    });
  }

  @Nullable
  @Override
  protected AbstractTreeUpdater createUpdater() {
    if (isDisposed()) {
      return null;
    }
    AbstractTreeStructure treeStructure = getTreeStructure();
    assert treeStructure != null;
    return new AndroidTreeUpdater(treeStructure, this);
  }

  @NotNull
  public Project getProject() {
    return myProject;
  }

  /**
   * Returns the tree node corresponding to a model element.
   * e.g. from a PsiDirectory -> tree node corresponding to that PsiDirectory
   *
   * When {@link com.intellij.ide.util.treeView.AbstractTreeUi} creates a {@link DefaultMutableTreeNode} for a given
   * {@link AbstractTreeNode}, it maintains a mapping between. This mapping between the model element to the tree node is necessary when
   * locating items by their model element (PsiFile or PsiDirectory).
   *
   * In the Android view, we have virtual nodes that don't correspond to Psi Elements, or a single virtual node corresponding to
   * multiple files/directories. Since such mappings aren't saved by the tree UI, these are handled in this method.
   *
   * The way this works is that every time a virtual node is created, it calls back to
   * {@link #createMapping(VirtualFile, AbstractTreeNode)}} to save that mapping between the virtual file and the node that represents it.
   * When we need to map a virtual file to its tree node, we look at the saved mapping to see if that virtual file corresponds to any node.
   * If so, we obtain the tree node corresponding to the node's parent, then iterate through its children to locate the tree node
   * corresponding to the element.
   */
  @Nullable
  @Override
  protected Object findNodeByElement(@Nullable Object element) {
    if (element == null) {
      return null;
    }

    Object node = super.findNodeByElement(element);
    if (node != null) {
      return node;
    }

    VirtualFile virtualFile = null;
    if (element instanceof PsiDirectory) {
      virtualFile = ((PsiDirectory)element).getVirtualFile();
    }
    else if (element instanceof PsiFile) {
      virtualFile = ((PsiFile)element).getVirtualFile();
    }

    if (virtualFile == null) {
      return null;
    }

    AbstractTreeNode treeNode = getNodeForFile(virtualFile);
    if (treeNode == null) {
      return null;
    }

    // recurse and find the tree node corresponding to the parent
    Object parentNode = findNodeByElement(treeNode.getParent());
    if (!(parentNode instanceof DefaultMutableTreeNode)) {
      return null;
    }

    // examine all the children of the parent tree node and return the one that maps to this virtual file.
    Enumeration children = ((DefaultMutableTreeNode)parentNode).children();
    while (children.hasMoreElements()) {
      DefaultMutableTreeNode child = (DefaultMutableTreeNode)children.nextElement();

      if (child.getUserObject() instanceof FolderGroupNode) {
        for (PsiDirectory folder : ((FolderGroupNode)child.getUserObject()).getFolders()) {
          if (folder.getVirtualFile().equals(virtualFile)) {
            return child;
          }
        }
      }
    }

    return null;
  }

  public void createMapping(@NotNull VirtualFile file, @NotNull AbstractTreeNode node) {
    myFileToNodeMap.put(file, node);
  }

  private void removeMapping(@Nullable VirtualFile file) {
    myFileToNodeMap.remove(file);
  }

  @Nullable
  private AbstractTreeNode getNodeForFile(@NotNull VirtualFile file) {
    return myFileToNodeMap.get(file);
  }
}
