/*
 * Copyright 2024 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.qsync.java;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableSet;
import com.google.idea.blaze.common.artifact.CachedArtifact;
import com.google.idea.blaze.exception.BuildException;
import com.google.idea.blaze.qsync.java.SrcJarInnerPathFinder.AllowPackagePrefixes;
import com.google.idea.blaze.qsync.java.SrcJarInnerPathFinder.JarPath;
import java.nio.file.Path;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@RunWith(JUnit4.class)
public class SourceJarInnerPathsAndPackagePrefixesTest {

  @Rule public final MockitoRule mockito = MockitoJUnit.rule();

  @Mock SrcJarInnerPathFinder pathFinder;

  private SourceJarInnerPathsAndPackagePrefixes metadata;

  @Before
  public void setup() {
    metadata = new SourceJarInnerPathsAndPackagePrefixes(pathFinder);
  }

  @Test
  public void extract_single_path() throws BuildException {
    CachedArtifact artifact = new CachedArtifact(Path.of("/path/to/file.srcjar"));
    when(pathFinder.findInnerJarPaths(eq(artifact),
        eq(AllowPackagePrefixes.ALLOW_NON_EMPTY_PACKAGE_PREFIXES), any()))
        .thenReturn(ImmutableSet.of(JarPath.create("root/path", "com.package")));
    assertThat(metadata.extract(artifact, new Object())).isEqualTo("root/path=com.package");
  }

  @Test
  public void parse_single_path() {
    assertThat(metadata.toJarPaths("root/path=com.package")).containsExactly(
        JarPath.create("root/path", "com.package"));
  }

  @Test
  public void extract_jar_root() throws BuildException {
    CachedArtifact artifact = new CachedArtifact(Path.of("/path/to/file.srcjar"));
    when(pathFinder.findInnerJarPaths(eq(artifact),
        eq(AllowPackagePrefixes.ALLOW_NON_EMPTY_PACKAGE_PREFIXES), any()))
        .thenReturn(ImmutableSet.of(JarPath.create("", "com.package")));
    assertThat(metadata.extract(artifact, new Object())).isEqualTo("=com.package");
  }

  @Test
  public void parse_jar_root() {
    assertThat(metadata.toJarPaths("=com.package")).containsExactly(
        JarPath.create("", "com.package"));
  }

  @Test
  public void extract_multi_path() throws BuildException {
    CachedArtifact artifact = new CachedArtifact(Path.of("/path/to/file.srcjar"));
    when(pathFinder.findInnerJarPaths(eq(artifact),
        eq(AllowPackagePrefixes.ALLOW_NON_EMPTY_PACKAGE_PREFIXES), any()))
        .thenReturn(ImmutableSet.of(JarPath.create("root/path", "com.package"),
            JarPath.create("root2", "com.otherpackage"),
            JarPath.create("anotherroot", "com.another")));
    assertThat(metadata.extract(artifact, new Object())).isEqualTo(
        "root/path=com.package;root2=com.otherpackage;anotherroot=com.another");
  }

  @Test
  public void parse_multi_path() {
    assertThat(metadata.toJarPaths(
        "root/path=com.package;root2=com.otherpackage;anotherroot=com.another")).containsExactly(
        JarPath.create("root/path", "com.package"),
        JarPath.create("root2", "com.otherpackage"),
        JarPath.create("anotherroot", "com.another"));
  }

  @Test
  public void parse_null() {
    assertThat(metadata.toJarPaths(null)).containsExactly(JarPath.create("", ""));
  }

  @Test
  public void extract_package_root() throws BuildException {
    CachedArtifact artifact = new CachedArtifact(Path.of("/path/to/file.srcjar"));
    when(pathFinder.findInnerJarPaths(eq(artifact),
        eq(AllowPackagePrefixes.ALLOW_NON_EMPTY_PACKAGE_PREFIXES), any()))
        .thenReturn(ImmutableSet.of(JarPath.create("", "")));
    assertThat(metadata.extract(artifact, new Object())).isEqualTo("=");
  }

  @Test
  public void parse_package_root() {
    assertThat(metadata.toJarPaths("=")).containsExactly(JarPath.create("", ""));
  }


}
