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
package com.android.tools.idea.explorer.adbimpl;

import org.junit.Test;

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
