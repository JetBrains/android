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
package com.android.tools.idea.navigator.nodes.android;

import com.android.tools.idea.navigator.AndroidProjectViewPane;
import com.android.tools.idea.resourceExplorer.ResourceExplorerTestCase;
import com.google.common.collect.Sets;
import com.intellij.ide.projectView.ViewSettings;
import com.intellij.openapi.fileEditor.FileEditorManager;
import org.jetbrains.annotations.NotNull;
import org.junit.Assert;

import static org.mockito.Mockito.mock;

public class AndroidResFolderNodeTest extends ResourceExplorerTestCase {

  public void testExpandOnDoubleClick() throws Exception {
    AndroidResFolderNode node = createNode();
    ResourceExplorerTestCase.runWithResourceExplorerFlagDisabled(
      () -> Assert.assertTrue(node.expandOnDoubleClick()));
    Assert.assertFalse(node.expandOnDoubleClick());
  }

  public void testCanNavigate() throws Exception {
    AndroidResFolderNode node = createNode();
    ResourceExplorerTestCase.runWithResourceExplorerFlagDisabled(
      () -> assertFalse(node.canNavigate()));
    Assert.assertTrue(node.canNavigate());
  }

  public void testNavigate() throws Exception {
    AndroidResFolderNode node = createNode();
    node.navigate(true);
    FileEditorManager.getInstance(getProject()).getSelectedTextEditor();
  }

  @NotNull
  private AndroidResFolderNode createNode() {
    return new AndroidResFolderNode(
      getProject(),
      myFacet,
      mock(ViewSettings.class),
      Sets.newHashSet(),
      mock(AndroidProjectViewPane.class)
    );
  }
}