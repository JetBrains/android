/*
 * Copyright (C) 2021 The Android Open Source Project
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

import static com.intellij.openapi.actionSystem.CommonDataKeys.PSI_ELEMENT;
import static com.intellij.openapi.actionSystem.CommonDataKeys.VIRTUAL_FILE;
import static com.intellij.openapi.actionSystem.CommonDataKeys.VIRTUAL_FILE_ARRAY;
import static com.intellij.openapi.actionSystem.PlatformDataKeys.DELETE_ELEMENT_PROVIDER;
import static com.intellij.openapi.util.io.FileUtil.filesEqual;
import static com.intellij.openapi.util.io.FileUtil.isAncestor;
import static com.intellij.openapi.vfs.VfsUtilCore.virtualToIoFile;
import static org.jetbrains.android.facet.AndroidRootUtil.findModuleRootFolderPath;

import com.android.tools.idea.IdeInfo;
import com.android.tools.idea.Projects;
import com.android.tools.idea.navigator.nodes.AndroidViewNodeProvider;
import com.android.tools.idea.navigator.nodes.AndroidViewProjectNode;
import com.android.tools.idea.navigator.nodes.FileGroupNode;
import com.android.tools.idea.navigator.nodes.FolderGroupNode;
import com.android.tools.idea.navigator.nodes.android.BuildScriptTreeStructureProvider;
import com.android.tools.idea.util.CommonAndroidUtil;
import com.intellij.facet.Facet;
import com.intellij.facet.ProjectWideFacetAdapter;
import com.intellij.facet.ProjectWideFacetListenersRegistry;
import com.intellij.ide.DeleteProvider;
import com.intellij.ide.SelectInTarget;
import com.intellij.ide.impl.ProjectViewSelectInTarget;
import com.intellij.ide.projectView.ProjectView;
import com.intellij.ide.projectView.ProjectViewSettings;
import com.intellij.ide.projectView.TreeStructureProvider;
import com.intellij.ide.projectView.ViewSettings;
import com.intellij.ide.projectView.impl.AbstractProjectViewPane;
import com.intellij.ide.projectView.impl.AbstractProjectViewPaneWithAsyncSupport;
import com.intellij.ide.projectView.impl.ProjectAbstractTreeStructureBase;
import com.intellij.ide.projectView.impl.ProjectTreeStructure;
import com.intellij.ide.projectView.impl.ProjectViewTree;
import com.intellij.ide.projectView.impl.nodes.PackageElement;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.ide.util.treeView.NodeDescriptor;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.DataSink;
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.GeneratedSourcesFilter;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileSystemItem;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.tree.TreeUtil;
import icons.StudioIcons;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.swing.Icon;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;
import org.jetbrains.annotations.VisibleForTesting;

public class AndroidProjectViewPane extends AbstractProjectViewPaneWithAsyncSupport {
  // Note: This value is duplicated in ProjectViewImpl.java to set the default view to be the Android project view.
  public static final String ID = AndroidProjectView.ID;
  public static final String PROJECT_VIEW_DEFAULT_KEY = "studio.projectview";

  private AtomicBoolean isProcessingChanges = new AtomicBoolean(false);

  public AndroidProjectViewPane(Project project) {
    super(project);
    ProjectWideFacetListenersRegistry.getInstance(project).registerListener(new ProjectWideFacetAdapter<Facet>() {
      @Override
      public void facetAdded(@NotNull Facet facet) {
        somethingChanged();
      }

      @Override
      public void facetRemoved(@NotNull Facet facet) {
        somethingChanged();
      }

      private void somethingChanged() {
        if (!isProcessingChanges.getAndSet(true)) {
          // Wait until other actions are over, in particular wait for all facets to be added.
          ApplicationManager.getApplication().invokeLater(() -> {
            try {
              if (project.isDisposed()) return;
              ProjectView projectView = ProjectView.getInstance(project);
              AbstractProjectViewPane pane = projectView.getProjectViewPaneById(ID);
              boolean visible = isInitiallyVisible();
              if (visible && pane == null) {
                projectView.addProjectPane(AndroidProjectViewPane.this);
              }
              else if (!visible && pane != null) {
                projectView.removeProjectPane(pane);
              }
            }
            finally {
              isProcessingChanges.set(false);
            }
          }, project.getDisposed());
        }
      }
    });
  }

  @NotNull
  @Override
  public String getTitle() {
    return "Android";
  }

  @NotNull
  @Override
  public Icon getIcon() {
    return StudioIcons.Common.ANDROID_HEAD;
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

  @Override
  public boolean isInitiallyVisible() {
    return CommonAndroidUtil.getInstance().isAndroidProject(myProject);
  }

  @NotNull
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

      @Override
      protected boolean canSelect(PsiFileSystemItem file) {
        if (super.canSelect(file)) return true;
        return AndroidViewNodeProvider.getProviders().stream()
          .anyMatch(it -> it.projectContainsExternalFile(myProject, file.getVirtualFile()));
      }
    };
  }

  @NotNull
  @Override
  protected ProjectAbstractTreeStructureBase createStructure() {
    return new AndroidProjectTreeStructure(myProject, ID);
  }

  @NotNull
  @Override
  protected ProjectViewTree createTree(@NotNull final DefaultTreeModel treeModel) {
    return new MyProjectViewTree(treeModel);
  }

  @Override
  public PsiDirectory @NotNull [] getSelectedDirectories() {
    TreePath[] paths = getSelectionPaths();
    Object selectedElement = null;
    if (paths != null) {
      Object[] selectedElements = ContainerUtil.map2Array(paths, TreeUtil::getLastUserObject);
      if (selectedElements.length == 1) {
        selectedElement = selectedElements[0];
      }
    }
    if (selectedElement instanceof PackageElement packageElement) {
      Module m = packageElement.getModule();
      if (m != null) {
        return packageElement.getPackage().getDirectories(GlobalSearchScope.moduleScope(m));
      }
    }

    NodeDescriptor descriptor = TreeUtil.getLastUserObject(NodeDescriptor.class, getSelectedPath());
    if (descriptor instanceof FolderGroupNode) {
      return ((FolderGroupNode)descriptor).getFolders().toArray(PsiDirectory.EMPTY_ARRAY);
    }

    PsiDirectory[] selectedDirectories = super.getSelectedDirectories();
    // For modules we'll include generated folders too, but we don't want
    // to treat these as selectable (for target output directories etc)
    if (selectedElement instanceof Module && selectedDirectories.length > 0) {
      List<PsiDirectory> dirs = new ArrayList<>(selectedDirectories.length);
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
      selectedDirectories = dirs.toArray(PsiDirectory.EMPTY_ARRAY);
    }

    return selectedDirectories;
  }

  @Nullable
  @Override
  public Object getValueFromNode(@Nullable Object node) {
    Object o = super.getValueFromNode(node);
    if (o instanceof ArrayList && TreeUtil.getUserObject(node) instanceof FolderGroupNode) {
      return ((ArrayList)o).toArray();
    }

    return o;
  }

  @Override
  protected void uiDataSnapshotForSelection(@NotNull DataSink sink,
                                            Object @NotNull [] selectedUserObjects,
                                            @Nullable Object[] singleSelectedPathUserObjects) {
    super.uiDataSnapshotForSelection(sink, selectedUserObjects, singleSelectedPathUserObjects);

    sink.lazy(DELETE_ELEMENT_PROVIDER, () -> {
      Object o = (selectedUserObjects.length != 1) ? null : getValueFromNode(selectedUserObjects[0]);
      if (o instanceof PsiDirectory) {
        VirtualFile directory = ((PsiDirectory)o).getVirtualFile();
        // Do not allow folder to be deleted if the folder is the root project folder.
        // See https://code.google.com/p/android/issues/detail?id=212522
        if (isTopModuleDirectoryOrParent(directory)) {
          return new NoOpDeleteProvider();
        }
      }
      return null;
    });

    sink.lazy(PlatformCoreDataKeys.MODULE, () -> {
      Object value = (selectedUserObjects.length != 1) ? null : getValueFromNode(selectedUserObjects[0]);
      return value instanceof PackageElement o ? o.getModule() :
             value instanceof AndroidFacet o ? o.getModule() :
             null;
    });

    sink.lazy(VIRTUAL_FILE, () -> {
      Object value = (selectedUserObjects.length != 1) ? null : getValueFromNode(selectedUserObjects[0]);
      if (!(value instanceof PackageElement packageElement)) {
        return null;
      }
      Module m = packageElement.getModule();
      if (m == null) return null;
      PsiDirectory[] folders = packageElement.getPackage().getDirectories(GlobalSearchScope.moduleScope(m));
      if (folders.length > 0) {
        return folders[0].getVirtualFile();
      }
      else {
        return null;
      }
    });

    sink.lazy(VIRTUAL_FILE_ARRAY, () -> {
      NodeDescriptor selectedDescriptor =
        (selectedUserObjects.length != 1) ? null : ObjectUtils.tryCast(selectedUserObjects[0], NodeDescriptor.class);
      if (selectedDescriptor instanceof FileGroupNode) {
        List<PsiFile> files = ((FileGroupNode)selectedDescriptor).getFiles();
        if (!files.isEmpty()) {
          List<VirtualFile> virtualFiles = new ArrayList<>(files.size());
          for (PsiFile file : files) {
            if (file.isValid()) {
              virtualFiles.add(file.getVirtualFile());
            }
          }
          return virtualFiles.toArray(VirtualFile.EMPTY_ARRAY);
        }
      }

      if (selectedDescriptor instanceof FolderGroupNode) {
        List<PsiDirectory> directories = ((FolderGroupNode)selectedDescriptor).getFolders();
        if (!directories.isEmpty()) {
          List<VirtualFile> virtualFiles = new ArrayList<>(directories.size());
          for (PsiDirectory directory : directories) {
            if (directory.isValid()) {
              virtualFiles.add(directory.getVirtualFile());
            }
          }
          return virtualFiles.toArray(VirtualFile.EMPTY_ARRAY);
        }
      }
      return null;
    });

    sink.lazy(PSI_ELEMENT, () -> {
      Object value = (selectedUserObjects.length != 1) ? null : getValueFromNode(selectedUserObjects[0]);
      if (value instanceof PsiElement o) {
        return o;
      }
      else if (value instanceof List<?> l) {
        if (!l.isEmpty() && l.get(0) instanceof PsiElement o) {
          return o;
        }
      }

      NodeDescriptor<?> selectedDescriptor =
        (selectedUserObjects.length != 1) ? null : ObjectUtils.tryCast(selectedUserObjects[0], NodeDescriptor.class);
      if (selectedDescriptor instanceof FileGroupNode o) {
        List<PsiFile> files = o.getFiles();
        if (!files.isEmpty()) {
          return files.get(0);
        }
      }

      if (selectedDescriptor instanceof FolderGroupNode o) {
        List<PsiDirectory> directories = o.getFolders();
        if (!directories.isEmpty()) {
          return directories.get(0);
        }
      }
      return null;
    });
  }

  @Override
  public boolean isDefaultPane(@NotNull Project project) {
    return isDefaultPane(project, IdeInfo.getInstance());
  }

  @VisibleForTesting
  boolean isDefaultPane(@NotNull Project project, @NotNull IdeInfo ideInfo) {
    if ((!ideInfo.isAndroidStudio()) && (!ideInfo.isGameTools())) {
      return super.isDefaultPane(project);
    }
    return !Boolean.getBoolean(PROJECT_VIEW_DEFAULT_KEY);
  }

  private boolean isTopModuleDirectoryOrParent(@NotNull VirtualFile directory) {
    for (Module module : ModuleManager.getInstance(myProject).getModules()) {
      File moduleRootFolderPath = findModuleRootFolderPath(module);
      if (moduleRootFolderPath == null) {
        continue;
      }
      File baseDirPath = Projects.getBaseDirPath(myProject);
      if (filesEqual(moduleRootFolderPath, baseDirPath)) {
        // This is the project module. Don't allow to delete.
        File directoryPath = virtualToIoFile(directory);
        return isAncestor(directoryPath, baseDirPath, false);
      }
    }
    return false;
  }

  private class MyProjectViewTree extends ProjectViewTree {
    MyProjectViewTree(DefaultTreeModel treeModel) {
      super(treeModel);
    }

    @Override
    public void uiDataSnapshot(@NotNull DataSink sink) {
      super.uiDataSnapshot(sink);
      DataSink.uiDataSnapshot(sink, AndroidProjectViewPane.this);
    }
  }

  // This class is used to prevent deleting folders that are actually the root project.
  // See: https://code.google.com/p/android/issues/detail?id=212522
  private static class NoOpDeleteProvider implements DeleteProvider {
    @NotNull
    @Override
    public ActionUpdateThread getActionUpdateThread() {
      return ActionUpdateThread.BGT;
    }

    @Override
    public void deleteElement(@NotNull DataContext dataContext) {
    }

    @Override
    public boolean canDeleteElement(@NotNull DataContext dataContext) {
      return false;
    }
  }

  private static class AndroidProjectTreeStructure extends ProjectTreeStructure implements ProjectViewSettings {

    AndroidProjectTreeStructure(@NotNull Project project, @NotNull String panelId) {
      super(project, panelId);
    }

    @Override
    @Unmodifiable
    public List<TreeStructureProvider> getProviders() {
      List<TreeStructureProvider> providers = super.getProviders();
      if (providers == null) {
        return null;
      }
      return ContainerUtil.map(providers, provider -> new BuildScriptTreeStructureProvider(provider));
    }

    @Override
    protected AbstractTreeNode createRoot(@NotNull Project project, @NotNull ViewSettings settings) {
      return new AndroidViewProjectNode(project, settings);
    }
  }
}
