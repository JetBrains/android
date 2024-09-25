/*
 * Copyright 2018 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.android.run.runner;

import static com.google.common.truth.Truth.assertThat;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests for {@link AaptUtil}. */
@RunWith(JUnit4.class)
public class AaptUtilTest {
  @Test
  public void matchPattern_packageName() throws IOException {
    String pattern =
        "package: name='com.google.foo.dev' versionCode='20000491' versionName='4.15.215788282'"
            + " compileSdkVersion='28' compileSdkVersionCodename='9'";
    assertThat(
            AaptUtil.matchPattern(
                    new BufferedReader(new StringReader(pattern)), AaptUtil.PACKAGE_PATTERN)
                .group(1))
        .isEqualTo("com.google.foo.dev");
  }
}
