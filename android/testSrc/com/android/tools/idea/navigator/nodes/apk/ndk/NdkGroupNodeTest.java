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

import com.android.tools.idea.testing.AndroidGradleTestCase;
import com.intellij.ide.projectView.ViewSettings;
import com.intellij.ide.projectView.impl.nodes.PsiDirectoryNode;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static com.android.tools.idea.testing.ProjectFiles.createFolderInProjectRoot;
import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Tests for {@link NdkGroupNode}.
 */
public class NdkGroupNodeTest extends AndroidGradleTestCase {
  public void testGetChildren() throws Exception {
    Project project = getProject();
    VirtualFile aFolder = createFolderInProjectRoot(project, "a");
    VirtualFile bFolder = createFolderInProjectRoot(project, "b");
    VirtualFile cFolder = createFolderInProjectRoot(project, "c");

    List<VirtualFile> folders = Arrays.asList(aFolder, bFolder, cFolder);

    NdkGroupNode node = new NdkGroupNode(project, folders, mock(ViewSettings.class));

    List<? extends AbstractTreeNode> children = new ArrayList<>(node.getChildren());
    assertThat(children).hasSize(3);

    verifyNodeHasFile(children.get(0), aFolder);
    verifyNodeHasFile(children.get(1), bFolder);
    verifyNodeHasFile(children.get(2), cFolder);
  }

  private static void verifyNodeHasFile(@NotNull AbstractTreeNode node, @NotNull VirtualFile folder) {
    assertThat(node).isInstanceOf(PsiDirectoryNode.class);
    PsiDirectoryNode directoryNode = (PsiDirectoryNode)node;
    PsiDirectory value = directoryNode.getValue();
    assertNotNull(value);
    assertEquals(folder.getPath(), value.getVirtualFile().getPath());
  }
}