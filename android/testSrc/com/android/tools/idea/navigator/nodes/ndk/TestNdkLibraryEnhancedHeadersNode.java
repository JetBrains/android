/*
 * Copyright (C) 2018 The Android Open Source Project
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
import com.android.tools.idea.navigator.nodes.ndk.includes.view.IncludeLayout;
import com.android.tools.tests.LeakCheckerRule;
import com.google.common.collect.Lists;
import com.intellij.ide.projectView.ViewSettings;
import com.intellij.ide.projectView.impl.nodes.PsiFileNode;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.IdeaTestCase;
import org.junit.ClassRule;
import org.mockito.Mockito;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static com.google.common.truth.Truth.assertThat;

public class TestNdkLibraryEnhancedHeadersNode extends IdeaTestCase {
  @ClassRule
  public static LeakCheckerRule checker = new LeakCheckerRule();

  public void testSimplest() throws IOException {
    List<NativeArtifact> nativeArtifacts = Lists.newArrayList();
    ViewSettings settings = Mockito.mock(ViewSettings.class);
    List<String> sourceFileExtensions = Lists.newArrayList();
    IncludeLayout layout = new IncludeLayout()
      .addRemoteHeaders("my-sdk/foo.h")
      .addRemoteHeaders("my-sdk/bar.h")
      .addRemoteArtifactIncludePaths("my-artifact", "my-sdk")
      .addArtifact("my-artifact", "bar.cpp");
    NdkLibraryEnhancedHeadersNode node = new NdkLibraryEnhancedHeadersNode(getProject(),
                                     "native-library-name",
                                      "native-library-type",
                                      nativeArtifacts,
                                      layout.getNativeIncludes(),
                                      settings,
                                      sourceFileExtensions);
    List<? extends AbstractTreeNode> children = new ArrayList<>(node.getChildren());
    assertThat(children).hasSize(1);
    AbstractTreeNode child = children.get(0);
    assertThat(child.toString()).isEqualTo("includes");
    List<? extends AbstractTreeNode> children2 = new ArrayList<>(child.getChildren());
    assertThat(children2).hasSize(2);
  }

  public void testSimplestWithArtifacts() throws IOException {
    List<NativeArtifact> nativeArtifacts = Lists.newArrayList();
    ViewSettings settings = Mockito.mock(ViewSettings.class);
    List<String> sourceFileExtensions = Lists.newArrayList();
    IncludeLayout layout = new IncludeLayout()
      .addRemoteHeaders("my-sdk/foo.h")
      .addRemoteHeaders("my-sdk/bar.h")
      .addRemoteArtifactIncludePaths("my-artifact", "my-sdk")
      .addArtifact("my-artifact", "bar.cpp");
    NdkLibraryEnhancedHeadersNode node = new NdkLibraryEnhancedHeadersNode(getProject(),
                                                                           "native-library-name",
                                                                           "native-library-type",
                                                                           layout.getNativeIncludes().myArtifacts,
                                                                           layout.getNativeIncludes(),
                                                                           settings,
                                                                           sourceFileExtensions);
    List<? extends AbstractTreeNode> children = new ArrayList<>(node.getChildren());
    assertThat(children).hasSize(1);
    AbstractTreeNode child = children.get(0);
    assertThat(child.toString()).isEqualTo("includes");
    List<? extends AbstractTreeNode> children2 = new ArrayList<>(child.getChildren());
    assertThat(children2).hasSize(2);
    PsiFileNode child2child1 = (PsiFileNode)children2.get(0);
    PsiFileNode child2child2 = (PsiFileNode)children2.get(1);
    assertThat(child2child1.getVirtualFile().getName()).isEqualTo("bar.h");
    assertThat(child2child2.getVirtualFile().getName()).isEqualTo("foo.h");
  }

  public void testSimplestWithArtifactsSubfolders() throws IOException {
    ViewSettings settings = Mockito.mock(ViewSettings.class);
    List<String> sourceFileExtensions = Lists.newArrayList();
    IncludeLayout layout = new IncludeLayout()
      .addRemoteHeaders("my-sdk/foo.h")
      .addRemoteHeaders("my-sdk/bar.h")
      .addRemoteArtifactIncludePaths("my-artifact", "my-sdk")
      .addArtifact("my-artifact", "sub1/bar1.cpp", "sub1/bar2.cpp", "sub2/baz.cpp");
    NdkLibraryEnhancedHeadersNode node = new NdkLibraryEnhancedHeadersNode(getProject(),
                                                                           "native-library-name",
                                                                           "native-library-type",
                                                                           layout.getNativeIncludes().myArtifacts,
                                                                           layout.getNativeIncludes(),
                                                                           settings,
                                                                           sourceFileExtensions);
    List<? extends AbstractTreeNode> children = new ArrayList<>(node.getChildren());
    assertThat(children).hasSize(1);
    AbstractTreeNode child = children.get(0);
    assertThat(child.toString()).isEqualTo("includes");
    List<? extends AbstractTreeNode> children2 = new ArrayList<>(child.getChildren());
    assertThat(children2).hasSize(2);
    PsiFileNode child2child1 = (PsiFileNode)children2.get(0);
    PsiFileNode child2child2 = (PsiFileNode)children2.get(1);
    assertThat(child2child1.getVirtualFile().getName()).isEqualTo("bar.h");
    assertThat(child2child2.getVirtualFile().getName()).isEqualTo("foo.h");
  }

  public void testSimplestWithMultipleArtifactIncludePaths() throws IOException {
    ViewSettings settings = Mockito.mock(ViewSettings.class);
    List<String> sourceFileExtensions = Lists.newArrayList();
    IncludeLayout layout = new IncludeLayout()
      .addRemoteHeaders("my-sdk/foo.h")
      .addRemoteHeaders("my-sdk/bar.h")
      .addRemoteArtifactIncludePaths("my-artifact", "my-sdk", "my-other-thing")
      .addArtifact("my-artifact", "sub1/bar1.cpp", "sub1/bar2.cpp", "sub2/baz.cpp");
    NdkLibraryEnhancedHeadersNode node = new NdkLibraryEnhancedHeadersNode(getProject(),
                                                                           "native-library-name",
                                                                           "native-library-type",
                                                                           layout.getNativeIncludes().myArtifacts,
                                                                           layout.getNativeIncludes(),
                                                                           settings,
                                                                           sourceFileExtensions);
    List<? extends AbstractTreeNode> children = new ArrayList<>(node.getChildren());
    assertThat(children).hasSize(1);
    AbstractTreeNode child = children.get(0);
    assertThat(child.toString()).isEqualTo("includes");
    List<? extends AbstractTreeNode> children2 = new ArrayList<>(child.getChildren());
    assertThat(children2).hasSize(2);
    PsiFileNode child2child1 = (PsiFileNode)children2.get(0);
    PsiFileNode child2child2 = (PsiFileNode)children2.get(1);
    assertThat(child2child1.getVirtualFile().getName()).isEqualTo("bar.h");
    assertThat(child2child2.getVirtualFile().getName()).isEqualTo("foo.h");
  }

  public void testSimplestWithMultipleArtifacts() throws IOException {
    ViewSettings settings = Mockito.mock(ViewSettings.class);
    List<String> sourceFileExtensions = Lists.newArrayList();
    IncludeLayout layout = new IncludeLayout()
      .addRemoteHeaders("my-sdk1/foo1.h")
      .addRemoteHeaders("my-sdk1/bar1.h")
      .addRemoteHeaders("my-sdk2/foo2.h")
      .addRemoteHeaders("my-sdk2/bar2.h")
      .addRemoteHeaders("my-other-thing1/foo1x.h")
      .addRemoteHeaders("my-other-thing1/bar1x.h")
      .addRemoteHeaders("my-other-thing2/foo2x.h")
      .addRemoteHeaders("my-other-thing2/bar2x.h")
      .addRemoteArtifactIncludePaths("my-artifact1", "my-sdk1", "my-other-thing1")
      .addRemoteArtifactIncludePaths("my-artifact2", "my-sdk2", "my-other-thing2")
      .addArtifact("my-artifact1", "sub1a/bar1.cpp", "sub1a/bar2.cpp", "sub1b/baz.cpp")
      .addArtifact("my-artifact2", "sub2a/bar1.cpp", "sub2a/bar2.cpp", "sub2b/baz.cpp");
    NdkLibraryEnhancedHeadersNode node = new NdkLibraryEnhancedHeadersNode(getProject(),
                                                                           "native-library-name",
                                                                           "native-library-type",
                                                                           layout.getNativeIncludes().myArtifacts,
                                                                           layout.getNativeIncludes(),
                                                                           settings,
                                                                           sourceFileExtensions);
    List<? extends AbstractTreeNode> children = new ArrayList<>(node.getChildren());
    assertThat(children).hasSize(1);
    AbstractTreeNode child = children.get(0);
    assertThat(child.toString()).isEqualTo("includes");
    List<? extends AbstractTreeNode> children2 = new ArrayList<>(child.getChildren());
    assertThat(children2).hasSize(8);
    assertThatNodeIs(children2.get(0), "includes/my-other-thing1/bar1x.h");
    assertThatNodeIs(children2.get(1), "includes/my-other-thing1/foo1x.h");
    assertThatNodeIs(children2.get(2), "includes/my-other-thing2/bar2x.h");
    assertThatNodeIs(children2.get(3), "includes/my-other-thing2/foo2x.h");
    assertThatNodeIs(children2.get(4), "includes/my-sdk1/bar1.h");
    assertThatNodeIs(children2.get(5), "includes/my-sdk1/foo1.h");
    assertThatNodeIs(children2.get(6), "includes/my-sdk2/bar2.h");
    assertThatNodeIs(children2.get(7), "includes/my-sdk2/foo2.h");
  }

  private void assertThatNodeIs(AbstractTreeNode node, String name) {
    PsiFileNode file = (PsiFileNode) node;
    assertThat(file.getVirtualFile().getPath()).endsWith(name);
  }
}