/*
 * Copyright 2016 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.android.resources.actions;

import static com.google.common.truth.Truth.assertThat;

import com.google.idea.blaze.base.BlazeTestCase;
import com.intellij.mock.MockVirtualFile;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests for BlazeCreateResourceUtils. */
@RunWith(JUnit4.class)
public class BlazeCreateResourceUtilsTest extends BlazeTestCase {

  // Set up a mock directory tree:
  // foo/src/
  //         res/
  //             values-fr-rCA/
  //                           misc/
  //                           foo.xml
  //             layout/
  //         A.java
  private MockVirtualFile xmlFile;
  private MockVirtualFile outOfPlaceSubdir;
  private MockVirtualFile valuesDir;
  private MockVirtualFile layoutDir;
  private MockVirtualFile resDir;
  private MockVirtualFile javaFile;
  private MockVirtualFile srcDir;
  private MockVirtualFile baseDir;

  @Before
  public void setUp() throws Exception {
    xmlFile = file("foo.xml");
    outOfPlaceSubdir = dir("misc");
    valuesDir = dir("values-fr-rCA", xmlFile, outOfPlaceSubdir);
    layoutDir = dir("layout");
    resDir = dir("res", valuesDir, layoutDir);
    javaFile = file("A.java");
    srcDir = dir("src", resDir, javaFile);
    baseDir = dir("foo", srcDir);
  }

  @Test
  public void getDirectoryFromContextResDirectory() {
    VirtualFile dir = BlazeCreateResourceUtils.getResDirFromDataContext(resDir);
    assertThat(dir).isNotNull();
    assertThat(dir.getName()).isEqualTo("res");
  }

  @Test
  public void getDirectoryFromContextResSubdirectory() {
    VirtualFile dir = BlazeCreateResourceUtils.getResDirFromDataContext(valuesDir);
    assertThat(dir).isNotNull();
    assertThat(dir.getName()).isEqualTo("res");
  }

  @Test
  public void getDirectoryFromContextResFile() {
    VirtualFile dir = BlazeCreateResourceUtils.getResDirFromDataContext(xmlFile);
    assertThat(dir).isNotNull();
    assertThat(dir.getName()).isEqualTo("res");
  }

  @Test
  public void getDirectoryFromContextOutOfPlaceSubdir() {
    // We don't try to guess the res/ ancestor from non-standard directory setups.
    VirtualFile dir = BlazeCreateResourceUtils.getResDirFromDataContext(outOfPlaceSubdir);
    assertThat(dir).isNull();
  }

  @Test
  public void getDirectoryFromContextJavaFile() {
    // This is just the first cut, where it isn't obvious that the A.java
    // file is associated with the neighboring res directory.
    // We'll have a second pass that looks at the rule map for possible choices.
    VirtualFile dir = BlazeCreateResourceUtils.getResDirFromDataContext(javaFile);
    assertThat(dir).isNull();
  }

  private static MockVirtualFile dir(@NotNull String name, MockVirtualFile... children) {
    MockVirtualFile dir = new MockVirtualFile(true, name);
    for (MockVirtualFile child : children) {
      dir.addChild(child);
    }
    return dir;
  }

  private static MockVirtualFile file(@NotNull String name) {
    return new MockVirtualFile(name);
  }
}
