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
package com.android.tools.idea.navigator.nodes.ndk;

import com.android.builder.model.NativeArtifact;
import com.android.builder.model.NativeFile;
import com.android.builder.model.NativeFolder;
import com.android.tools.idea.navigator.nodes.FolderGroupNode;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.*;

import static com.intellij.openapi.util.io.FileUtil.getLocationRelativeToUserHome;
import static com.intellij.openapi.util.io.FileUtil.getNameWithoutExtension;
import static com.intellij.ui.SimpleTextAttributes.GRAY_ATTRIBUTES;
import static com.intellij.ui.SimpleTextAttributes.REGULAR_ATTRIBUTES;

public class NdkLibraryNode extends ProjectViewNode<Collection<NativeArtifact>> implements FolderGroupNode {
  private static final Set<String> HEADER_FILE_EXTENSIONS = ImmutableSet.of("h", "hpp", "hh", "h++", "hxx");

  @NotNull private final String myNativeLibraryName;
  @NotNull private final String myNativeLibraryType;
  @NotNull private final Collection<String> mySourceFileExtensions;

  @Nullable private VirtualFile myLibraryFolder;

  public NdkLibraryNode(@NotNull Project project,
                        @NotNull String nativeLibraryName,
                        @NotNull String nativeLibraryType,
                        @NotNull Collection<NativeArtifact> artifacts,
                        @NotNull ViewSettings settings,
                        @NotNull Collection<String> sourceFileExtensions) {
    super(project, artifacts, settings);
    myNativeLibraryName = nativeLibraryName;
    myNativeLibraryType = nativeLibraryType;
    mySourceFileExtensions = sourceFileExtensions;
  }

  @NotNull
  static Collection<AbstractTreeNode> getSourceFolderNodes(@NotNull Project project,
                                                           @NotNull Collection<NativeArtifact> artifacts,
                                                           @NotNull ViewSettings settings,
                                                           @NotNull Collection<String> sourceFileExtensions) {
    TreeMap<String, RootFolder> rootFolders = new TreeMap<>();

    for (NativeArtifact artifact : artifacts) {
      addSourceFolders(rootFolders, artifact);
      addSourceFiles(rootFolders, artifact);
    }

    if (rootFolders.size() > 1) {
      groupFolders(rootFolders);
    }

    if (rootFolders.size() > 1) {
      mergeFolders(rootFolders);
    }

    Set<String> fileExtensions = new HashSet<>(sourceFileExtensions.size() + HEADER_FILE_EXTENSIONS.size());
    fileExtensions.addAll(sourceFileExtensions);
    // add header files extension explicitly as the model only provides the extensions of source files.
    fileExtensions.addAll(HEADER_FILE_EXTENSIONS);

    PsiManager psiManager = PsiManager.getInstance(project);
    List<AbstractTreeNode> children = new ArrayList<>();
    for (RootFolder rootFolder : rootFolders.values()) {
      PsiDirectory directory = psiManager.findDirectory(rootFolder.rootFolder);
      if (directory != null) {
        children.add(new NdkSourceFolderNode(project, directory, settings, fileExtensions, rootFolder.sourceFolders,
                                             rootFolder.sourceFiles));
      }
    }
    return children;
  }

  private static void addSourceFolders(@NotNull TreeMap<String, RootFolder> rootFolders, @NotNull NativeArtifact artifact) {
    for (VirtualFile sourceFolder : getSourceFolders(artifact)) {
      String path = sourceFolder.getPath();
      if (rootFolders.containsKey(path)) {
        continue;
      }
      RootFolder rootFolder = new RootFolder(sourceFolder);
      rootFolder.sourceFolders.add(sourceFolder);
      rootFolders.put(path, rootFolder);
    }
  }

  @NotNull
  private static List<VirtualFile> getSourceFolders(@NotNull NativeArtifact artifact) {
    List<File> sourceFolders = new ArrayList<>();
    for (File headerRoot : artifact.getExportedHeaders()) {
      sourceFolders.add(headerRoot);
    }
    for (NativeFolder sourceFolder : artifact.getSourceFolders()) {
      sourceFolders.add(sourceFolder.getFolderPath());
    }

    return convertToVirtualFiles(sourceFolders);
  }

