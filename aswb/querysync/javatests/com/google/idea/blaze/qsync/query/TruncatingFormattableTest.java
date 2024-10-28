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
package com.google.idea.blaze.qsync.query;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.base.Strings;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class TruncatingFormattableTest {

  public record TestTruncatingFormattable(String data) implements TruncatingFormattable{
    @Override
    public String toString() {
      return data;
    }
  }

  @Test
  public void testFormat_noPrecision() {
    final var t = new TestTruncatingFormattable("abc 123");
    assertThat(String.format("%s", t)).isEqualTo(t.toString());
  }

  @Test
  public void testFormat_shortPrecision() {
    final var t = new TestTruncatingFormattable("Long enough text for this test.");
    assertThat(String.format("%.6s", t)).isEqualTo("Long e");
  }

  @Test
  public void testFormat_longPrecision() {
    final var t = new TestTruncatingFormattable("Long enough text for this test.");
    assertThat(String.format("%.26s", t)).isEqualTo("Long enough tex<truncated>");
  }

  @Test
  public void testFormat_veryLongPrecision() {
    final var t = new TestTruncatingFormattable(Strings.repeat("Long enough text for this test.\n", 10));
    assertThat(String.format("%.1000s", t)).isEqualTo(t.toString());
  }
}
