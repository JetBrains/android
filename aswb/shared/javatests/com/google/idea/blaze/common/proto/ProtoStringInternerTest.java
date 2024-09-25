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
package com.google.idea.blaze.common.proto;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.base.Joiner;
import com.google.common.collect.Iterables;
import com.google.idea.blaze.common.proto.TestMessage.MyMessage;
import com.google.protobuf.TextFormat;
import com.google.protobuf.TextFormat.ParseException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class ProtoStringInternerTest {

  private static MyMessage parse(String... lines) throws ParseException {
    TextFormat.Parser parser = TextFormat.Parser.newBuilder().build();
    MyMessage.Builder builder = MyMessage.newBuilder();
    parser.merge(Joiner.on("\n").join(lines), builder);
    return builder.build();
  }

  @Test
  public void sanity_check() throws ParseException {
    MyMessage message1 = parse("string: 'value'");
    MyMessage message2 = parse("string: 'value'");
    assertThat(message1.getString()).isEqualTo(message2.getString());
    // if this fails, it invalidates the whole basis of the string interner:
    assertThat(message1.getString()).isNotSameInstanceAs(message2.getString());
  }

  @Test
  public void basic_string() throws ParseException {
    MyMessage message1 = ProtoStringInterner.intern(parse("string: 'value'"));
    MyMessage message2 = ProtoStringInterner.intern(parse("string: 'value'"));
    assertThat(message1.getString()).isSameInstanceAs(message2.getString());
  }

  @Test
  public void no_strings_message_not_copied() throws ParseException {
    MyMessage original = parse("integer: 42");
    MyMessage interned = ProtoStringInterner.intern(original);
    assertThat(interned).isSameInstanceAs(original);
  }

  @Test
  public void repeated_strings() throws ParseException {
    MyMessage original = parse("strings: 'one'", "strings: 'one'", "strings: 'two'");
    MyMessage interned = ProtoStringInterner.intern(original);
    assertThat(interned).isEqualTo(original);
    assertThat(interned.getStrings(0)).isSameInstanceAs(interned.getStrings(1));
  }

  @Test
  public void string_map_values() throws ParseException {
    MyMessage original =
        parse(
            "string_map {",
            "  key: 'one'",
            "  value: 'kittens'",
            "}",
            "string_map {",
            "  key: 'two'",
            "  value: 'kittens'",
            "}",
            "string_map {",
            "  key: 'three'",
            "  value: 'puppies'",
            "}");

    MyMessage interned = ProtoStringInterner.intern(original);
    assertThat(interned).isEqualTo(original);
    assertThat(interned.getStringMapMap().get("one"))
        .isSameInstanceAs(interned.getStringMapMap().get("two"));
  }

  @Test
  public void string_map_keys() throws ParseException {
    MyMessage original =
        parse("string: 'one'", "string_map {", "  key: 'one'", "  value: 'kittens'", "}");

    MyMessage interned = ProtoStringInterner.intern(original);
    assertThat(interned).isEqualTo(original);
    assertThat(interned.getString())
        .isSameInstanceAs(Iterables.getOnlyElement(interned.getStringMapMap().keySet()));
  }

  @Test
  public void nested_string() throws ParseException {
    MyMessage original = parse("string: 'hello'", "sub_message {", "  sub_string: 'hello'", "}");

    MyMessage interned = ProtoStringInterner.intern(original);
    assertThat(interned).isEqualTo(original);
    assertThat(interned.getString()).isSameInstanceAs(interned.getSubMessage().getSubString());
  }

  @Test
  public void nested_repeated_field_no_strings_not_copied() throws ParseException {
    MyMessage original =
        parse("sub_messages {", "  sub_int: 1", "}", "sub_messages {", "  sub_int: 2", "}");
    MyMessage interned = ProtoStringInterner.intern(original);
    assertThat(interned).isSameInstanceAs(original);
  }
}
