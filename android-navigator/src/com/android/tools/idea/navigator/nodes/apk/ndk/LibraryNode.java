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
package com.android.tools.idea.navigator.nodes.apk.ndk;

import static com.android.tools.idea.navigator.nodes.apk.SourceFolders.isInSourceFolder;
import static com.android.tools.idea.navigator.nodes.apk.ndk.PathTrees.getSourceFolderNodes;
import static com.intellij.ui.SimpleTextAttributes.GRAY_ATTRIBUTES;
import static com.intellij.ui.SimpleTextAttributes.REGULAR_ATTRIBUTES;
import static icons.StudioIcons.Shell.Filetree.LIBRARY_MODULE;
import static java.io.File.separatorChar;

import com.android.tools.idea.apk.debugging.NativeLibrary;
import com.android.tools.idea.apk.paths.PathTree;
import com.android.tools.idea.navigator.nodes.ndk.includes.model.SimpleIncludeValue;
import com.android.tools.idea.navigator.nodes.ndk.includes.resolver.NdkIncludeResolver;
import com.google.common.base.Joiner;
import com.intellij.ide.projectView.PresentationData;
import com.intellij.ide.projectView.ProjectViewNode;
import com.intellij.ide.projectView.ViewSettings;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Queryable;
import com.intellij.openapi.vfs.VirtualFile;
import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Represents a native library.
 */
public class LibraryNode extends ProjectViewNode<NativeLibrary> {
  @NotNull final NativeLibrary myLibrary;
  @NotNull private final String myLibraryName;

  public LibraryNode(@NotNull Project project, @NotNull NativeLibrary library, @NotNull ViewSettings settings) {
    super(project, library, settings);
    myLibrary = library;
    myLibraryName = getLibraryName();
  }

  @NotNull
  private String getLibraryName() {
    VirtualFile file = getFirstFile();
    return file != null ? file.getNameWithoutExtension() : myLibrary.name;
  }

  @Nullable
  protected VirtualFile getFirstFile() {
    List<VirtualFile> files = myLibrary.getSharedObjectFiles();
    return files.isEmpty() ? null : files.get(0);
  }

  @Override
  @NotNull
  public Collection<? extends AbstractTreeNode<?>> getChildren() {
    assert myProject != null;
    List<AbstractTreeNode<?>> children = new ArrayList<>();
    ViewSettings settings = getSettings();
    children.add(new LibraryFileNode(myProject, myLibrary, settings));

    // TODO(jomof): This class is called NdkIncludeResolver but it is resolving a source folder here. This class and associated classes should be renamed to *SourceResolver
    NdkIncludeResolver resolver = new NdkIncludeResolver();
    List<String> paths = myLibrary.getSourceFolderPaths();

    if (!paths.isEmpty()) {
      // Organize source folders in a tree.
      List<String> srcPaths = new ArrayList<>();
      List<String> ndkPaths = new ArrayList<>();
      PathTree srcPathTree = new PathTree();
      Map<File, PathTree> ndkPathTreeMap = new HashMap<>();

      for (String path : paths) {
        SimpleIncludeValue resolution = resolver.resolve(new File(path));
        if (resolution != null) {
          File ndkPath = resolution.getPackageFamilyBaseFolder();
          PathTree ndkPathTree = ndkPathTreeMap.get(ndkPath);
          if (ndkPathTree == null) {
            ndkPathTree = new PathTree();
            ndkPathTreeMap.put(ndkPath, ndkPathTree);
          }

          ndkPaths.add(path);
          ndkPathTree.addPath(path, separatorChar);
          continue;
        }
        srcPaths.add(path);
        srcPathTree.addPath(path, separatorChar);
      }

      for(File ndkPath : ndkPathTreeMap.keySet()) {
        PathTree ndkPathTree = ndkPathTreeMap.get(ndkPath);
        children.add(new NdkSourceNode(myProject, ndkPath, ndkPathTree, new SourceCodeFilter(ndkPaths), settings));
      }
      children.addAll(getSourceFolderNodes(srcPathTree, new SourceCodeFilter(srcPaths), myProject, settings));
    }
    return children;
  }

  @Override
  public boolean canNavigate() {
    return true;
  }

  @Override
  public boolean contains(@NotNull VirtualFile file) {
    if (myLibrary.sharedObjectFilesByAbi.containsValue(file)) {
      return true;
    }

    return isInSourceFolder(file, myLibrary);
  }

  @Override
  protected void update(@NotNull PresentationData presentation) {
    presentation.setIcon(LIBRARY_MODULE);
    presentation.addText(myLibraryName, REGULAR_ATTRIBUTES);

    String abis = Joiner.on(", ").join(myLibrary.abis);
    presentation.addText(" (" + abis + ")", GRAY_ATTRIBUTES);
  }

  @Override
  public boolean isAlwaysExpand() {
    return true;
  }

  @Override
  public boolean isAlwaysShowPlus() {
    return true;
  }

  @Nullable
  @Override
  public String toTestString(@Nullable Queryable.PrintInfo printInfo) {
    return myLibraryName;
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
    LibraryNode node = (LibraryNode)o;
    return Objects.equals(myLibrary, node.myLibrary) &&
           Objects.equals(myLibraryName, node.myLibraryName);
  }

  @Override
  public int hashCode() {
    return Objects.hash(super.hashCode(), myLibrary, myLibraryName);
  }
}
