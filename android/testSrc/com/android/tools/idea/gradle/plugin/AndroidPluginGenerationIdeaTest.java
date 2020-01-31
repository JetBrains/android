/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.gradle.plugin;

import com.android.Version;
import com.android.tools.idea.gradle.util.EmbeddedDistributionPaths;
import com.android.tools.idea.testing.IdeComponents;
import com.intellij.testFramework.PlatformTestCase;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;

import static com.intellij.openapi.util.io.FileUtilRt.createDirectory;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link AndroidPluginGenerationIdea}.
 */
public class AndroidPluginGenerationIdeaTest extends PlatformTestCase {
  private EmbeddedDistributionPaths myEmbeddedDistributionPaths;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myEmbeddedDistributionPaths = new IdeComponents(myProject).mockApplicationService(EmbeddedDistributionPaths.class);
  }

  public void testGetLatestKnownVersion() throws IOException {
    File rootFolderPath = createTempDirectory();

    File repo1Path = new File(rootFolderPath, "repo1");
    File repo2Path = new File(rootFolderPath, "repo2");
    createFakeRepo(repo1Path, "2.2.0");
    createFakeRepo(repo2Path, "2.3.0");
    when(myEmbeddedDistributionPaths.findAndroidStudioLocalMavenRepoPaths()).thenReturn(Arrays.asList(repo1Path, repo2Path));

    String version = LatestKnownPluginVersionProvider.INSTANCE.get();
    assertEquals("2.3.0", version);
  }

  private static void createFakeRepo(@NotNull File rootFolderPath, @NotNull String version) {
    String path = AndroidPluginInfo.GROUP_ID.replace('.', File.separatorChar);
    path += File.separatorChar + AndroidPluginInfo.ARTIFACT_ID + File.separatorChar + version;
    File pluginFolderPath = new File(rootFolderPath, path);
    assertTrue("Failed to create '" + pluginFolderPath + "'", createDirectory(pluginFolderPath));
  }

  public void testGetLatestKnownVersionWithNoRepos() {
    when(myEmbeddedDistributionPaths.findAndroidStudioLocalMavenRepoPaths()).thenReturn(Collections.emptyList());

    String version = LatestKnownPluginVersionProvider.INSTANCE.get();
    assertEquals(Version.ANDROID_GRADLE_PLUGIN_VERSION, version);
  }
}
