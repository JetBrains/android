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

import static com.android.tools.idea.testing.ProjectFiles.createFolder;
import static com.android.tools.idea.testing.ProjectFiles.createFolderInProjectRoot;
import static com.google.common.truth.Truth.assertThat;
import static com.intellij.openapi.util.io.FileUtil.toSystemDependentName;
import static com.intellij.openapi.vfs.VfsUtilCore.virtualToIoFile;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.android.tools.idea.apk.debugging.NativeLibrary;
import com.android.tools.idea.sdk.IdeSdks;
import com.android.tools.idea.testing.IdeComponents;
import com.intellij.ide.projectView.ViewSettings;
import com.intellij.ide.projectView.impl.nodes.PsiDirectoryNode;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.PlatformTestCase;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.jetbrains.annotations.NotNull;

/**
 * Tests for {@link LibraryNode}.
 */
public class LibraryNodeTest extends PlatformTestCase {
  private IdeSdks myIdeSdks;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myIdeSdks = new IdeComponents(getProject()).mockApplicationService(IdeSdks.class);
  }

  public void testGetChildren() throws IOException {
    List<String> sourceFolderPaths = createSourceFolders("z", "y", "x", "a", "b", "c");
    NativeLibrary library = new NativeLibrary("test") {
      @Override
      @NotNull
      public List<String> getSourceFolderPaths() {
        return sourceFolderPaths;
      }
    };

    LibraryNode libraryNode = new LibraryNode(getProject(), library, mock(ViewSettings.class));
    List<? extends AbstractTreeNode> children = new ArrayList<>(libraryNode.getChildren());
    assertThat(children).hasSize(2);

    assertThat(children.get(0)).isInstanceOf(LibraryFileNode.class);

    VirtualFile folder = getFolderFrom(children.get(1));
    String path = virtualToIoFile(folder).getPath();

    String firstSourceFolder = sourceFolderPaths.get(0);
    // Verify that the root node is the root folder for the source paths.
    // PsiDirectoryNode will take care of populate its children.
    assertThat(firstSourceFolder).contains(path);
  }

  public void testGetChildrenWithNdk() throws IOException {
    VirtualFile ndkFolder = createFolderInProjectRoot(getProject(), "ndk-bundle");
    File ndkFolderPath = virtualToIoFile(ndkFolder);
    when(myIdeSdks.getAndroidNdkPath()).thenReturn(ndkFolderPath);

    VirtualFile sysrootFolder = createFolder(ndkFolder, "sysroot");

    List<String> sourceFolderPaths = new ArrayList<>(createSourceFolders("a", "b", "c"));
    sourceFolderPaths.add(toSystemDependentName(sysrootFolder.getPath()));

    NativeLibrary library = new NativeLibrary("test") {
      @Override
      @NotNull
      public List<String> getSourceFolderPaths() {
        return sourceFolderPaths;
      }
    };

    LibraryNode libraryNode = new LibraryNode(getProject(), library, mock(ViewSettings.class));
    List<? extends AbstractTreeNode> children = new ArrayList<>(libraryNode.getChildren());
    assertThat(children).hasSize(3);

    assertThat(children.get(0)).isInstanceOf(LibraryFileNode.class);

    AbstractTreeNode node = children.get(1);
    assertThat(node).isInstanceOf(NdkSourceNode.class);

    NdkSourceNode ndkSourceNode = (NdkSourceNode)node;
    List<? extends AbstractTreeNode> ndkChildren = new ArrayList<>(ndkSourceNode.getChildren());
    assertThat(ndkChildren).hasSize(1);

    VirtualFile folder = getFolderFrom(ndkChildren.get(0));
    assertEquals(sysrootFolder.getPath(), folder.getPath());

    folder = getFolderFrom(children.get(2));
    String path = virtualToIoFile(folder).getPath();

    String firstSourceFolder = sourceFolderPaths.get(0);
    // Verify that the root node is the root folder for the source paths.
    // PsiDirectoryNode will take care of populate its children.
    assertThat(firstSourceFolder).contains(path);
  }

  public void testGetChildrenWithSideBySideNdk() throws IOException {
    VirtualFile ndkBaseFolder = createFolderInProjectRoot(getProject(), "ndk");
    when(myIdeSdks.getAndroidNdkPath()).thenReturn(null); // No singleton NDK only SxS

    VirtualFile ndkVersionFolder = createFolder(ndkBaseFolder, "21.4.7075529");
    VirtualFile sysrootFolder = createFolder(ndkVersionFolder, "sysroot");

    List<String> sourceFolderPaths = new ArrayList<>(createSourceFolders("a", "b", "c"));
    sourceFolderPaths.add(toSystemDependentName(sysrootFolder.getPath()));

    NativeLibrary library = new NativeLibrary("test") {
      @Override
      @NotNull
      public List<String> getSourceFolderPaths() {
        return sourceFolderPaths;
      }
    };

    LibraryNode libraryNode = new LibraryNode(getProject(), library, mock(ViewSettings.class));
    List<? extends AbstractTreeNode> children = new ArrayList<>(libraryNode.getChildren());
    assertThat(children).hasSize(3);

    assertThat(children.get(0)).isInstanceOf(LibraryFileNode.class);

    AbstractTreeNode node = children.get(1);
    assertThat(node).isInstanceOf(NdkSourceNode.class);

    NdkSourceNode ndkSourceNode = (NdkSourceNode)node;
    List<? extends AbstractTreeNode> ndkChildren = new ArrayList<>(ndkSourceNode.getChildren());
    assertThat(ndkChildren).hasSize(1);

    VirtualFile folder = getFolderFrom(ndkChildren.get(0));
    assertEquals(sysrootFolder.getPath(), folder.getPath());

    folder = getFolderFrom(children.get(2));
    String path = virtualToIoFile(folder).getPath();

    String firstSourceFolder = sourceFolderPaths.get(0);
    // Verify that the root node is the root folder for the source paths.
    // PsiDirectoryNode will take care of populate its children.
    assertThat(firstSourceFolder).contains(path);
  }

  @NotNull
  private List<String> createSourceFolders(@NotNull String... folderNames) throws IOException {
    List<String> sourceFolderPaths = new ArrayList<>();
    for (String folderName : folderNames) {
      VirtualFile folder = createFolderInProjectRoot(getProject(), folderName);
      sourceFolderPaths.add(toSystemDependentName(folder.getPath()));
    }
    sourceFolderPaths.sort(Comparator.naturalOrder());
    return sourceFolderPaths;
  }

  @NotNull
  private static VirtualFile getFolderFrom(@NotNull AbstractTreeNode node) {
    assertThat(node).isInstanceOf(PsiDirectoryNode.class);
    return ((PsiDirectoryNode)node).getVirtualFile();
  }
}
