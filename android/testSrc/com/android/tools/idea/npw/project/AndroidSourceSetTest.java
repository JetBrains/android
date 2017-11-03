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
package com.android.tools.idea.npw.project;

import com.android.builder.model.SourceProvider;
import org.jetbrains.annotations.Nullable;
import org.junit.Test;

import java.io.File;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Tests for {@link AndroidSourceSet}.
 */
public class AndroidSourceSetTest {
  @Test
  public void testToSourceProviderDirectories() {
    File javaDirectory = mock(File.class);
    File aidlDirectory = mock(File.class);
    File resDirectory = mock(File.class);

    AndroidProjectPaths mockProjectPaths = new AndroidProjectPaths() {
      @Nullable @Override public File getModuleRoot() { return null; }
      @Nullable @Override public File getSrcDirectory(@Nullable String packageName) { return javaDirectory; }
      @Nullable @Override public File getTestDirectory(@Nullable String packageName) { return null; }
      @Nullable @Override public File getResDirectory() { return resDirectory; }
      @Nullable @Override public File getAidlDirectory(@Nullable String packageName) { return aidlDirectory; }
      @Nullable @Override public File getManifestDirectory() { return null; }
    };

    AndroidSourceSet sourceSet = new AndroidSourceSet("", mockProjectPaths);
    SourceProvider sourceProvider = sourceSet.toSourceProvider();

    assertThat(sourceProvider.getJavaDirectories()).containsExactly(javaDirectory);
    assertThat(sourceProvider.getAidlDirectories()).containsExactly(aidlDirectory);
    assertThat(sourceProvider.getResDirectories()).containsExactly(resDirectory);

    assertThat(sourceProvider.getResourcesDirectories()).isEmpty();
    assertThat(sourceProvider.getRenderscriptDirectories()).isEmpty();
    assertThat(sourceProvider.getCDirectories()).isEmpty();
    assertThat(sourceProvider.getCppDirectories()).isEmpty();
    assertThat(sourceProvider.getAssetsDirectories()).isEmpty();
    assertThat(sourceProvider.getJniLibsDirectories()).isEmpty();
    assertThat(sourceProvider.getShadersDirectories()).isEmpty();
  }

  @Test
  public void testToSourceProviderWithEmptyDirectories() {
    AndroidProjectPaths mockProjectPaths = new AndroidProjectPaths() {
      @Nullable @Override public File getModuleRoot() { return null; }
      @Nullable @Override public File getSrcDirectory(@Nullable String packageName) { return null; }
      @Nullable @Override public File getTestDirectory(@Nullable String packageName) { return null; }
      @Nullable @Override public File getResDirectory() { return null; }
      @Nullable @Override public File getAidlDirectory(@Nullable String packageName) { return null; }
      @Nullable @Override public File getManifestDirectory() { return null; }
    };

    AndroidSourceSet sourceSet = new AndroidSourceSet("", mockProjectPaths);
    SourceProvider sourceProvider = sourceSet.toSourceProvider();

    assertThat(sourceProvider.getJavaDirectories()).isEmpty();
    assertThat(sourceProvider.getResourcesDirectories()).isEmpty();
    assertThat(sourceProvider.getAidlDirectories()).isEmpty();
    assertThat(sourceProvider.getRenderscriptDirectories()).isEmpty();
    assertThat(sourceProvider.getCDirectories()).isEmpty();
    assertThat(sourceProvider.getCppDirectories()).isEmpty();
    assertThat(sourceProvider.getResDirectories()).isEmpty();
    assertThat(sourceProvider.getAssetsDirectories()).isEmpty();
    assertThat(sourceProvider.getJniLibsDirectories()).isEmpty();
    assertThat(sourceProvider.getShadersDirectories()).isEmpty();
  }
}
