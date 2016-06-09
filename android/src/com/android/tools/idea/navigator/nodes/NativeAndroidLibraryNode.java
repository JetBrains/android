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

import com.android.builder.model.NativeArtifact;
import com.android.builder.model.NativeFile;
import com.android.builder.model.NativeFolder;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.intellij.icons.AllIcons;
import com.intellij.ide.projectView.PresentationData;
import com.intellij.ide.projectView.ProjectViewNode;
import com.intellij.ide.projectView.ViewSettings;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Queryable;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiManager;
import com.intellij.ui.SimpleTextAttributes;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.TreeMap;

import static com.intellij.openapi.util.io.FileUtil.getNameWithoutExtension;

public class NativeAndroidLibraryNode extends ProjectViewNode<Collection<NativeArtifact>> implements DirectoryGroupNode {
  @NotNull private final String myNativeLibraryName;
  @NotNull private final String myNativeLibraryType;
  @NotNull private final Collection<String> myFileExtensions;

  public NativeAndroidLibraryNode(@NotNull Project project,
                                  @NotNull String nativeLibraryName,
                                  @NotNull String nativeLibraryType,
                                  @NotNull Collection<NativeArtifact> artifacts,
                                  @NotNull ViewSettings settings,
                                  @NotNull Collection<String> fileExtensions) {
    super(project, artifacts, settings);
    myNativeLibraryName = nativeLibraryName;
    myNativeLibraryType = nativeLibraryType;
    myFileExtensions = fileExtensions;
  }

  @NotNull
  public static Collection<AbstractTreeNode> getSourceDirectoryNodes(@NotNull Project project,
                                                                     @NotNull Collection<NativeArtifact> artifacts,
                                                                     @NotNull ViewSettings settings,
                                                                     @NotNull Collection<String> fileExtensions) {
    TreeMap<String, RootDirectory> rootDirectories = new TreeMap<>();

    for (NativeArtifact artifact : artifacts) {
      addSourceFolders(rootDirectories, artifact);
      addSourceFiles(rootDirectories, artifact);
    }

    if (rootDirectories.size() > 1) {
      groupDirectories(rootDirectories);
    }

    if (rootDirectories.size() > 1) {
      mergeDirectories(rootDirectories);
    }

    PsiManager psiManager = PsiManager.getInstance(project);
    List<AbstractTreeNode> children = Lists.newArrayList();
    for (RootDirectory rootDirectory : rootDirectories.values()) {
      PsiDirectory psiDir = psiManager.findDirectory(rootDirectory.rootDir);
      if (psiDir != null) {
        children.add(new NativeAndroidSourceDirectoryNode(project, psiDir, settings, fileExtensions, rootDirectory.sourceFolders,
                                                          rootDirectory.sourceFiles));
      }
    }
    return children;
  }

  private static void addSourceFolders(TreeMap<String, RootDirectory> rootDirectories, NativeArtifact artifact) {
    for (VirtualFile sourceFolder : getSourceFolders(artifact)) {
      String path = sourceFolder.getPath();
      if (rootDirectories.containsKey(path)) {
        continue;
      }
      RootDirectory rootDirectory = new RootDirectory(sourceFolder);
      rootDirectory.sourceFolders.add(sourceFolder);
      rootDirectories.put(path, rootDirectory);
    }
  }

  @NotNull
  private static List<VirtualFile> getSourceFolders(@NotNull NativeArtifact artifact) {
    List<File> sourceFolders = Lists.newArrayList();
    for (File headerRoot : artifact.getExportedHeaders()) {
      sourceFolders.add(headerRoot);
    }
    for (NativeFolder sourceFolder : artifact.getSourceFolders()) {
      sourceFolders.add(sourceFolder.getFolderPath());
    }

    return convertToVirtualFiles(sourceFolders);
  }

