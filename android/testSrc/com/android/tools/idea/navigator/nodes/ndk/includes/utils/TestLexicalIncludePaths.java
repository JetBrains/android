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
package com.android.tools.idea.navigator.nodes.ndk.includes.utils;

import org.junit.Test;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import static com.google.common.truth.Truth.assertThat;

public class TestLexicalIncludePaths {

  @Test
  public void testCommonRelativeIncludePath1() {
    List<String> paths = new ArrayList<>();
    paths.add("usr/one/two");
    paths.add("usr/one/three");
    String common = LexicalIncludePaths.findCommonParentFolder(paths);
    assertThat(common).isEqualTo("usr/one");
  }

  @Test
  public void testCommonRelativeIncludePath2() {
    List<String> paths = new ArrayList<>();
    paths.add("usr/one/two");
    paths.add("usr/one/two");
    String common = LexicalIncludePaths.findCommonParentFolder(paths);
    assertThat(common).isEqualTo("usr/one/two");
  }

  @Test
  public void testCommonRelativeIncludePath3() {
    List<String> paths = new ArrayList<>();
    paths.add("/usr/one/two");
    paths.add("/usr/one/three");
    String common = LexicalIncludePaths.findCommonParentFolder(paths);
    assertThat(common).isEqualTo("/usr/one");
  }

  @Test
  public void testCommonRelativeIncludePathWindowsDifferentDrive() {
    List<String> paths = new ArrayList<>();
    paths.add("c:/usr/one/two");
    paths.add("d:/usr/one/three");
    String common = LexicalIncludePaths.findCommonParentFolder(paths);
    assertThat(common).isEqualTo("");
  }

  @Test
  public void testCommonRelativeIncludePathWindowsSameDrive() {
    List<String> paths = new ArrayList<>();
    paths.add("c:/usr/one/two");
    paths.add("c:/usr/one/three");
    String common = LexicalIncludePaths.findCommonParentFolder(paths);
    assertThat(common).isEqualTo("c:/usr/one");
  }

  @Test
  public void testCommonRelativeIncludePathEmptyList() {
    List<String> paths = new ArrayList<>();
    String common = LexicalIncludePaths.findCommonParentFolder(paths);
    assertThat(common).isEqualTo("");
  }


  @Test
  public void testHeaderExtension() {
    assertThat(LexicalIncludePaths.hasHeaderExtension("header")).isTrue();
    assertThat(LexicalIncludePaths.hasHeaderExtension("header.h")).isTrue();
    assertThat(LexicalIncludePaths.hasHeaderExtension("header.inl")).isTrue();
    assertThat(LexicalIncludePaths.hasHeaderExtension("header.cpp")).isFalse();
  }

  @Test
  public void testMatchRegex() {
    assertThat(LexicalIncludePaths.matchFolderToRegex(Pattern.compile("/.*"), new File("\\header.h")).matches()).isTrue();
    assertThat(LexicalIncludePaths.matchFolderToRegex(Pattern.compile("/.*"), new File("/header.h")).matches()).isTrue();
  }

  @Test
  public void testTrimPathSeparator() {
    assertThat(LexicalIncludePaths.trimPathSeparators("a")).isEqualTo("a");
    assertThat(LexicalIncludePaths.trimPathSeparators("/a")).isEqualTo("a");
    assertThat(LexicalIncludePaths.trimPathSeparators("a/")).isEqualTo("a");
    assertThat(LexicalIncludePaths.trimPathSeparators("/a/")).isEqualTo("a");
  }
}
