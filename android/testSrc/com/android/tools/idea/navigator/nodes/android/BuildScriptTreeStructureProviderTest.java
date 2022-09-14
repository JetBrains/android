/*
 * Copyright (C) 2019 The Android Open Source Project
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

import static com.intellij.testFramework.UsefulTestCase.assertSameElements;
import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import com.intellij.ide.projectView.TreeStructureProvider;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.junit.Test;

/**
 * Test for {@link BuildScriptTreeStructureProvider}
 */
public class BuildScriptTreeStructureProviderTest {
  /**
   * Children of AndroidBuildScriptsGroupNode are not modified.
   */
  @Test
  public void modifyAndroidBuildScriptsGroupNode() {
    TreeStructureProvider mockProvider = mock(TreeStructureProvider.class);
    AbstractTreeNode mockParent = mock(AndroidBuildScriptsGroupNode.class);
    BuildScriptTreeStructureProvider provider = new BuildScriptTreeStructureProvider(mockProvider);
    List<AbstractTreeNode<?>> children = createChildren();

    Collection<AbstractTreeNode<?>> result = provider.modify(mockParent, children, null);
    verifyNoMoreInteractions(mockProvider);
    assertSameElements(result, children);
  }

  /**
   * Children of AbstractTreeNode are modified by the structure provider.
   */
  @Test
  public void modifyAbstractTreeNode() {
    TreeStructureProvider mockProvider = mock(TreeStructureProvider.class);
    AbstractTreeNode mockParent = mock(AbstractTreeNode.class);
    BuildScriptTreeStructureProvider provider = new BuildScriptTreeStructureProvider(mockProvider);
    List<AbstractTreeNode<?>> children = createChildren();
    List<AbstractTreeNode<?>> modified = createChildren();
    doReturn(modified).when(mockProvider).modify(mockParent, children, null);

    Collection<AbstractTreeNode<?>> result = provider.modify(mockParent, children, null);
    verify(mockProvider).modify(mockParent, children, null);
    assertSameElements(result, modified);
  }

  /**
   * Test data returns the result of wrapped provider
   */
  @Test
  public void getData() {
    TreeStructureProvider mockProvider = mock(TreeStructureProvider.class);
    BuildScriptTreeStructureProvider provider = new BuildScriptTreeStructureProvider(mockProvider);
    Object data = "This is test data";
    doReturn(data).when(mockProvider).getData(any(), any());
    Object result = provider.getData(createChildren(), "");
    verify(mockProvider).getData(any(), any());
    assertEquals(data, result);
  }

  private static List<AbstractTreeNode<?>> createChildren() {
    ArrayList<AbstractTreeNode<?>> result = new ArrayList<>();
    result.add(mock(AbstractTreeNode.class));
    result.add(mock(AbstractTreeNode.class));
    return result;
  }
}
