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

import com.android.tools.idea.navigator.nodes.AndroidViewProjectNode;
import com.android.tools.idea.navigator.nodes.DirectoryGroupNode;
import com.android.tools.idea.navigator.nodes.FileGroupNode;
import com.google.common.collect.Lists;
import com.intellij.ide.SelectInTarget;
import com.intellij.ide.impl.ProjectPaneSelectInTarget;
import com.intellij.ide.impl.ProjectViewSelectInTarget;
import com.intellij.ide.projectView.BaseProjectTreeBuilder;
import com.intellij.ide.projectView.ViewSettings;
import com.intellij.ide.projectView.impl.AbstractProjectViewPSIPane;
import com.intellij.ide.projectView.impl.ProjectAbstractTreeStructureBase;
import com.intellij.ide.projectView.impl.ProjectTreeStructure;
import com.intellij.ide.projectView.impl.ProjectViewTree;
import com.intellij.ide.projectView.impl.nodes.PackageElement;
import com.intellij.ide.util.treeView.AbstractTreeBuilder;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.ide.util.treeView.AbstractTreeUpdater;
import com.intellij.ide.util.treeView.NodeDescriptor;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.GeneratedSourcesFilter;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.search.GlobalSearchScope;
import icons.AndroidIcons;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.facet.IdeaSourceProvider;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import java.util.ArrayList;
import java.util.List;

public class AndroidProjectViewPane extends AbstractProjectViewPSIPane {
  // Note: This value is duplicated in ProjectViewImpl.java to set the default view to be the Android project view.
  public static final String ID = "AndroidView";

  public AndroidProjectViewPane(Project project) {
    super(project);
  }

  @Override
  public String getTitle() {
    return "Android";
  }

  @Override
  public Icon getIcon() {
    return AndroidIcons.Android;
  }

  @NotNull
  @Override
  public String getId() {
    return ID;
  }

  @Override
  public int getWeight() {
    // used for sorting the sequence of panes, but the weight cannot match any existing pane's weight
    // IDEA's panes start with 0 (project view pane) and go up (1 for package view, favorites seems to use 4, ..)
    return 142;
  }

  @NotNull
  public static List<IdeaSourceProvider> getSourceProviders(@NotNull AndroidFacet facet) {
    List<IdeaSourceProvider> sourceProviders = IdeaSourceProvider.getCurrentSourceProviders(facet);
    sourceProviders.addAll(IdeaSourceProvider.getCurrentTestSourceProviders(facet));
    return sourceProviders;
  }

  @Override
  public SelectInTarget createSelectInTarget() {
    return new ProjectViewSelectInTarget(myProject) {
      @Override
      public String toString() {
        return getTitle();
      }

      @Override
      public String getMinorViewId() {
        return getId();
      }

      @Override
      public float getWeight() {
        return AndroidProjectViewPane.this.getWeight();
      }
    };
  }

  @Override
  protected ProjectAbstractTreeStructureBase createStructure() {
    return new ProjectTreeStructure(myProject, ID) {
      @Override
      protected AbstractTreeNode createRoot(Project project, ViewSettings settings) {
        return new AndroidViewProjectNode(project, settings, AndroidProjectViewPane.this);
      }
    };
  }

  @NotNull
  @Override
  protected BaseProjectTreeBuilder createBuilder(DefaultTreeModel treeModel) {
    return new AndroidProjectTreeBuilder(myProject, myTree, treeModel, null, (ProjectAbstractTreeStructureBase)myTreeStructure);
  }

  @Override
  protected ProjectViewTree createTree(final DefaultTreeModel treeModel) {
    return new MyProjectViewTree(treeModel);
  }

  @Override
  protected AbstractTreeUpdater createTreeUpdater(AbstractTreeBuilder treeBuilder) {
    return new AbstractTreeUpdater(treeBuilder);
  }

  @Override
  public PsiDirectory[] getSelectedDirectories() {
    Object selectedElement = getSelectedElement();
    if (selectedElement instanceof PackageElement) {
      PackageElement packageElement = (PackageElement)selectedElement;
      Module m = packageElement.getModule();
      if (m != null) {
        return packageElement.getPackage().getDirectories(GlobalSearchScope.moduleScope(m));
      }
    }

    NodeDescriptor descriptor = getSelectedDescriptor();
    if (descriptor instanceof DirectoryGroupNode) {
      return ((DirectoryGroupNode)descriptor).getDirectories();
    }

    PsiDirectory[] selectedDirectories = super.getSelectedDirectories();
    // For modules we'll include generated folders too but we don't want
    // to treat these as selectable (for target output directories etc)
    if (selectedElement instanceof Module && selectedDirectories.length > 0) {
      List<PsiDirectory> dirs = Lists.newArrayListWithExpectedSize(selectedDirectories.length);
      for (PsiDirectory dir : selectedDirectories) {
        VirtualFile file = dir.getVirtualFile();
        if (!GeneratedSourcesFilter.isGeneratedSourceByAnyFilter(file, myProject)) {
          if (file.getParent() != null && file.getPath().contains("/generated/")) {
            // Workaround for https://code.google.com/p/android/issues/detail?id=79843:
            // The generated resource folders (e.g. for RenderScript) cannot be marked as generated, e.g.
            /// build/generated/res/rs/debug, build/generated/res/rs/test/debug, etc.
            continue;
          }
          dirs.add(dir);
        }
      }
      selectedDirectories = dirs.toArray(new PsiDirectory[dirs.size()]);
    }

    return selectedDirectories;
  }

