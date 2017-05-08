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

import com.android.tools.idea.apk.debugging.NativeLibrary;
import com.intellij.ide.projectView.ViewSettings;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.IdeaTestCase;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static com.android.tools.idea.testing.ProjectFiles.createFolderInProjectRoot;
import static com.google.common.truth.Truth.assertThat;
import static com.intellij.openapi.vfs.VfsUtilCore.virtualToIoFile;
import static org.mockito.Mockito.mock;

/**
 * Tests for {@link LibraryNode}.
 */
public class LibraryNodeTest extends IdeaTestCase {
  public void testGetChildren() throws IOException {
    NativeLibrary library = new NativeLibrary("test");
    createSourceFoldersAndAddPathToLibrary(library, "z", "y", "x", "a", "b", "c");

    LibraryNode libraryNode = new LibraryNode(getProject(), library, mock(ViewSettings.class));
    List<? extends AbstractTreeNode> children = new ArrayList<>(libraryNode.getChildren());
    assertThat(children).hasSize(7);

    assertThat(children.get(0)).isInstanceOf(LibraryFileNode.class);

    // Verify folder nodes are sorted.
    List<String> folderNames = children.subList(1, 7).stream().map(node -> {
      if (node instanceof SourceFolderNode) {
        SourceFolderNode sourceFolderNode = (SourceFolderNode)node;
        return sourceFolderNode.getValue().getName();
      }
      return null;
    }).collect(Collectors.toList());
    assertEquals(Arrays.asList("a", "b", "c", "x", "y", "z"), folderNames);
  }

  private void createSourceFoldersAndAddPathToLibrary(@NotNull NativeLibrary library, @NotNull String... folderNames) throws IOException {
    for (String folderName : folderNames) {
      VirtualFile folder = createFolderInProjectRoot(getProject(), folderName);
      library.sourceFolderPaths.add(virtualToIoFile(folder).getPath());
    }
  }
}