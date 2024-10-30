/*
 * Copyright 2023 The Bazel Authors. All rights reserved.
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

import static com.google.common.truth.Truth8.assertThat;

import java.nio.file.Path;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class QuerySpecTest {

  @Test
  public void testGetQueryExpression_includes_singlePath() {
    QuerySpec qs =
        QuerySpec.builder(QuerySpec.QueryStrategy.PLAIN)
            .workspaceRoot(Path.of("/workspace/"))
            .includePath(Path.of("some/included/path"))
            .build();
    assertThat(qs.getQueryExpression()).hasValue("(//some/included/path/...:*)");
  }

  @Test
  public void testGetQueryExpression_empty_query() {
    QuerySpec qs = QuerySpec.builder(QuerySpec.QueryStrategy.PLAIN).workspaceRoot(Path.of("/workspace/")).build();
    assertThat(qs.getQueryExpression()).isEmpty();
  }

  @Test
  public void testGetQueryExpression_includes_multiplePaths() {
    QuerySpec qs =
        QuerySpec.builder(QuerySpec.QueryStrategy.PLAIN)
            .workspaceRoot(Path.of("/workspace/"))
            .includePath(Path.of("some/included/path"))
            .includePath(Path.of("another/included/path"))
            .build();
    assertThat(qs.getQueryExpression())
        .hasValue("(//some/included/path/...:* + //another/included/path/...:*)");
  }

  @Test
  public void testGetQueryExpression_includes_and_excludes() {
    QuerySpec qs =
        QuerySpec.builder(QuerySpec.QueryStrategy.PLAIN)
            .workspaceRoot(Path.of("/workspace/"))
            .includePath(Path.of("some/included/path"))
            .includePath(Path.of("another/included/path"))
            .excludePath(Path.of("some/included/path/excluded"))
            .excludePath(Path.of("another/included/path/excluded"))
            .build();
    assertThat(qs.getQueryExpression())
        .hasValue(
            "(//some/included/path/...:* + //another/included/path/...:* -"
                + " //some/included/path/excluded/...:* - //another/included/path/excluded/...:*)");
  }
}
