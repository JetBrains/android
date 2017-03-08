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
package com.android.tools.idea.navigator.nodes.apk;

import com.android.tools.idea.apk.ApkFacet;
import com.android.tools.idea.navigator.nodes.apk.ndk.NdkGroupNode;
import com.intellij.ide.projectView.ViewSettings;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.IdeaTestCase;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.mockito.Mock;

import java.io.IOException;
import java.util.List;

import static com.android.tools.idea.testing.Facets.createAndAddAndroidFacet;
import static com.android.tools.idea.testing.Facets.createAndAddApkFacet;
import static com.android.tools.idea.testing.ProjectFiles.createFolderInProjectRoot;
import static com.intellij.openapi.vfs.VfsUtilCore.virtualToIoFile;
import static org.mockito.MockitoAnnotations.initMocks;

/**
 * Tests for {@link ApkModuleNode}.
 */
public class ApkModuleNodeTest extends IdeaTestCase {
  @Mock private ViewSettings myViewSettings;

  private ApkFacet myApkFacet;

  private ApkModuleNode myNode;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    initMocks(this);

    Module module = getModule();
    AndroidFacet androidFacet = createAndAddAndroidFacet(module);
    myApkFacet = createAndAddApkFacet(module);

    myNode = new ApkModuleNode(getProject(), module, androidFacet, myApkFacet, myViewSettings);
  }

  public void testCreateNdkGroupNodeWithNativeSourcePaths() throws IOException {
    Project project = getProject();
    VirtualFile aFolder = createFolderInProjectRoot(project, "a");
    VirtualFile bFolder = createFolderInProjectRoot(project, "b");
    addNativeSourceFolders(aFolder, bFolder);

    NdkGroupNode ndkGroupNode = myNode.createNdkGroupNode();
    assertNotNull(ndkGroupNode);

    List<VirtualFile> value = ndkGroupNode.getValue();
    assertNotNull(value);

    assertEquals(aFolder.getPath(), value.get(0).getPath());
    assertEquals(bFolder.getPath(), value.get(1).getPath());
  }

  private void addNativeSourceFolders(@NotNull VirtualFile... folders) {
    List<String> nativeSourcePaths = myApkFacet.getConfiguration().NATIVE_SOURCE_PATHS;
    for (VirtualFile folder : folders) {
      nativeSourcePaths.add(virtualToIoFile(folder).getPath());
    }
  }

  public void testCreateNdkGroupNodeWithoutNativeSourcePaths() {
    myApkFacet.getConfiguration().NATIVE_SOURCE_PATHS.clear(); // No source paths.
    NdkGroupNode ndkGroupNode = myNode.createNdkGroupNode();
    assertNull(ndkGroupNode);
  }
}