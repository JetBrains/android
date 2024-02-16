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

import com.android.tools.idea.apk.paths.PathTree;
import com.intellij.ide.projectView.ViewSettings;
import com.intellij.ide.projectView.impl.nodes.PsiDirectoryNode;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.testFramework.HeavyPlatformTestCase;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.List;

import static com.android.tools.idea.testing.ProjectFiles.createFolder;
import static com.android.tools.idea.testing.ProjectFiles.createFolderInProjectRoot;
import static com.google.common.truth.Truth.assertThat;
import static com.intellij.openapi.vfs.VfsUtilCore.isAncestor;
import static com.intellij.openapi.vfs.VfsUtilCore.virtualToIoFile;
import static java.io.File.separatorChar;
import static org.mockito.Mockito.mock;

/**
 * Tests for {@link PathTrees}.
 */
public class PathTreesTest extends HeavyPlatformTestCase {
  private VirtualFile myNdkFolder;
  private VirtualFile myPlatformsFolder;
  private VirtualFile myPrebuiltsFolder;
  private PathTree myTree;

  @Override
  protected void setUp() throws Exception {
    super.setUp();

    myNdkFolder = createFolderInProjectRoot(getProject(), "ndk");

    myPlatformsFolder = createFolder(myNdkFolder, "platforms");
    VirtualFile android19Folder = createFolder(myPlatformsFolder, "android-19");

    myPrebuiltsFolder = createFolder(myNdkFolder, "prebuilts");
    VirtualFile gccFolder = createFolder(myPrebuiltsFolder, "gcc");

    myTree = new PathTree();
    myTree.addPath(virtualToIoFile(android19Folder).getPath(), separatorChar);
    myTree.addPath(virtualToIoFile(gccFolder).getPath(), separatorChar);
  }

  public void testFindSourceFolders() throws IOException {
    List<PsiDirectory> folders = PathTrees.findSourceFolders(myTree, virtualToIoFile(myNdkFolder).getPath(), getProject());
    assertThat(folders).hasSize(2);

    assertEquals(myPlatformsFolder.getPath(), pathOf(folders.get(0)));
    assertEquals(myPrebuiltsFolder.getPath(), pathOf(folders.get(1)));
  }

  @NotNull
  private static String pathOf(@NotNull PsiDirectory folder) {
    return folder.getVirtualFile().getPath();
  }

  public void testGetSourceFolderNodes() {
    SourceCodeFilter filter = mock(SourceCodeFilter.class);
    ViewSettings settings = mock(ViewSettings.class);

    List<AbstractTreeNode<?>> nodes = PathTrees.getSourceFolderNodes(myTree, filter, getProject(), settings);
    assertThat(nodes).hasSize(1);

    AbstractTreeNode node = nodes.get(0);
    assertThat(node).isInstanceOf(PsiDirectoryNode.class);

    PsiDirectoryNode folderNode = (PsiDirectoryNode)node;
    assertSame(filter, folderNode.getFilter());
    assertSame(settings, folderNode.getSettings());
    VirtualFile rootFolder = folderNode.getVirtualFile();
    assertTrue(isAncestor(rootFolder, myNdkFolder, false /* not strict */));
  }
}