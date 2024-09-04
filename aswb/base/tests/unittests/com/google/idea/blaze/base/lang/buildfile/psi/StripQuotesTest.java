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
package com.google.idea.blaze.base.lang.buildfile.psi;

import static com.google.common.truth.Truth.assertThat;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Test strip quotes method in {@link StringLiteral} */
@RunWith(JUnit4.class)
public class StripQuotesTest {

  @Test
  public void testStandardSingleQuotes() {
    String s = "'normal string'";
    assertThat(StringLiteral.stripQuotes(s)).isEqualTo("normal string");
  }

  @Test
  public void testStandardDoubleQuotes() {
    String s = "\"normal string\"";
    assertThat(StringLiteral.stripQuotes(s)).isEqualTo("normal string");
  }

  @Test
  public void testOtherQuoteTypeMixedIn() {
    String s = "\"normal 'string'\"";
    assertThat(StringLiteral.stripQuotes(s)).isEqualTo("normal 'string'");
  }

  @Test
  public void testOtherQuoteTypeMixedIn2() {
    String s = "'normal \"string\"'";
    assertThat(StringLiteral.stripQuotes(s)).isEqualTo("normal \"string\"");
  }

  @Test
  public void testTrailingQuoteMissing() {
    String s = "'normal string";
    assertThat(StringLiteral.stripQuotes(s)).isEqualTo("normal string");
  }

  @Test
  public void testStandardTripleSingleQuotes() {
    String s = "'''normal string'''";
    assertThat(StringLiteral.stripQuotes(s)).isEqualTo("normal string");
  }

  @Test
  public void testStandardTripleDoubleQuotes() {
    String s = "\"\"\"normal string\"\"\"";
    assertThat(StringLiteral.stripQuotes(s)).isEqualTo("normal string");
  }

  @Test
  public void testTripleQuotesWithMissingTrailingQuotes() {
    String s = "\"\"\"normal string";
    assertThat(StringLiteral.stripQuotes(s)).isEqualTo("normal string");

    s = "\"\"\"normal string\"";
    assertThat(StringLiteral.stripQuotes(s)).isEqualTo("normal string");

    s = "\"\"\"normal string\"\"";
    assertThat(StringLiteral.stripQuotes(s)).isEqualTo("normal string");
  }
}