  @Override
  protected Object exhumeElementFromNode(DefaultMutableTreeNode node) {
    Object o = super.exhumeElementFromNode(node);
    if (o instanceof ArrayList && node.getUserObject() instanceof DirectoryGroupNode) {
      return ((ArrayList)o).toArray();
    }

    return o;
  }

  @Override
  public Object getData(String dataId) {
    if (CommonDataKeys.PROJECT.is(dataId)) {
      return myProject;
    }

    if (LangDataKeys.MODULE.is(dataId)) {
      Object o = getSelectedElement();
      if (o instanceof PackageElement) {
        PackageElement packageElement = (PackageElement)o;
        return packageElement.getModule();
      } else if (o instanceof AndroidFacet) {
        return ((AndroidFacet)o).getModule();
      }
    }

    if (CommonDataKeys.VIRTUAL_FILE.is(dataId)) {
      Object o = getSelectedElement();
      if (o instanceof PackageElement) {
        PackageElement packageElement = (PackageElement)o;
        Module m = packageElement.getModule();
        if (m != null) {
          PsiDirectory[] folders = packageElement.getPackage().getDirectories(GlobalSearchScope.moduleScope(m));
          if (folders.length > 0) {
            return folders[0].getVirtualFile();
          } else {
            return null;
          }
        }
      }
    }

    if (CommonDataKeys.VIRTUAL_FILE_ARRAY.is(dataId)) {
      NodeDescriptor selectedDescriptor = getSelectedDescriptor();
      if (selectedDescriptor instanceof FileGroupNode) {
        PsiFile[] files = ((FileGroupNode)selectedDescriptor).getFiles();
        if (files.length > 0) {
          List<VirtualFile> virtualFiles = Lists.newArrayListWithExpectedSize(files.length);
          for (PsiFile file : files) {
            if (file.isValid()) {
              virtualFiles.add(file.getVirtualFile());
            }
          }
          return virtualFiles.toArray(new VirtualFile[virtualFiles.size()]);
        }
      }

      if (selectedDescriptor instanceof DirectoryGroupNode) {
        PsiDirectory[] directories = ((DirectoryGroupNode)selectedDescriptor).getDirectories();
        if (directories.length > 0) {
          List<VirtualFile> virtualFiles = Lists.newArrayListWithExpectedSize(directories.length);
          for (PsiDirectory directory : directories) {
            if (directory.isValid()) {
              virtualFiles.add(directory.getVirtualFile());
            }
          }
          return virtualFiles.toArray(new VirtualFile[virtualFiles.size()]);
        }
      }
    }

    if (CommonDataKeys.PSI_ELEMENT.is(dataId)) {
      Object o = getSelectedElement();
      if (o instanceof PsiElement) {
        return o;
      } else if (o instanceof List<?>) {
        List<?> l = (List<?>) o;
        if (!l.isEmpty() && l.get(0) instanceof PsiElement) {
          return l.get(0);
        }
      }

      NodeDescriptor selectedDescriptor = getSelectedDescriptor();
      if (selectedDescriptor instanceof FileGroupNode) {
        PsiFile[] files = ((FileGroupNode)selectedDescriptor).getFiles();
        if (files.length > 0) {
          return files[0];
        }
      }

      if (selectedDescriptor instanceof DirectoryGroupNode) {
        PsiDirectory[] directories = ((DirectoryGroupNode)selectedDescriptor).getDirectories();
        if (directories.length > 0) {
          return directories[0];
        }
      }
    }

    return super.getData(dataId);
  }

  private class MyProjectViewTree extends ProjectViewTree implements DataProvider {
    public MyProjectViewTree(DefaultTreeModel treeModel) {
      super(AndroidProjectViewPane.this.myProject, treeModel);
    }

    @Override
    public DefaultMutableTreeNode getSelectedNode() {
      return AndroidProjectViewPane.this.getSelectedNode();
    }

    @Nullable
    @Override
    public Object getData(@NonNls String dataId) {
      return AndroidProjectViewPane.this.getData(dataId);
    }
  }
}
