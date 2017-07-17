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

import com.intellij.ide.projectView.PresentationData;
import com.intellij.ide.projectView.ViewSettings;
import com.intellij.ide.util.treeView.PresentableNodeDescriptor;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiManager;
import com.intellij.testFramework.IdeaTestCase;

import java.io.File;
import java.util.List;

import static com.android.tools.idea.gradle.util.FilePaths.toSystemDependentPath;
import static com.android.tools.idea.testing.ProjectFiles.createFolderInProjectRoot;
import static com.google.common.truth.Truth.assertThat;
import static com.intellij.ui.SimpleTextAttributes.GRAY_ATTRIBUTES;
import static com.intellij.ui.SimpleTextAttributes.REGULAR_ATTRIBUTES;
import static org.mockito.Mockito.mock;

/**
 * Tests for {@link SourceFolderNode}.
 */
public class SourceFolderNodeTest extends IdeaTestCase {
  private SourceFolderNode myNode;
  private VirtualFile myFolder;
  private PresentationData myPresentation;

  @Override
  protected void setUp() throws Exception {
    super.setUp();

    myFolder = createFolderInProjectRoot(getProject(), "src");
    PsiDirectory psiFolder = PsiManager.getInstance(myProject).findDirectory(myFolder);
    assertNotNull(psiFolder);
    myPresentation = new PresentationData();
    myNode = new SourceFolderNode(getProject(), psiFolder, mock(ViewSettings.class));
  }

  public void testUpdateImpl() throws Exception {
    myNode.updateImpl(myPresentation);

    File expectedFile = toSystemDependentPath(myFolder.getPath());
    assertThat(myPresentation.getTooltip()).isEqualTo(expectedFile.getPath());

    List<PresentableNodeDescriptor.ColoredFragment> allText = myPresentation.getColoredText();
    assertThat(allText).hasSize(2);

    PresentableNodeDescriptor.ColoredFragment folderName = allText.get(0);
    assertEquals("src ", folderName.getText());
    assertEquals(REGULAR_ATTRIBUTES, folderName.getAttributes());

    PresentableNodeDescriptor.ColoredFragment path = allText.get(1);
    assertThat(path.getText()).contains(expectedFile.getParentFile().getName());
    assertEquals(GRAY_ATTRIBUTES, path.getAttributes());
  }
}