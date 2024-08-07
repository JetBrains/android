/*
 * Copyright 2017 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.base.run.filter;

import static com.google.common.truth.Truth.assertThat;
import static com.google.idea.blaze.base.run.filter.BlazeTargetFilter.TARGET_PATTERN;

import java.util.regex.Matcher;
import javax.annotation.Nullable;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Unit tests for {@link BlazeTargetFilter}. */
@RunWith(JUnit4.class)
public class BlazeTargetFilterTest {

  @Test
  public void testSimpleTarget() {
    String line = "Something //package:target_name something else";
    assertThat(findMatch(line)).isEqualTo("//package:target_name");
  }

  @Test
  public void testExternalWorkspaceTarget() {
    String line = "Something @ext//package:target_name something else";
    assertThat(findMatch(line)).isEqualTo("@ext//package:target_name");
  }

  @Test
  public void testQuotedTarget() {
    String line = "Something '//package:target_name' something else";
    assertThat(findMatch(line)).isEqualTo("//package:target_name");
  }

  @Test
  public void testUnusualCharsInTarget() {
    String line = "Something //Package-._$():T0+,=~#target_@name something else";
    assertThat(findMatch(line)).isEqualTo("//Package-._$():T0+,=~#target_@name");
  }

  @Test
  public void testMatchesTargetInSubDirectory() {
    String line = "Something //package:path/to/file";
    assertThat(findMatch(line)).isEqualTo("//package:path/to/file");
  }

  @Test
  public void testMatchesTargetAtStartOfLine() {
    String line = "//package:name";
    assertThat(findMatch(line)).isEqualTo("//package:name");
  }

  @Test
  public void testDoesNotMatchMidText() {
    String line = "text//path/to/package";
    assertThat(findMatch(line)).isNull();
  }

  @Nullable
  private static String findMatch(String line) {
    Matcher matcher = TARGET_PATTERN.matcher(line);
    if (!matcher.find()) {
      return null;
    }
    String labelString = matcher.group();
    String prefix = matcher.group(1);
    return prefix != null ? labelString.substring(prefix.length()) : labelString;
  }
}
