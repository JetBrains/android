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

import static com.intellij.openapi.util.io.FileUtilRt.createDirectory;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import com.android.Version;
import com.android.tools.idea.IdeInfo;
import com.android.tools.idea.gradle.util.EmbeddedDistributionPaths;
import com.android.tools.idea.testing.IdeComponents;
import com.intellij.testFramework.PlatformTestCase;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import org.jetbrains.annotations.NotNull;
import org.mockito.MockedStatic;

/**
 * Tests for {@link AndroidPluginGenerationIdea}.
 */
public class AndroidPluginGenerationIdeaTest extends PlatformTestCase {
  private EmbeddedDistributionPaths myEmbeddedDistributionPaths;
  private MockedStatic<IdeInfo> mockIdeInfo;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    mockIdeInfo = mockStatic(IdeInfo.class);
    myEmbeddedDistributionPaths = new IdeComponents(myProject).mockApplicationService(EmbeddedDistributionPaths.class);
  }

  @Override
  protected void tearDown() throws Exception {
    try {
      mockIdeInfo.close();
    }
    catch (Throwable e) {
      addSuppressedException(e);
    }
    finally {
      super.tearDown();
    }
  }

  public void testGetLatestKnownVersion() throws IOException {
    File rootFolderPath = createTempDirectory();

    File repo1Path = new File(rootFolderPath, "repo1");
    File repo2Path = new File(rootFolderPath, "repo2");
    createFakeRepo(repo1Path, "2.2.0");
    createFakeRepo(repo2Path, "2.3.0");
    when(myEmbeddedDistributionPaths.findAndroidStudioLocalMavenRepoPaths()).thenReturn(Arrays.asList(repo1Path, repo2Path));
    when(mockIdeInfo().isAndroidStudio()).thenReturn(true);

    String version = LatestKnownPluginVersionProvider.INSTANCE.get();
    assertEquals("2.3.0", version);
  }

  public void testGetLatestKnownPluginVersionShouldReturnLatestKnownVersionWhenRunningInAndroidStudio() {
    when(myEmbeddedDistributionPaths.findAndroidStudioLocalMavenRepoPaths()).thenReturn(Collections.emptyList());
    when(mockIdeInfo().isAndroidStudio()).thenReturn(true);

    String version = LatestKnownPluginVersionProvider.INSTANCE.get();
    assertEquals(Version.ANDROID_GRADLE_PLUGIN_VERSION, version);
  }


  public void testGetLatestKnownPluginVersionShouldReturnLatestKnownStableVersionWhenRunningInIdea() {
    when(myEmbeddedDistributionPaths.findAndroidStudioLocalMavenRepoPaths()).thenReturn(Collections.emptyList());
    when(mockIdeInfo().isAndroidStudio()).thenReturn(false);

    String version = LatestKnownPluginVersionProvider.INSTANCE.get();
    assertEquals(AndroidGradlePluginVersion.LATEST_STABLE_VERSION, version);
  }

  private IdeInfo mockIdeInfo() {
    IdeInfo ideInfo = mock(IdeInfo.class);
    mockIdeInfo.when(IdeInfo::getInstance).thenReturn(ideInfo);
    return ideInfo;
  }

  private void createFakeRepo(@NotNull File rootFolderPath, @NotNull String version) {
    String path = AndroidPluginInfo.GROUP_ID.replace('.', File.separatorChar);
    path += File.separatorChar + AndroidPluginInfo.ARTIFACT_ID + File.separatorChar + version;
    File pluginFolderPath = new File(rootFolderPath, path);
    assertTrue("Failed to create '" + pluginFolderPath + "'", createDirectory(pluginFolderPath));
  }
}
