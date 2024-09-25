/*
 * Copyright 2023 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.common;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;

import com.google.common.truth.Truth8;
import java.nio.file.Path;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class LabelTest {

  @Test
  public void testGetPackage_nonEmpty() {
    Truth8.assertThat(Label.of("//package/path:rule").getPackage())
        .isEqualTo(Path.of("package/path"));
  }

  @Test
  public void testGetPackage_withWorkspace() {
    Truth8.assertThat(Label.of("@myws//package/path:rule").getPackage())
        .isEqualTo(Path.of("package/path"));
  }

  @Test
  public void testGetPackage_withQualifiedRootWorkspace() {
    Truth8.assertThat(Label.of("@//package/path:rule").getPackage())
        .isEqualTo(Path.of("package/path"));
    Truth8.assertThat(Label.of("@@//package/path:rule").getPackage())
        .isEqualTo(Path.of("package/path"));
  }

  @Test
  public void testGetName_simple() {
    Truth8.assertThat(Label.of("//package/path:rule").getName()).isEqualTo(Path.of("rule"));
  }

  @Test
  public void testGetName_withWorkspace() {
    Truth8.assertThat(Label.of("@someworkspace//package/path:rule").getName())
        .isEqualTo(Path.of("rule"));
  }

  @Test
  public void testGetPackage_empty() {
    Truth8.assertThat(Label.of("//:rule").getPackage()).isEqualTo(Path.of(""));
  }

  @Test
  public void testGetPackage_empty_withWorkspace() {
    Truth8.assertThat(Label.of("@workspace//:rule").getPackage()).isEqualTo(Path.of(""));
  }

  @Test
  public void testGetName_withDirectory() {
    Truth8.assertThat(Label.of("//package/path:source/Class.java").getName())
        .isEqualTo(Path.of("source/Class.java"));
  }

  @Test
  public void testGetName_emptyPackage() {
    Truth8.assertThat(Label.of("//:rule").getName()).isEqualTo(Path.of("rule"));
  }

  @Test
  public void testGetName_emptyPackage_withWorkspace() {
    Truth8.assertThat(Label.of("@foo//:rule").getName()).isEqualTo(Path.of("rule"));
  }

  @Test
  public void testNew_badPackage() {
    assertThrows(IllegalArgumentException.class, () -> Label.of("package/path:rule"));
  }

  @Test
  public void testNew_noName() {
    assertThrows(IllegalArgumentException.class, () -> Label.of("//package/path"));
  }

  @Test
  public void testToFilePath() {
    Truth8.assertThat(Label.of("//package/path:BUILD").toFilePath())
        .isEqualTo(Path.of("package/path/BUILD"));
  }

  @Test
  public void testGetWorkspace_empty() {
    assertThat(Label.of("//package:rule").getWorkspaceName()).isEmpty();
  }

  @Test
  public void testGetWorkspace_nonEmpty() {
    assertThat(Label.of("@myworkspace//package:rule").getWorkspaceName()).isEqualTo("myworkspace");
  }

  @Test
  public void testGetWorkspace_doubleAt() {
    assertThat(Label.of("@@myws//package:rule").getWorkspaceName()).isEqualTo("myws");
  }

  @Test
  public void testNew_badWorkspace() {
    assertThrows(IllegalArgumentException.class, () -> Label.of("@work:space//package/path"));
  }

  @Test
  public void doubleAtNormalization() {
    assertThat(Label.of("@abc//:def")).isEqualTo(Label.of("@@abc//:def"));
  }

  @Test
  public void siblingWithName() {
    assertThat(Label.of("//some/path:def").siblingWithName("name"))
        .isEqualTo(Label.of("//some/path:name"));
  }

  @Test
  public void siblingWithPathAndName() {
    assertThat(Label.of("@abc//some/path:def").siblingWithPathAndName("other/path:name"))
        .isEqualTo(Label.of("@@abc//some/path/other/path:name"));
  }
}
