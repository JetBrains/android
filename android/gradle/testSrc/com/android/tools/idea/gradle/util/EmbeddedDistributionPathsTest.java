/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.gradle.util;

import static com.android.tools.idea.gradle.util.EmbeddedDistributionPaths.doFindAndroidStudioLocalMavenRepoPaths;
import static com.android.tools.idea.gradle.util.EmbeddedDistributionPaths.getJdkRootPathFromSourcesRoot;
import static com.android.tools.idea.util.StudioPathManager.getSourcesRoot;
import static com.google.common.truth.Truth.assertThat;

import com.android.tools.idea.flags.StudioFlags;
import com.android.tools.idea.util.StudioPathManager;
import com.intellij.testFramework.rules.TempDirectory;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.Rule;
import org.junit.Test;

/**
 * Tests for {@link EmbeddedDistributionPaths}.
 */
public class EmbeddedDistributionPathsTest {

  @Rule
  public TempDirectory temporaryDirectory = new TempDirectory();

  @Test
  public void testFindAndroidStudioLocalMavenRepoPaths() {
    List<Path> expectedRepo = Arrays.asList(
      StudioPathManager.resolvePathFromSourcesRoot("out/repo"),
      StudioPathManager.resolvePathFromSourcesRoot("out/studio/repo"),
      StudioPathManager.resolvePathFromSourcesRoot("prebuilts/tools/common/m2/repository"),
      StudioPathManager.resolvePathFromSourcesRoot("../maven/repository"),
      Paths.get(System.getProperty("java.io.tmpdir"), "offline-maven-repo")
    );
    expectedRepo = expectedRepo.stream().filter(Files::isDirectory).collect(Collectors.toList());
    // Invoke the method to test.
    List<Path> paths = doFindAndroidStudioLocalMavenRepoPaths().stream().map(File::toPath).collect(Collectors.toList());
    assertThat(paths).hasSize(expectedRepo.size());
    assertThat(paths).containsExactlyElementsIn(expectedRepo);
  }

  @Test
  public void testGetJdkRootPathFromSourcesRoot() {
    @SuppressWarnings("deprecation") String root = getSourcesRoot();
    List<Path> jdk8Paths = Stream.of("win64", "linux", "mac/Contents/Home")
      .map((x) -> Path.of(root + "/prebuilts/studio/jdk/jdk8/" + x))
      .collect(Collectors.toList());
    assertThat(jdk8Paths).contains(getJdkRootPathFromSourcesRoot("prebuilts/studio/jdk/jdk8"));
    List<Path> jdk11Paths = Stream.of("win", "linux", "mac/Contents/Home")
      .map((x) -> Path.of(root + "/prebuilts/studio/jdk/jdk11/" + x))
      .collect(Collectors.toList());
    assertThat(jdk11Paths).contains(getJdkRootPathFromSourcesRoot("prebuilts/studio/jdk/jdk11"));
    List<Path> jdk17Paths = Stream.of("win", "linux", "mac/Contents/Home")
      .map((x) -> Path.of(root + "/prebuilts/studio/jdk/jdk17/" + x))
      .collect(Collectors.toList());
    assertThat(jdk17Paths).contains(getJdkRootPathFromSourcesRoot("prebuilts/studio/jdk/jdk17"));
  }


  @Test
  public void testStudioFlag() throws IOException {
    Path additionalPath = temporaryDirectory.newDirectory("additional repo 1").toPath().toRealPath();
    Path additionalPathLinkedTo = temporaryDirectory.newDirectory("additional repo 2").toPath().toRealPath();
    Path linkToDirectory = temporaryDirectory.newDirectory("link parent").toPath().resolve("link to directory");
    Files.createSymbolicLink(linkToDirectory, additionalPathLinkedTo);

    List<Path> paths;
    StudioFlags.DEVELOPMENT_OFFLINE_REPO_LOCATION.override(additionalPath + File.pathSeparator + linkToDirectory);
    try {
      paths = doFindAndroidStudioLocalMavenRepoPaths().stream().map(File::toPath).collect(Collectors.toList());
    } finally {
      StudioFlags.DEVELOPMENT_OFFLINE_REPO_LOCATION.clearOverride();
    }
    assertThat(paths).containsAllOf(additionalPath, additionalPathLinkedTo);
    assertThat(paths).doesNotContain(linkToDirectory);
    assertThat(doFindAndroidStudioLocalMavenRepoPaths().stream().map(File::toPath).collect(Collectors.toList())).containsNoneOf(additionalPath, additionalPathLinkedTo, linkToDirectory);
  }
}
