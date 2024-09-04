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
package com.google.idea.blaze.base.model.primitives;

import static com.google.common.truth.Truth.assertThat;

import com.google.idea.blaze.base.BlazeTestCase;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Unit tests for {@link TargetName} validation */
@RunWith(JUnit4.class)
public class TargetNameTest extends BlazeTestCase {

  @Test
  public void testValidateTargetName() {
    // Legal names
    assertThat(TargetName.validate("foo")).isNull();
    assertThat(TargetName.validate(".")).isNull();
    assertThat(TargetName.validate(".foo")).isNull();
    assertThat(TargetName.validate("foo+")).isNull();
    assertThat(TargetName.validate("_foo")).isNull();
    assertThat(TargetName.validate("-foo")).isNull();
    assertThat(TargetName.validate("foo-bar")).isNull();
    assertThat(TargetName.validate("foo..")).isNull();
    assertThat(TargetName.validate("..foo")).isNull();
    assertThat(TargetName.validate("foo+bar")).isNull();
    assertThat(TargetName.validate("foo_bar")).isNull();
    assertThat(TargetName.validate("foo=bar")).isNull();
    assertThat(TargetName.validate("foo.bar")).isNull();
    assertThat(TargetName.validate("foo@bar")).isNull();
    assertThat(TargetName.validate("foo~bar")).isNull();
    assertThat(TargetName.validate("foo#bar")).isNull();
    assertThat(TargetName.validate("foo!bar")).isNull();
    assertThat(TargetName.validate("foo bar")).isNull();
    assertThat(TargetName.validate("bar&")).isNull();

    // Illegal names
    assertThat(TargetName.validate("")).isNotEmpty();
    assertThat(TargetName.validate("/foo")).isNotEmpty();
    assertThat(TargetName.validate("../foo")).isNotEmpty();
    assertThat(TargetName.validate("./foo")).isNotEmpty();
    assertThat(TargetName.validate("..")).isNotEmpty();
    assertThat(TargetName.validate("foo/../bar")).isNotEmpty();
    assertThat(TargetName.validate("foo/./bar")).isNotEmpty();
    assertThat(TargetName.validate("foo//bar")).isNotEmpty();
    assertThat(TargetName.validate("foo/..")).isNotEmpty();
    assertThat(TargetName.validate("/..")).isNotEmpty();
    assertThat(TargetName.validate("foo/")).isNotEmpty();
    assertThat(TargetName.validate("/")).isNotEmpty();
    assertThat(TargetName.validate("bar:baz")).isNotEmpty();
    assertThat(TargetName.validate("bar:")).isNotEmpty();
  }
}
