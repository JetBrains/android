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
package com.google.idea.blaze.aspect;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.fail;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Unit tests for {@link OptionParser}. */
@RunWith(JUnit4.class)
public class OptionParserTest {
  @Test
  public void testParseSingleOption() throws Exception {
    // Test parsing of a flag that doesn't occur.
    assertThat(
            OptionParser.parseSingleOption(
                new String[] {"--foo", "1", "--bar", "2"}, "unknown", String::toString))
        .isNull();
    // Test parsing of an integer flag.
    assertThat(
            OptionParser.<Integer>parseSingleOption(
                new String[] {"--foo", "1", "--bar", "2"}, "bar", Integer::parseInt))
        .isEqualTo(2);
    // Test parsing of a flag that's missing its value: if there's a subsequent entry and it can be
    // parsed, it'll be taken as the flag's value.
    assertThat(
            OptionParser.parseSingleOption(
                new String[] {"--foo", "--bar", "--baz"}, "foo", String::toString))
        .isEqualTo("--bar");
    // Test parsing of a flag that's missing its value: if there's no subsequent entry, an exception
    // is thrown.
    try {
      OptionParser.parseSingleOption(new String[] {"--foo", "1", "--bar"}, "bar", String::toString);
      fail("Expected failure");
    } catch (IllegalArgumentException e) {
      assertThat(e).hasMessageThat().isEqualTo("Expected value after --bar");
    }
    // Test that a single-value flag should not appear multiple times.
    try {
      OptionParser.parseSingleOption(
          new String[] {"--foo", "1", "--foo", "2"}, "foo", String::toString);
      fail("Expected failure");
    } catch (IllegalArgumentException e) {
      assertThat(e).hasMessageThat().isEqualTo("Expected --foo to appear at most once");
    }
  }

  @Test
  public void testParseMultiOption() throws Exception {
    // Test parsing of a flag that doesn't occur.
    assertThat(
            OptionParser.parseMultiOption(
                new String[] {"--foo", "1", "--bar", "2"}, "unknown", String::toString))
        .isEmpty();
    // Test parsing of an integer multi-flag. Ensure that the flag name is matched wholly, and
    // doesn't match other flags that it's a prefix of.
    assertThat(
            OptionParser.parseMultiOption(
                new String[] {"--foo", "1", "--foooo", "2", "--foo", "3"},
                "foo",
                Integer::parseInt))
        .containsExactly(1, 3)
        .inOrder();
    // Test that the --name=value style of flags is not supported (for sake of simplicity).
    assertThat(
            OptionParser.parseMultiOption(
                new String[] {"--foo", "1", "--foo=2", "--foo", "3"}, "foo", Integer::parseInt))
        .containsExactly(1, 3)
        .inOrder();
    // Test parsing a multi-flag where one occurrence cannot consume a value.
    try {
      OptionParser.parseMultiOption(
          new String[] {"--foo", "1", "--bar", "2", "--foo"}, "foo", String::toString);
      fail("Expected failure");
    } catch (IllegalArgumentException e) {
      assertThat(e).hasMessageThat().isEqualTo("Expected value after --foo");
    }
  }
}