  private static void addSourceFiles(TreeMap<String, RootFolder> rootFolders, NativeArtifact artifact) {
    for (VirtualFile sourceFile : getSourceFiles(artifact)) {
      VirtualFile sourceFolder = sourceFile.getParent();
      String path = sourceFolder.getPath();
      RootFolder rootFolder = rootFolders.computeIfAbsent(path, k -> new RootFolder(sourceFolder));
      rootFolder.sourceFiles.add(sourceFile);
    }
  }

  @NotNull
  private static List<VirtualFile> getSourceFiles(@NotNull NativeArtifact artifact) {
    List<File> sourceFiles = new ArrayList<>();
    for (NativeFile sourceFile : artifact.getSourceFiles()) {
      File source = sourceFile.getFilePath();
      sourceFiles.add(source);
      for (String extension : HEADER_FILE_EXTENSIONS) {
        sourceFiles.add(new File(source.getParentFile(), getNameWithoutExtension(source) + "." + extension));
      }
    }

    return convertToVirtualFiles(sourceFiles);
  }

  @NotNull
  private static List<VirtualFile> convertToVirtualFiles(@NotNull Collection<File> files) {
    List<VirtualFile> result = new ArrayList<>(files.size());
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
   * Groups folders recursively if either two folders share a common parent folder or a folder is a parent of another folder.
   */
  private static void groupFolders(TreeMap<String, RootFolder> rootFolders) {
    String keyToMerge = rootFolders.lastKey();
    while (keyToMerge != null) {
      RootFolder folderToMerge = rootFolders.get(keyToMerge);
      VirtualFile folderToMergeParent = folderToMerge.rootFolder.getParent();
      if (folderToMergeParent == null) {
        keyToMerge = rootFolders.lowerKey(keyToMerge);
        continue;
      }

      RootFolder targetFolder = rootFolders.get(folderToMergeParent.getPath());
      if (targetFolder != null) {
        targetFolder.sourceFolders.addAll(folderToMerge.sourceFolders);
        targetFolder.sourceFiles.addAll(folderToMerge.sourceFiles);
        rootFolders.remove(keyToMerge);
        keyToMerge = rootFolders.lastKey();
        continue;
      }

      String previousKey = rootFolders.lowerKey(keyToMerge);
      if (previousKey == null) {
        break;
      }

      RootFolder previousFolder = rootFolders.get(previousKey);
      VirtualFile previousFolderParent = previousFolder.rootFolder.getParent();
      if (previousFolderParent != null && previousFolderParent.getPath().equals(folderToMergeParent.getPath())) {
        targetFolder = rootFolders.computeIfAbsent(folderToMergeParent.getPath(), k -> new RootFolder(folderToMergeParent));
        targetFolder.sourceFolders.addAll(folderToMerge.sourceFolders);
        targetFolder.sourceFolders.addAll(previousFolder.sourceFolders);
        targetFolder.sourceFiles.addAll(folderToMerge.sourceFiles);
        targetFolder.sourceFiles.addAll(previousFolder.sourceFiles);
        rootFolders.remove(keyToMerge);
        rootFolders.remove(previousKey);
        keyToMerge = rootFolders.lastKey();
        continue;
      }

      keyToMerge = previousKey;
    }
  }

  /**
   * Merges folders recursively if one folder is an ancestor of another folder.
   */
  private static void mergeFolders(TreeMap<String, RootFolder> rootFolders) {
    String keyToMerge = rootFolders.lastKey();
    while (keyToMerge != null) {
      RootFolder folderToMerge = rootFolders.get(keyToMerge);
      VirtualFile folder = folderToMerge.rootFolder.getParent();
      while (folder != null) {
        RootFolder targetFolder = rootFolders.get(folder.getPath());
        if (targetFolder == null) {
          folder = folder.getParent();
          continue;
        }
        targetFolder.sourceFolders.addAll(folderToMerge.sourceFolders);
        targetFolder.sourceFiles.addAll(folderToMerge.sourceFiles);
        rootFolders.remove(keyToMerge);
        keyToMerge = rootFolders.lastKey();
        break;
      }
      if (rootFolders.size() <= 1) {
        break;
      }
      if (folder == null) {
        keyToMerge = rootFolders.lowerKey(keyToMerge);
      }
    }
  }

  @NotNull
  @Override
  public Collection<? extends AbstractTreeNode> getChildren() {
    Collection<AbstractTreeNode> sourceFolderNodes =
      getSourceFolderNodes(getNotNullProject(), getArtifacts(), getSettings(), mySourceFileExtensions);
    if (sourceFolderNodes.size() == 1) {
      AbstractTreeNode node = Iterables.getOnlyElement(sourceFolderNodes);
      assert node instanceof NdkSourceFolderNode;
      NdkSourceFolderNode sourceFolderNode = (NdkSourceFolderNode)node;
      sourceFolderNode.setShowFolderPath(false);
      myLibraryFolder = sourceFolderNode.getVirtualFile();
      return sourceFolderNode.getChildren();
    }

    for (AbstractTreeNode sourceFolderNode : sourceFolderNodes) {
      if (sourceFolderNode instanceof NdkSourceFolderNode) {
        ((NdkSourceFolderNode)sourceFolderNode).setShowFolderPath(true);
      }
    }
    myLibraryFolder = null;
    return sourceFolderNodes;
  }

  @Override
  protected void update(PresentationData presentation) {
    presentation.addText(myNativeLibraryName, REGULAR_ATTRIBUTES);
    if (!myNativeLibraryType.isEmpty()) {
      presentation.addText(" (" +
                           myNativeLibraryType +
                           (myLibraryFolder != null ? ", " + getLocationRelativeToUserHome(myLibraryFolder.getPresentableUrl()) : "") +
                           ")", GRAY_ATTRIBUTES);
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

  @Override
  @Nullable
  public Comparable getSortKey() {
    return myNativeLibraryType + myNativeLibraryName;
  }

  @Override
  @Nullable
  public Comparable getTypeSortKey() {
    return getSortKey();
  }

  @Override
  @Nullable
  public String toTestString(@Nullable Queryable.PrintInfo printInfo) {
    return myNativeLibraryName + (myNativeLibraryType.isEmpty() ? "" : " (" + myNativeLibraryType + ")");
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    if (!super.equals(o)) {
      return false;
    }
    NdkLibraryNode that = (NdkLibraryNode)o;
    return getValue() == that.getValue();
  }

  @Override
  public int hashCode() {
    int result = super.hashCode();
    for (NativeArtifact artifact : getArtifacts()) {
      result = 31 * result + artifact.hashCode();
    }
    return result;
  }

  @Override
  @NotNull
  public PsiDirectory[] getFolders() {
    PsiManager psiManager = PsiManager.getInstance(getNotNullProject());
    List<PsiDirectory> folders = new ArrayList<>();

    for (NativeArtifact artifact : getArtifacts()) {
      for (VirtualFile f : getSourceFolders(artifact)) {
        PsiDirectory dir = psiManager.findDirectory(f);
        if (dir != null) {
          folders.add(dir);
        }
      }
    }
    return folders.toArray(new PsiDirectory[folders.size()]);
  }

  @NotNull
  private Project getNotNullProject() {
    assert myProject != null;
    return myProject;
  }

  @NotNull
  private Collection<NativeArtifact> getArtifacts() {
    Collection<NativeArtifact> artifacts = getValue();
    assert artifacts != null;
    return artifacts;
  }

  private static final class RootFolder {
    @NotNull final VirtualFile rootFolder;
    @NotNull final List<VirtualFile> sourceFolders = new ArrayList<>();
    @NotNull final List<VirtualFile> sourceFiles = new ArrayList<>();

    RootFolder(@NotNull VirtualFile rootFolder) {
      this.rootFolder = rootFolder;
    }
  }
}
