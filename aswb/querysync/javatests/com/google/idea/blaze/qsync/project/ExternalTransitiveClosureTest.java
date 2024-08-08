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
public class ExternalTransitiveClosureTest {

  @Test
  public void test_empty() {
    ExternalTransitiveClosure<String> etc =
        new ExternalTransitiveClosure<>(new DepsGraph.Builder<String>().build(), ImmutableSet.of());

    assertThat(etc.get("a")).isEmpty();
  }

  @Test
  public void test_no_transitive() {
    ExternalTransitiveClosure<String> etc =
        new ExternalTransitiveClosure<>(
            new DepsGraph.Builder<String>().add("a", ImmutableSet.of("b")).build(),
            ImmutableSet.of("b"));

    assertThat(etc.get("a")).containsExactly("b");
  }

  @Test
  public void test_get_self_external() {
    ExternalTransitiveClosure<String> etc =
        new ExternalTransitiveClosure<>(
            new DepsGraph.Builder<String>().add("a", ImmutableSet.of("b")).build(),
            ImmutableSet.of("a"));

    assertThat(etc.get("a")).containsExactly("a");
  }

  @Test
  public void test_get_transitive() {
    ExternalTransitiveClosure<String> etc =
        new ExternalTransitiveClosure<>(
            new DepsGraph.Builder<String>()
                .add("a", ImmutableSet.of("b"))
                .add("b", ImmutableSet.of("c"))
                .build(),
            ImmutableSet.of("c"));

    assertThat(etc.get("a")).containsExactly("c");
  }

  @Test
  public void test_get_transitive_and_self() {
    ExternalTransitiveClosure<String> etc =
        new ExternalTransitiveClosure<>(
            new DepsGraph.Builder<String>()
                .add("a", ImmutableSet.of("b"))
                .add("b", ImmutableSet.of("c"))
                .build(),
            ImmutableSet.of("a", "c"));

    assertThat(etc.get("a")).containsExactly("a", "c");
  }
}
