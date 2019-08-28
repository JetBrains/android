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
package com.android.tools.idea.navigator.nodes.ndk.includes.view;

import com.android.tools.idea.navigator.nodes.ndk.includes.model.PackageFamilyValue;
import com.android.tools.idea.navigator.nodes.ndk.includes.model.SimpleIncludeValue;
import com.android.tools.idea.sdk.IdeSdks;
import com.android.tools.idea.testing.IdeComponents;
import com.android.tools.tests.LeakCheckerRule;
import com.google.common.collect.Lists;
import com.intellij.ide.projectView.impl.nodes.PsiFileNode;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.testFramework.JavaProjectTestCase;
import org.junit.ClassRule;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.List;

import static com.android.tools.idea.navigator.nodes.ndk.includes.view.IncludeViewTestUtils.checkPresentationDataContainsOsSpecificSlashes;
import static com.android.tools.idea.navigator.nodes.ndk.includes.view.IncludeViewTestUtils.checkPresentationDataHasOsSpecificSlashes;
import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.when;

public class IncludesViewTest extends JavaProjectTestCase {
  @ClassRule
  public static LeakCheckerRule checker = new LeakCheckerRule();

  public void testSimpleIncludesView() throws IOException {
    IncludeLayout layout = new IncludeLayout()
      .addLocalHeaders("foo.h")
      .addArtifact("my-artifact", "foo.cpp");

    Collection<? extends AbstractTreeNode> nodes = IncludeViewTests.getChildNodesForIncludes(getProject(), layout.getNativeIncludes());
    assertThat(nodes).hasSize(0);
  }

  public void testSimpleRemoteIncludesView() throws IOException {
    IncludeLayout layout = new IncludeLayout()
      .addRemoteHeaders("my-sdk/foo.h")
      .addRemoteHeaders("my-sdk/bar.h")
      .addRemoteArtifactIncludePaths("my-artifact", "my-sdk")
      .addArtifact("my-artifact", "bar.cpp");

    List<? extends AbstractTreeNode> nodes =
      Lists.newArrayList(IncludeViewTests.getChildNodesForIncludes(getProject(), layout.getNativeIncludes()));
    assertThat(nodes).hasSize(2);
    PsiFileNode node = (PsiFileNode)nodes.get(0);
    assertThat(node.getVirtualFile().exists()).isTrue();
    assertThat(node.getVirtualFile().getName()).isEqualTo("bar.h");
    node = (PsiFileNode)nodes.get(1);
    assertThat(node.getVirtualFile().exists()).isTrue();
    assertThat(node.getVirtualFile().getName()).isEqualTo("foo.h");
    checkPresentationDataHasOsSpecificSlashes(node, "");
  }

  public void testThirdPartyLayout1() throws IOException {
    IncludeLayout layout = new IncludeLayout()
      .addRemoteHeaders("third_party/my-sdk-1/foo.h")
      .addRemoteHeaders("third_party/my-sdk-2/bar.h")
      .addRemoteArtifactIncludePaths("my-artifact", "third_party/my-sdk-1")
      .addRemoteArtifactIncludePaths("my-artifact", "third_party/my-sdk-2")
      .addArtifact("my-artifact", "bar.cpp");

    List<? extends AbstractTreeNode> nodes =
      Lists.newArrayList(IncludeViewTests.getChildNodesForIncludes(getProject(), layout.getNativeIncludes()));
    assertThat(nodes).hasSize(1);
    PackagingFamilyViewNode node = (PackagingFamilyViewNode)nodes.get(0);
    PackageFamilyValue nodeValue = node.getValue();
    assertThat(nodeValue.toString().startsWith("Third Party Packages")).isTrue();
    Collection<? extends AbstractTreeNode> children = node.getChildren();
    assertThat(children).hasSize(2);
    SimpleIncludeViewNode package1 = (SimpleIncludeViewNode)children.iterator().next();
    assertThat(package1.getChildren()).hasSize(1);
    SimpleIncludeValue package1Value = package1.getValue();
    assertThat(package1Value.mySimplePackageName).isEqualTo("my-sdk-1");
    List<? extends AbstractTreeNode> grandChildren = Lists.newArrayList(package1.getChildren());
    assertThat(((PsiFileNode)grandChildren.get(0)).getVirtualFile().getName()).isEqualTo("foo.h");
    checkPresentationDataHasOsSpecificSlashes(package1, "my-sdk-1 (third_party{os-slash}my-sdk-1)");
  }