  private static void addSourceFiles(TreeMap<String, RootDirectory> rootDirectories, NativeArtifact artifact) {
    for (VirtualFile sourceFile : getSourceFiles(artifact)) {
      VirtualFile sourceFolder = sourceFile.getParent();
      String path = sourceFolder.getPath();
      RootDirectory rootDirectory = rootDirectories.get(path);
      if (rootDirectory == null) {
        rootDirectory = new RootDirectory(sourceFolder);
        rootDirectories.put(path, rootDirectory);
      }
      rootDirectory.sourceFiles.add(sourceFile);
    }
  }

  @NotNull
  private static List<VirtualFile> getSourceFiles(@NotNull NativeArtifact artifact) {
    List<File> sourceFiles = Lists.newArrayList();
    for (NativeFile sourceFile : artifact.getSourceFiles()) {
      File source = sourceFile.getFilePath();
      sourceFiles.add(source);
      File header = new File(source.getParentFile(), getNameWithoutExtension(source) + ".h");
      sourceFiles.add(header);
    }

    return convertToVirtualFiles(sourceFiles);
  }

  @NotNull
  private static List<VirtualFile> convertToVirtualFiles(@NotNull Collection<File> files) {
    List<VirtualFile> result = Lists.newArrayListWithCapacity(files.size());
    LocalFileSystem fileSystem = LocalFileSystem.getInstance();
    for (File file : files) {
      VirtualFile virtualFile = fileSystem.findFileByIoFile(file);
      if (virtualFile != null) {
        result.add(virtualFile);
      }
    }

    return result;
  }

  /**
   * Groups directories recursively if either two directories share a common parent directory or a directory is a parent of another
   * directory.
   */
  private static void groupDirectories(TreeMap<String, RootDirectory> rootDirectories) {
    String keyToMerge = rootDirectories.lastKey();
    while (keyToMerge != null) {
      RootDirectory dirToMerge = rootDirectories.get(keyToMerge);
      VirtualFile dirToMergeParent = dirToMerge.rootDir.getParent();
      if (dirToMergeParent == null) {
        keyToMerge = rootDirectories.lowerKey(keyToMerge);
        continue;
      }

      RootDirectory targetDir = rootDirectories.get(dirToMergeParent.getPath());
      if (targetDir != null) {
        targetDir.sourceFolders.addAll(dirToMerge.sourceFolders);
        targetDir.sourceFiles.addAll(dirToMerge.sourceFiles);
        rootDirectories.remove(keyToMerge);
        keyToMerge = rootDirectories.lastKey();
        continue;
      }

      String previousKey = rootDirectories.lowerKey(keyToMerge);
      if (previousKey == null) {
        break;
      }

      RootDirectory previousDir = rootDirectories.get(previousKey);
      VirtualFile previousDirParent = previousDir.rootDir.getParent();
      if (previousDirParent != null && previousDirParent.getPath().equals(dirToMergeParent.getPath())) {
        targetDir = rootDirectories.get(dirToMergeParent.getPath());
        if (targetDir == null) {
          targetDir = new RootDirectory(dirToMergeParent);
          rootDirectories.put(dirToMergeParent.getPath(), targetDir);
        }
        targetDir.sourceFolders.addAll(dirToMerge.sourceFolders);
        targetDir.sourceFolders.addAll(previousDir.sourceFolders);
        targetDir.sourceFiles.addAll(dirToMerge.sourceFiles);
        targetDir.sourceFiles.addAll(previousDir.sourceFiles);
        rootDirectories.remove(keyToMerge);
        rootDirectories.remove(previousKey);
        keyToMerge = rootDirectories.lastKey();
        continue;
      }

      keyToMerge = previousKey;
    }
  }

