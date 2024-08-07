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
package com.google.idea.blaze.qsync.project;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.ImmutableSet;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class DepsGraphTest {

  @Test
  public void test_empty() {
    DepsGraph<String> graph = new DepsGraph.Builder<String>().build();

    assertThat(graph.nodes()).isEmpty();
  }

  @Test
  public void test_basics() {
    DepsGraph<String> graph = new DepsGraph.Builder<>().add("a", ImmutableSet.of("b", "c")).build();

    assertThat(graph.nodes()).containsExactly("a", "b", "c");
    assertThat(graph.deps("a")).containsExactly("b", "c");
    assertThat(graph.rdeps("b")).containsExactly("a");
    assertThat(graph.rdeps("c")).containsExactly("a");
  }

  @Test
  public void test_multi_rdeps() {
    DepsGraph<String> graph =
        new DepsGraph.Builder<>()
            .add("a", ImmutableSet.of("c", "d"))
            .add("b", ImmutableSet.of("c"))
            .build();

    assertThat(graph.nodes()).containsExactly("a", "b", "c", "d");
    assertThat(graph.rdeps("c")).containsExactly("a", "b");
    assertThat(graph.rdeps("d")).containsExactly("a");
  }

  @Test
  public void test_transitive_deps() {
    DepsGraph<String> graph =
        new DepsGraph.Builder<>()
            .add("a", ImmutableSet.of("b", "c"))
            .add("b", ImmutableSet.of("d"))
            .build();

    assertThat(graph.nodes()).containsExactly("a", "b", "c", "d");
    // deps is not transitive:
    assertThat(graph.deps("a")).containsExactly("b", "c");
  }
}
