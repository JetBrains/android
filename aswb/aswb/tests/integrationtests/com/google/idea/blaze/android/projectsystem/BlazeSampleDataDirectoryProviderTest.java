/*
 * Copyright 2018 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.idea.blaze.android.projectsystem;

import static com.android.SdkConstants.FD_SAMPLE_DATA;
import static com.android.tools.idea.util.FileExtensions.toPathString;
import static com.android.tools.idea.util.FileExtensions.toVirtualFile;
import static com.google.common.truth.Truth.assertThat;
import static com.google.idea.blaze.android.targetmapbuilder.NbAndroidTarget.android_binary;

import com.android.ide.common.util.PathString;
import com.google.idea.blaze.android.BlazeAndroidIntegrationTestCase;
import com.google.idea.blaze.android.MockSdkUtil;
import com.google.idea.blaze.base.model.primitives.WorkspacePath;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.IndexingTestUtil;
import java.io.IOException;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests for BlazeSampleDataDirectoryProvider. */
@RunWith(JUnit4.class)
public class BlazeSampleDataDirectoryProviderTest extends BlazeAndroidIntegrationTestCase {
  private Module workspaceModule;
  private Module resModule;
  private VirtualFile workspaceDir;
  private VirtualFile resDir;

  @Before
  public void setup() {
    setProjectView(
        "directories:",
        "  com/google/example",
        "targets:",
        "  //com/google/example:main",
        "android_sdk_platform: android-25");
    MockSdkUtil.registerSdk(workspace, "25");

    workspaceDir = workspace.createDirectory(new WorkspacePath("com/google/example"));
    resDir = workspace.createFile(new WorkspacePath("com/google/example/res"));

    setTargetMap(android_binary("//com/google/example:main").res("res"));
    runFullBlazeSyncWithNoIssues();

    ModuleManager moduleManager = ModuleManager.getInstance(getProject());
    workspaceModule = moduleManager.findModuleByName(".workspace");
    resModule = moduleManager.findModuleByName("com.google.example.main");
  }

  @Test
  public void getSampleDataDirectory_nullIfNotResourceModule() {
    BlazeSampleDataDirectoryProvider provider =
        new BlazeSampleDataDirectoryProvider(workspaceModule);
    assertThat(provider.getSampleDataDirectory()).isNull();
  }

  @Test
  public void getSampleDataDirectory_parallelToResFolder() {
    BlazeSampleDataDirectoryProvider provider = new BlazeSampleDataDirectoryProvider(resModule);
    PathString expectedPath = toPathString(resDir).getParentOrRoot().resolve(FD_SAMPLE_DATA);

    assertThat(provider.getSampleDataDirectory()).isEqualTo(expectedPath);
  }

  @Test
  public void getOrCreateSampleDataDirectory_doesNothingIfNotResourceModule() throws IOException {
    BlazeSampleDataDirectoryProvider provider =
        new BlazeSampleDataDirectoryProvider(workspaceModule);
    PathString sampleDataDirPath =
        WriteAction.computeAndWait(() -> provider.getOrCreateSampleDataDirectory());

    assertThat(sampleDataDirPath).isNull();
    assertThat(workspaceDir.getParent().findChild(FD_SAMPLE_DATA)).isNull();
  }

  @Test
  public void getOrCreateSampleDataDirectory_createsDirectoryParallelToResFolderIfMissing()
      throws IOException {
    BlazeSampleDataDirectoryProvider provider = new BlazeSampleDataDirectoryProvider(resModule);

    PathString sampleDataDirPath =
        WriteAction.computeAndWait(() -> provider.getOrCreateSampleDataDirectory());
    VirtualFile sampleDataDir = sampleDataDirPath != null ? toVirtualFile(sampleDataDirPath) : null;

    assertThat(sampleDataDir).isNotNull();
    assertThat(sampleDataDir.getParent()).isEqualTo(resDir.getParent());
    assertThat(sampleDataDir.exists()).isTrue();

    // Workaround for https://youtrack.jetbrains.com/issue/IJPL-149706:
    // and flaky "already disposed" exceptions during tear-down.
    IndexingTestUtil.waitUntilIndexesAreReady(getProject());
  }
}
