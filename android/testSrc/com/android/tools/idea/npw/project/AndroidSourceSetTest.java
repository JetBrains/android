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

import com.android.annotations.NonNull;
import com.android.builder.model.SourceProvider;
import org.junit.Test;

import java.io.File;
import java.util.Collection;
import java.util.Collections;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Tests for {@link AndroidSourceSet}.
 */
public class AndroidSourceSetTest {
  @Test
  public void testToSourceProviderDirectories() {
    File javaDirectory = mock(File.class);
    File resourceDirectory = mock(File.class);
    File aidlDirectory = mock(File.class);
    File renderscriptDirectory = mock(File.class);
    File cDirectory = mock(File.class);
    File cppDirectory = mock(File.class);
    File resDirectory = mock(File.class);
    File assetsDirectory = mock(File.class);
    File jniLibsDirectory = mock(File.class);
    File shadersDirectory = mock(File.class);

    AndroidProjectPaths mockProjectPaths = new AndroidProjectPaths(mock(File.class), mock(File.class), new SourceProvider() {
      @NonNull @Override public String getName() { return ""; }
      @NonNull @Override public File getManifestFile() { return mock(File.class); }
      @NonNull @Override public Collection<File> getJavaDirectories() { return Collections.singleton(javaDirectory); }
      @NonNull @Override public Collection<File> getResourcesDirectories() { return Collections.singleton(resourceDirectory); }
      @NonNull @Override public Collection<File> getAidlDirectories() { return Collections.singleton(aidlDirectory); }
      @NonNull @Override public Collection<File> getRenderscriptDirectories() { return Collections.singleton(renderscriptDirectory); }
      @NonNull @Override public Collection<File> getCDirectories() { return Collections.singleton(cDirectory); }
      @NonNull @Override public Collection<File> getCppDirectories() { return Collections.singleton(cppDirectory); }
      @NonNull @Override public Collection<File> getResDirectories() { return Collections.singleton(resDirectory); }
      @NonNull @Override public Collection<File> getAssetsDirectories() { return Collections.singleton(assetsDirectory); }
      @NonNull @Override public Collection<File> getJniLibsDirectories() { return Collections.singleton(jniLibsDirectory); }
      @NonNull @Override public Collection<File> getShadersDirectories() { return Collections.singleton(shadersDirectory); }
    });

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
    AndroidProjectPaths mockProjectPaths = new AndroidProjectPaths(mock(File.class), mock(File.class), new SourceProvider() {
      @NonNull @Override public String getName() { return ""; }
      @NonNull @Override public File getManifestFile() { return mock(File.class); }
      @NonNull @Override public Collection<File> getJavaDirectories() { return Collections.emptyList(); }
      @NonNull @Override public Collection<File> getResourcesDirectories() { return Collections.emptyList(); }
      @NonNull @Override public Collection<File> getAidlDirectories() { return Collections.emptyList(); }
      @NonNull @Override public Collection<File> getRenderscriptDirectories() { return Collections.emptyList(); }
      @NonNull @Override public Collection<File> getCDirectories() { return Collections.emptyList(); }
      @NonNull @Override public Collection<File> getCppDirectories() { return Collections.emptyList(); }
      @NonNull @Override public Collection<File> getResDirectories() { return Collections.emptyList(); }
      @NonNull @Override public Collection<File> getAssetsDirectories() { return Collections.emptyList(); }
      @NonNull @Override public Collection<File> getJniLibsDirectories() { return Collections.emptyList(); }
      @NonNull @Override public Collection<File> getShadersDirectories() { return Collections.emptyList(); }
    });

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
