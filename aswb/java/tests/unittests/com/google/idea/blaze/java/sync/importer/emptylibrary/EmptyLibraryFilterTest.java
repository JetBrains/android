/*
 * Copyright 2020 The Bazel Authors. All rights reserved.
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

package com.google.idea.blaze.java.sync.importer.emptylibrary;

import static com.google.common.truth.Truth.assertThat;

import com.google.idea.blaze.base.BlazeTestCase;
import com.google.idea.blaze.base.command.buildresult.SourceArtifact;
import com.google.idea.blaze.base.io.FileOperationProvider;
import com.google.idea.common.experiments.ExperimentService;
import com.google.idea.common.experiments.MockExperimentService;
import com.intellij.testFramework.rules.TempDirectory;
import java.io.File;
import java.io.IOException;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Unit tests for {@link EmptyLibraryFilter}. */
@RunWith(JUnit4.class)
public class EmptyLibraryFilterTest extends BlazeTestCase {
  @Rule public TempDirectory tempDirectory = new TempDirectory();

  @Override
  protected void initTest(Container applicationServices, Container projectServices) {
    applicationServices.register(FileOperationProvider.class, new FileOperationProvider());

    MockExperimentService experimentService = new MockExperimentService();
    applicationServices.register(ExperimentService.class, experimentService);
  }

  @Test
  public void isEmpty_nonexistent() throws IOException {
    File jar = new File("nonexistent.jar");
    assertThat(EmptyLibraryFilter.isEmpty(new SourceArtifact(jar))).isTrue();
  }

  @Test
  public void isEmpty_trulyEmpty() throws IOException {
    File jar = JarBuilder.newEmptyJar(tempDirectory).build();
    assertThat(EmptyLibraryFilter.isEmpty(new SourceArtifact(jar))).isTrue();
  }

  @Test
  public void isEmpty_largeButManifestOnly() throws IOException {
    File jar = JarBuilder.newEmptyJar(tempDirectory).addManifest().bloatBy(200).build();
    assertThat(EmptyLibraryFilter.isEmpty(new SourceArtifact(jar))).isTrue();
  }

  @Test
  public void isEmpty_largeButOnlyManifestAndDirectories() throws IOException {
    File jar =
        JarBuilder.newEmptyJar(tempDirectory)
            .addDirectory("dir1/")
            .addDirectory("dir2/")
            .addManifest()
            .build();
    assertThat(EmptyLibraryFilter.isEmpty(new SourceArtifact(jar))).isTrue();
  }

  @Test
  public void isEmpty_smallButNonEmpty() throws IOException {
    File jar =
        JarBuilder.newEmptyJar(tempDirectory)
            .addManifest()
            .addFile("com/google/example/A.java", "package com.google.example; class A {}")
            .build();
    assertThat(EmptyLibraryFilter.isEmpty(new SourceArtifact(jar))).isFalse();
  }

  @Test
  public void isEmpty_largeAndNonEmpty() throws IOException {
    File jar =
        JarBuilder.newEmptyJar(tempDirectory)
            .addManifest()
            .addDirectory("dir1")
            .addDirectory("dir2")
            .addFile("com/google/example/A.java", "package com.google.example; public class A {}")
            .addFile("com/google/example/B.java", "package com.google.example; public class B {}")
            .addFile("com/google/example/C.java", "package com.google.example; public class C {}")
            .build();
    assertThat(EmptyLibraryFilter.isEmpty(new SourceArtifact(jar))).isFalse();
  }
}
