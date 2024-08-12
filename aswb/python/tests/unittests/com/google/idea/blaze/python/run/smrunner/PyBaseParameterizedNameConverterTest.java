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
package com.google.idea.blaze.python.run.smrunner;

import static com.google.common.truth.Truth.assertThat;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Unit tests for {@link PyBaseParameterizedNameConverter}. */
@RunWith(JUnit4.class)
public class PyBaseParameterizedNameConverterTest {

  private static final PyBaseParameterizedNameConverter CONVERTER =
      new PyBaseParameterizedNameConverter();

  @Test
  public void testUnparameterizedTestCaseReturnsNull() {
    assertThat(CONVERTER.toFunctionName("simpleName")).isNull();
    assertThat(CONVERTER.toFunctionName("name_with_underscores")).isNull();
    assertThat(CONVERTER.toFunctionName("name__with__double__underscores123")).isNull();
  }

  @Test
  public void testSimpleParameterNames() {
    assertThat(CONVERTER.toFunctionName("test(1)")).isEqualTo("test");
    assertThat(CONVERTER.toFunctionName("test(param__name)")).isEqualTo("test");
    assertThat(CONVERTER.toFunctionName("test(@#$%^&*,./)")).isEqualTo("test");
  }

  @Test
  public void testSplitAtFirstOpenBracket() {
    assertThat(CONVERTER.toFunctionName("test(param(1)")).isEqualTo("test");
    assertThat(CONVERTER.toFunctionName("test(@#$;('(())))())")).isEqualTo("test");
  }
}
