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

import com.android.tools.idea.sdk.IdeSdks;
import com.android.tools.idea.testing.IdeComponents;
import com.android.tools.tests.LeakCheckerRule;
import com.intellij.ide.projectView.impl.nodes.PsiFileNode;
import com.intellij.testFramework.JavaProjectTestCase;
import org.junit.ClassRule;

import java.io.File;
import java.io.IOException;
import java.util.List;

import static com.android.tools.idea.navigator.nodes.ndk.includes.view.IncludeViewTestUtils.checkPresentationDataHasOsSpecificSlashes;
import static com.android.tools.idea.navigator.nodes.ndk.includes.view.IncludeViewTests.*;
import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.when;

public class SimpleIncludeViewNodeTest extends JavaProjectTestCase {
  @ClassRule
  public static LeakCheckerRule checker = new LeakCheckerRule();

  public void testNdkLayout() throws IOException {
    IncludeLayout layout = new IncludeLayout()
      .addRemoteHeaders("my-ndk-folder/sources/android/native_app_glue/foo.h")
      .addRemoteHeaders("my-ndk-folder/sources/android/ndk_helper/bar.h")
      .addRemoteArtifactIncludePaths("my-artifact", "my-ndk-folder/sources/android/native_app_glue")
      .addRemoteArtifactIncludePaths("my-artifact", "my-ndk-folder/sources/android/ndk_helper")
      .addArtifact("my-artifact", "bar.cpp");
    IdeSdks mockIdeSdks = IdeComponents.mockApplicationService(IdeSdks.class, getTestRootDisposable());
    assertSame(mockIdeSdks, IdeSdks.getInstance());
    File ndkRootFolder = new File(layout.getRemoteRoot(), "my-ndk-folder");
    when(mockIdeSdks.getAndroidNdkPath()).thenReturn(ndkRootFolder);

    List<SimpleIncludeViewNode> nodes = getChildNodesForIncludes(getProject(), layout.getNativeIncludes(), SimpleIncludeViewNode.class);

    assertThat(nodes).hasSize(2);
    assertThat(nodes.get(0).getValue().myIncludeFolder.getName()).isEqualTo("ndk_helper");
    assertThat(nodes.get(1).getValue().myIncludeFolder.getName()).isEqualTo("native_app_glue");

    // Check the children of the simple include view nodes
    List<PsiFileNode> children = getChildrenOfType(nodes, PsiFileNode.class);
    assertThat(children).hasSize(2);

    // Check that nodes contains all files in the layout.
    assertContainsAllFilesAsChildren(nodes, layout.headerFilesCreated);

    checkPresentationDataHasOsSpecificSlashes(nodes.get(0), "NDK Helper (sources{os-slash}android{os-slash}ndk_helper)");
    checkPresentationDataHasOsSpecificSlashes(nodes.get(1), "Native App Glue (sources{os-slash}android{os-slash}native_app_glue)");
  }

  public void testNdkLayoutNonHeader() throws IOException {
    IncludeLayout layout = new IncludeLayout()
      .addRemoteExtraFiles("my-ndk-folder/sources/android/native_app_glue/logfile.txt")
      .addRemoteHeaders("my-ndk-folder/sources/android/native_app_glue/foo.h")
      .addRemoteHeaders("my-ndk-folder/sources/android/ndk_helper/bar.h")
      .addRemoteArtifactIncludePaths("my-artifact", "my-ndk-folder/sources/android/native_app_glue")
      .addRemoteArtifactIncludePaths("my-artifact", "my-ndk-folder/sources/android/ndk_helper")
      .addArtifact("my-artifact", "bar.cpp");
    IdeSdks mockIdeSdks = IdeComponents.mockApplicationService(IdeSdks.class, getTestRootDisposable());
    assertSame(mockIdeSdks, IdeSdks.getInstance());
    File ndkRootFolder = new File(layout.getRemoteRoot(), "my-ndk-folder");
    when(mockIdeSdks.getAndroidNdkPath()).thenReturn(ndkRootFolder);

    List<SimpleIncludeViewNode> nodes = getChildNodesForIncludes(getProject(), layout.getNativeIncludes(), SimpleIncludeViewNode.class);

    assertThat(nodes).hasSize(2);
    assertThat(nodes.get(0).getValue().myIncludeFolder.getName()).isEqualTo("ndk_helper");
    assertThat(nodes.get(1).getValue().myIncludeFolder.getName()).isEqualTo("native_app_glue");

    // Check the children of the simple include view nodes
    List<PsiFileNode> children = getChildrenOfType(nodes, PsiFileNode.class);
    assertThat(children).hasSize(2);

    // Check that nodes contains all files in the layout.
    assertContainsAllFilesAsChildren(nodes, layout.headerFilesCreated);

    // Check that nodes don't contain non-header files
    assertDoesNotContainAnyFilesAsChildren(nodes, layout.extraFilesCreated);

    checkPresentationDataHasOsSpecificSlashes(nodes.get(0), "NDK Helper (sources{os-slash}android{os-slash}ndk_helper)");
    checkPresentationDataHasOsSpecificSlashes(nodes.get(1), "Native App Glue (sources{os-slash}android{os-slash}native_app_glue)");
  }

  public void testNdkPointingToMissingFolder() throws IOException {
    IncludeLayout layout = new IncludeLayout()
      .addRemoteArtifactIncludePaths("my-artifact", "my-ndk-folder/sources/android/native_app_glue")
      .addRemoteArtifactIncludePaths("my-artifact", "my-ndk-folder/sources/android/ndk_helper")
      .addLocalHeaders("baz.h")
      .addArtifact("my-artifact", "bar.cpp");
    IdeSdks mockIdeSdks = IdeComponents.mockApplicationService(IdeSdks.class, getTestRootDisposable());
    assertSame(mockIdeSdks, IdeSdks.getInstance());
    File ndkRootFolder = new File(layout.getRemoteRoot(), "my-ndk-folder");
    when(mockIdeSdks.getAndroidNdkPath()).thenReturn(ndkRootFolder);

    List<SimpleIncludeViewNode> nodes = getChildNodesForIncludes(getProject(), layout.getNativeIncludes(), SimpleIncludeViewNode.class);

    assertThat(nodes).hasSize(2);
    assertThat(nodes.get(0).getValue().myIncludeFolder.getName()).isEqualTo("ndk_helper");
    assertThat(nodes.get(1).getValue().myIncludeFolder.getName()).isEqualTo("native_app_glue");
    checkPresentationDataHasOsSpecificSlashes(nodes.get(0), "NDK Helper (sources{os-slash}android{os-slash}ndk_helper)");
    checkPresentationDataHasOsSpecificSlashes(nodes.get(1), "Native App Glue (sources{os-slash}android{os-slash}native_app_glue)");

    // Check the children of the simple include view nodes
    List<PsiFileNode> children = getChildrenOfType(nodes, PsiFileNode.class);
    assertThat(children).hasSize(0);
  }

  public void testNullProject() throws IOException {
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

    List<SimpleIncludeViewNode> nodes = getChildNodesForIncludes(null, layout.getNativeIncludes(), SimpleIncludeViewNode.class);

    assertThat(nodes).hasSize(0);
  }
}