  public void testThirdPartyLayout2() throws IOException {
    IncludeLayout layout = new IncludeLayout()
      .addRemoteHeaders("third-party/my-sdk-1/foo.h")
      .addRemoteHeaders("third-party/my-sdk-2/bar.h")
      .addRemoteArtifactIncludePaths("my-artifact", "third-party/my-sdk-1")
      .addRemoteArtifactIncludePaths("my-artifact", "third-party/my-sdk-2")
      .addArtifact("my-artifact", "bar.cpp");

    List<? extends AbstractTreeNode> nodes =
      Lists.newArrayList(IncludeViewTests.getChildNodesForIncludes(getProject(), layout.getNativeIncludes()));
    assertThat(nodes).hasSize(1);
    PackagingFamilyViewNode node = (PackagingFamilyViewNode)nodes.get(0);
    PackageFamilyValue nodeValue = node.getValue();
    assertThat(nodeValue.toString().startsWith("Third Party Packages")).isTrue();
    Collection<? extends AbstractTreeNode> children = node.getChildren();
    assertThat(children).hasSize(2);
    checkPresentationDataContainsOsSpecificSlashes(node, "Third Party Packages");
  }

  public void testUpdateUsesOsSlashes() throws IOException {
    IncludeLayout layout = new IncludeLayout()
      .addRemoteHeaders("third_party/my-sdk-1/foo.h")
      .addRemoteHeaders("third_party/my-sdk-2/bar.h")
      .addRemoteArtifactIncludePaths("my-artifact", "third_party/my-sdk-1")
      .addRemoteArtifactIncludePaths("my-artifact", "third_party/my-sdk-2")
      .addArtifact("my-artifact", "bar.cpp");

    List<? extends AbstractTreeNode> nodes =
      Lists.newArrayList(IncludeViewTests.getChildNodesForIncludes(getProject(), layout.getNativeIncludes()));
    assertThat(nodes).hasSize(1);
    PackagingFamilyViewNode node = (PackagingFamilyViewNode)nodes.get(0);
    PackageFamilyValue nodeValue = node.getValue();
    assertThat(nodeValue.toString().startsWith("Third Party Packages")).isTrue();
    Collection<? extends AbstractTreeNode> children = node.getChildren();
    assertThat(children).hasSize(2);
    SimpleIncludeViewNode package1 = (SimpleIncludeViewNode)children.iterator().next();
    checkPresentationDataHasOsSpecificSlashes(package1, "my-sdk-1 (third_party{os-slash}my-sdk-1)");
  }

  public void testNdkLayout() throws IOException {
    IncludeLayout layout = new IncludeLayout()
      .addRemoteHeaders("my-ndk-folder/sources/android/native_app_glue/foo.h")
      .addRemoteHeaders("my-ndk-folder/sources/android/ndk_helper/bar.h")
      .addRemoteArtifactIncludePaths("my-artifact", "my-ndk-folder/sources/android/native_app_glue")
      .addRemoteArtifactIncludePaths("my-artifact", "my-ndk-folder/sources/android/ndk_helper")
      .addLocalHeaders("baz.h")
      .addArtifact("my-artifact", "bar.cpp");
    IdeSdks mockIdeSdks = IdeComponents.mockApplicationService(IdeSdks.class, getTestRootDisposable());
    assertSame(mockIdeSdks, IdeSdks.getInstance());
    File ndkRootFolder = new File(layout.getRemoteRoot(), "my-ndk-folder");
    when(mockIdeSdks.getAndroidNdkPath()).thenReturn(ndkRootFolder);

      List<? extends AbstractTreeNode> nodes =
        Lists.newArrayList(IncludeViewTests.getChildNodesForIncludes(getProject(), layout.getNativeIncludes()));
      assertThat(nodes).hasSize(1);

      PackagingFamilyViewNode child1 = (PackagingFamilyViewNode)nodes.get(0);
      List<? extends AbstractTreeNode> child1Children = Lists.newArrayList(child1.getChildren());
      assertThat(child1Children).hasSize(2);
      SimpleIncludeViewNode child1Child1 = (SimpleIncludeViewNode)child1Children.get(0);
      SimpleIncludeViewNode child1Child2 = (SimpleIncludeViewNode)child1Children.get(1);
      List<? extends AbstractTreeNode> child2child1Children = Lists.newArrayList(child1Child1.getChildren());
      List<? extends AbstractTreeNode> child2child2Children = Lists.newArrayList(child1Child2.getChildren());
      assertThat(child2child1Children).hasSize(1);
      assertThat(child2child2Children).hasSize(1);
      PsiFileNode child2child1child1 = (PsiFileNode)child2child1Children.get(0);
      PsiFileNode child2child2child1 = (PsiFileNode)child2child2Children.get(0);
      assertThat(child2child1child1.getVirtualFile().getName()).isEqualTo("bar.h");
      assertThat(child2child2child1.getVirtualFile().getName()).isEqualTo("foo.h");
      checkPresentationDataHasOsSpecificSlashes(child1Child1, "NDK Helper (sources{os-slash}android{os-slash}ndk_helper)");

  }
}
