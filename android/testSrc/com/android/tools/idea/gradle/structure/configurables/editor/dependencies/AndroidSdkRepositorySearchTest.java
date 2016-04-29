/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.tools.idea.gradle.structure.configurables.editor.dependencies;

import com.android.builder.model.AndroidProject;
import com.android.builder.model.Variant;
import com.android.ide.common.repository.GradleCoordinate;
import com.android.tools.idea.gradle.structure.configurables.model.ModuleMergedModel;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.util.Collections;

import static com.android.tools.idea.AndroidTestCaseHelper.getAndroidSdkPath;
import static org.fest.assertions.Assertions.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link AndroidSdkRepositorySearch}.
 */
public class AndroidSdkRepositorySearchTest {
  private File myAndroidSdkPath;
  private AndroidSdkRepositorySearch myRepositorySearch;

  @Before
  public void setUp() {
    myAndroidSdkPath = getAndroidSdkPath();

    AndroidProject androidProject = mock(AndroidProject.class);
    when(androidProject.getVariants()).thenReturn(Collections.<Variant>emptyList());

    ModuleMergedModel model = mock(ModuleMergedModel.class);
    when(model.getAndroidProject()).thenReturn(androidProject);

    myRepositorySearch = new AndroidSdkRepositorySearch(model, myAndroidSdkPath);
  }

  @Test
  public void testGetLibraryCoordinate() {
    GradleCoordinate coordinate = AndroidSdkRepositorySearch.getLibraryCoordinate("appcompat-v7", myAndroidSdkPath, false);
    assertNotNull(coordinate);
    assertEquals("com.android.support", coordinate.getGroupId());
    assertEquals("appcompat-v7", coordinate.getArtifactId());
    assertThat(coordinate.getRevision()).isNotNull().isNotEmpty();
  }

  @Test
  public void testStartWithGroupId() throws IOException {
    ArtifactRepositorySearch.Request request = new ArtifactRepositorySearch.Request("support-annotations", "com.android.support", 0, 0);
    SearchResult result = myRepositorySearch.start(request);
    assertThat(result.data).hasSize(1);
    String entry = result.data.get(0);
    assertThat(entry).startsWith("com.android.support:support-annotations:");
  }

  @Test
  public void testStartWithoutGroupId() throws IOException {
    ArtifactRepositorySearch.Request request = new ArtifactRepositorySearch.Request("support-annotations", null, 0, 0);
    SearchResult result = myRepositorySearch.start(request);
    assertThat(result.data).hasSize(1);
    String entry = result.data.get(0);
    assertThat(entry).startsWith("com.android.support:support-annotations:");
  }
}