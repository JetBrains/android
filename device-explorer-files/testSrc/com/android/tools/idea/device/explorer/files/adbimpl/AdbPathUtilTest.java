/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.device.explorer.files.adbimpl;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

import static com.google.common.truth.Truth.assertThat;

public class AdbPathUtilTest  {
  @Test
  public void testGetFileName() {
    assertThat(AdbPathUtil.getFileName("")).isEqualTo("");
    assertThat(AdbPathUtil.getFileName("/")).isEqualTo("");
    assertThat(AdbPathUtil.getFileName("/foo")).isEqualTo("foo");
    assertThat(AdbPathUtil.getFileName("/foo/bar")).isEqualTo("bar");
    assertThat(AdbPathUtil.getFileName("/foo/blah/bar-test.txt")).isEqualTo("bar-test.txt");
  }

  @Test
  public void testGetParentPath() {
    assertThat(AdbPathUtil.getParentPath("")).isEqualTo("");
    assertThat(AdbPathUtil.getParentPath("/")).isEqualTo("");
    assertThat(AdbPathUtil.getParentPath("/foo")).isEqualTo("/");
    assertThat(AdbPathUtil.getParentPath("/foo/")).isEqualTo("/");
    assertThat(AdbPathUtil.getParentPath("/foo/bar")).isEqualTo("/foo");
    assertThat(AdbPathUtil.getParentPath("/foo/bar/")).isEqualTo("/foo");
    assertThat(AdbPathUtil.getParentPath("/foo/blah/bar-test.txt")).isEqualTo("/foo/blah");
  }

  @Test
  public void testGetEscapedPath() {
    assertThat(AdbPathUtil.getEscapedPath("")).isEqualTo("");
    assertThat(AdbPathUtil.getEscapedPath("/")).isEqualTo("/");
    assertThat(AdbPathUtil.getEscapedPath("/foo\\")).isEqualTo("/foo\\\\");
    assertThat(AdbPathUtil.getEscapedPath("/foo/&")).isEqualTo("/foo/\\&");
    assertThat(AdbPathUtil.getEscapedPath("/foo/blah/bar-#test.txt")).isEqualTo("/foo/blah/bar-\\#test.txt");
  }

  @Test
  public void testGetSegments() {
    assertThat(AdbPathUtil.getSegments("")).isEqualTo(Collections.emptyList());
    assertThat(AdbPathUtil.getSegments("/")).isEqualTo(Collections.emptyList());
    assertThat(AdbPathUtil.getSegments("/foo")).isEqualTo(Collections.singletonList("foo"));
    assertThat(AdbPathUtil.getSegments("/foo/")).isEqualTo(Collections.singletonList("foo"));
    assertThat(AdbPathUtil.getSegments("/foo/bar")).isEqualTo(Arrays.asList("foo", "bar"));
    assertThat(AdbPathUtil.getSegments("/foo/bar/")).isEqualTo(Arrays.asList("foo", "bar"));
    assertThat(AdbPathUtil.getSegments("/foo/blah/bar-test.txt")).isEqualTo(Arrays.asList("foo", "blah", "bar-test.txt"));
  }

  @Test
  public void testResolve() {
    assertThat(AdbPathUtil.resolve("", "")).isEqualTo("");
    assertThat(AdbPathUtil.resolve("/foo", "")).isEqualTo("/foo");
    assertThat(AdbPathUtil.resolve("/foo/", "")).isEqualTo("/foo/");
    assertThat(AdbPathUtil.resolve("/", "foo")).isEqualTo("/foo");
    assertThat(AdbPathUtil.resolve("/foo", "")).isEqualTo("/foo");
    assertThat(AdbPathUtil.resolve("/bar", "/foo")).isEqualTo("/foo");
    assertThat(AdbPathUtil.resolve("/bar/", "/foo")).isEqualTo("/foo");
    assertThat(AdbPathUtil.resolve("/bar", "foo")).isEqualTo("/bar/foo");
    assertThat(AdbPathUtil.resolve("/bar/", "foo")).isEqualTo("/bar/foo");
    assertThat(AdbPathUtil.resolve("bar", "foo")).isEqualTo("bar/foo");
    assertThat(AdbPathUtil.resolve("bar/", "foo")).isEqualTo("bar/foo");
    assertThat(AdbPathUtil.resolve("/bar/foo2", "bar/foo")).isEqualTo("/bar/foo2/bar/foo");
    assertThat(AdbPathUtil.resolve("/bar/foo2/", "bar/foo")).isEqualTo("/bar/foo2/bar/foo");
    assertThat(AdbPathUtil.resolve("/bar/foo2", "/bar/foo")).isEqualTo("/bar/foo");
    assertThat(AdbPathUtil.resolve("/bar/foo2/", "/bar/foo")).isEqualTo("/bar/foo");
  }
}