  /**
   * Merges directories recursively if one directory is an ancestor of another directory.
   */
  private static void mergeDirectories(TreeMap<String, RootDirectory> rootDirectories) {
    String keyToMerge = rootDirectories.lastKey();
    while (keyToMerge != null) {
      RootDirectory dirToMerge = rootDirectories.get(keyToMerge);
      VirtualFile dir = dirToMerge.rootDir.getParent();
      while (dir != null) {
        RootDirectory targetDir = rootDirectories.get(dir.getPath());
        if (targetDir == null) {
          dir = dir.getParent();
          continue;
        }
        targetDir.sourceFolders.addAll(dirToMerge.sourceFolders);
        targetDir.sourceFiles.addAll(dirToMerge.sourceFiles);
        rootDirectories.remove(keyToMerge);
        keyToMerge = rootDirectories.lastKey();
        break;
      }
      if (rootDirectories.size() <= 1) {
        break;
      }
      if (dir == null) {
        keyToMerge = rootDirectories.lowerKey(keyToMerge);
      }
    }
  }

  @NotNull
  @Override
  public Collection<? extends AbstractTreeNode> getChildren() {
    Collection<AbstractTreeNode> sourceDirectoryNodes = getSourceDirectoryNodes(myProject, getArtifacts(), getSettings(), myFileExtensions);
    if (sourceDirectoryNodes.size() == 1) {
      AbstractTreeNode node = Iterables.getOnlyElement(sourceDirectoryNodes);
      assert node instanceof NativeAndroidSourceDirectoryNode;
      return ((NativeAndroidSourceDirectoryNode)node).getChildren();
    }
    return sourceDirectoryNodes;
  }

  @Override
  protected void update(PresentationData presentation) {
    presentation.addText(myNativeLibraryName, SimpleTextAttributes.REGULAR_ATTRIBUTES);
    if (!myNativeLibraryType.isEmpty()) {
      presentation.addText(" (" + myNativeLibraryType + ")", SimpleTextAttributes.GRAY_ATTRIBUTES);
    }
    presentation.setIcon(AllIcons.Nodes.NativeLibrariesFolder);
  }

  @Override
  public boolean contains(@NotNull VirtualFile file) {
    for (NativeArtifact artifact : getArtifacts()) {
      for (VirtualFile folder : getSourceFolders(artifact)) {
        if (VfsUtilCore.isAncestor(folder, file, false)) {
          return true;
        }
      }
    }
    return false;
  }

  @Nullable
  @Override
  public Comparable getSortKey() {
    return myNativeLibraryType + myNativeLibraryName;
  }

  @Nullable
  @Override
  public Comparable getTypeSortKey() {
    return getSortKey();
  }

  @Nullable
  @Override
  public String toTestString(@Nullable Queryable.PrintInfo printInfo) {
    return myNativeLibraryName + (myNativeLibraryType.isEmpty() ? "" : " (" + myNativeLibraryType + ")");
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    if (!super.equals(o)) return false;

    NativeAndroidLibraryNode that = (NativeAndroidLibraryNode)o;

    if (getValue() != that.getValue()) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = super.hashCode();
    for (NativeArtifact artifact : getArtifacts()) {
      result = 31 * result + artifact.hashCode();
    }
    return result;
  }

  @NotNull
  @Override
  public PsiDirectory[] getDirectories() {
    PsiManager psiManager = PsiManager.getInstance(myProject);
    List<PsiDirectory> psiDirectories = new ArrayList<>();

    for (NativeArtifact artifact : getArtifacts()) {
      for (VirtualFile f : getSourceFolders(artifact)) {
        PsiDirectory dir = psiManager.findDirectory(f);
        if (dir != null) {
          psiDirectories.add(dir);
        }
      }
    }

    return psiDirectories.toArray(new PsiDirectory[psiDirectories.size()]);
  }

  @NotNull
  private Collection<NativeArtifact> getArtifacts() {
    Collection<NativeArtifact> artifacts = getValue();
    assert artifacts != null;
    return artifacts;
  }

  private static final class RootDirectory {
    @NotNull private final VirtualFile rootDir;
    @NotNull private final List<VirtualFile> sourceFolders = Lists.newArrayList();
    @NotNull private final List<VirtualFile> sourceFiles = Lists.newArrayList();

    public RootDirectory(@NotNull VirtualFile rootDir) {
      this.rootDir = rootDir;
    }
  }
}
