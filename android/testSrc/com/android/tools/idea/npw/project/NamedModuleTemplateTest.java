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
import com.android.tools.idea.projectsystem.AndroidModulePaths;
import com.android.tools.idea.projectsystem.NamedModuleTemplate;
import com.google.common.collect.ImmutableList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Test;

import java.io.File;
import java.util.Collections;
import java.util.List;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Tests for {@link NamedModuleTemplate}.
 */
public class NamedModuleTemplateTest {
  @Test
  public void testToSourceProviderDirectories() {
    File javaDirectory = mock(File.class);
    File aidlDirectory = mock(File.class);
    List<File> resDirectories = ImmutableList.of(mock(File.class));

    AndroidModulePaths mockProjectPaths = new AndroidModulePaths() {
      @Nullable @Override public File getModuleRoot() { return null; }
      @Nullable @Override public File getSrcDirectory(@Nullable String packageName) { return javaDirectory; }
      @Nullable @Override public File getTestDirectory(@Nullable String packageName) { return null; }
      @NotNull
      @Override public List<File> getResDirectories() { return resDirectories; }
      @Nullable @Override public File getAidlDirectory(@Nullable String packageName) { return aidlDirectory; }
      @Nullable @Override public File getManifestDirectory() { return null; }
    };

    NamedModuleTemplate moduleTemplate = new NamedModuleTemplate("", mockProjectPaths);
    SourceProvider sourceProvider = AndroidGradleModuleUtils.getSourceProvider(moduleTemplate);

    assertThat(sourceProvider.getJavaDirectories()).containsExactly(javaDirectory);
    assertThat(sourceProvider.getAidlDirectories()).containsExactly(aidlDirectory);
    assertThat(sourceProvider.getResDirectories()).containsExactlyElementsIn(resDirectories);

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
    AndroidModulePaths mockProjectPaths = new AndroidModulePaths() {
      @Nullable @Override public File getModuleRoot() { return null; }
      @Nullable @Override public File getSrcDirectory(@Nullable String packageName) { return null; }
      @Nullable @Override public File getTestDirectory(@Nullable String packageName) { return null; }
      @NotNull @Override public List<File> getResDirectories() { return Collections.emptyList(); }
      @Nullable @Override public File getAidlDirectory(@Nullable String packageName) { return null; }
      @Nullable @Override public File getManifestDirectory() { return null; }
    };

    NamedModuleTemplate moduleTemplate = new NamedModuleTemplate("", mockProjectPaths);
    SourceProvider sourceProvider = AndroidGradleModuleUtils.getSourceProvider(moduleTemplate);

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
