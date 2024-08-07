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
package com.google.idea.blaze.base.execution;

import static com.google.common.truth.Truth.assertThat;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests for {@link BlazeParametersListUtil} */
@RunWith(JUnit4.class)
public class BlazeParametersListUtilTest {

  @Test
  public void testEncodeSimpleParameter() {
    assertThat(BlazeParametersListUtil.encodeParam("abcdef")).isEqualTo("abcdef");
  }

  @Test
  public void testEncodeParameterWithDoubleQuotes() {
    assertThat(BlazeParametersListUtil.encodeParam("\"abcdef\"")).isEqualTo("\\\"abcdef\\\"");
  }

  @Test
  public void testEncodeParameterWithSingleQuotes() {
    assertThat(BlazeParametersListUtil.encodeParam("'abcdef'")).isEqualTo("\"'abcdef'\"");
  }

  @Test
  public void testEncodeParameterWithSpaces() {
    assertThat(BlazeParametersListUtil.encodeParam("a b c d e f")).isEqualTo("\"a b c d e f\"");
  }

  @Test
  public void testEncodeParameterWithSpaceBeforeSingleQuote() {
    assertThat(BlazeParametersListUtil.encodeParam("abc 'def")).isEqualTo("\"abc 'def\"");
    assertThat(BlazeParametersListUtil.encodeParam("\"abc 'def\""))
        .isEqualTo("\"\\\"abc 'def\\\"\"");
  }
}
