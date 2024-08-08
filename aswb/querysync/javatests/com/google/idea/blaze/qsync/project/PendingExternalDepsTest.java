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
public class PendingExternalDepsTest {

  @Test
  public void test_self_not_built() {
    PendingExternalDeps ped =
        new PendingExternalDeps<String>(
            new ExternalTransitiveClosure<String>(
                new DepsGraph.Builder<String>().add("a", ImmutableSet.of("b")).build(),
                /*externalDeps*/ ImmutableSet.of()),
            /*builtDeps*/ ImmutableSet.of(),
            t -> ImmutableSet.of(DependencyTrackingBehavior.SELF));
    assertThat(ped.get("a")).containsExactly("a");
  }

  @Test
  public void test_self_built() {
    PendingExternalDeps ped =
        new PendingExternalDeps<String>(
            new ExternalTransitiveClosure<String>(
                new DepsGraph.Builder<String>().add("a", ImmutableSet.of("b")).build(),
                /*externalDeps*/ ImmutableSet.of("b")),
            /*builtDeps*/ ImmutableSet.of("a"),
            t -> ImmutableSet.of(DependencyTrackingBehavior.SELF));
    assertThat(ped.get("a")).isEmpty();
  }

  @Test
  public void test_external_none_built() {
    PendingExternalDeps ped =
        new PendingExternalDeps<String>(
            new ExternalTransitiveClosure<String>(
                new DepsGraph.Builder<String>()
                    .add("a", ImmutableSet.of("b"))
                    .add("b", ImmutableSet.of("c", "d"))
                    .build(),
                /*externalDeps*/ ImmutableSet.of("c", "d")),
            /*builtDeps*/ ImmutableSet.of(),
            t -> ImmutableSet.of(DependencyTrackingBehavior.EXTERNAL_DEPENDENCIES));
    assertThat(ped.get("a")).containsExactly("c", "d");
  }

  @Test
  public void test_external_some_built() {
    PendingExternalDeps ped =
        new PendingExternalDeps<String>(
            new ExternalTransitiveClosure<String>(
                new DepsGraph.Builder<String>()
                    .add("a", ImmutableSet.of("b"))
                    .add("b", ImmutableSet.of("c", "d"))
                    .build(),
                /*externalDeps*/ ImmutableSet.of("c", "d")),
            /*builtDeps*/ ImmutableSet.of("c"),
            t -> ImmutableSet.of(DependencyTrackingBehavior.EXTERNAL_DEPENDENCIES));
    assertThat(ped.get("a")).containsExactly("d");
  }

  @Test
  public void test_external_all_built() {
    PendingExternalDeps ped =
        new PendingExternalDeps<String>(
            new ExternalTransitiveClosure<String>(
                new DepsGraph.Builder<String>()
                    .add("a", ImmutableSet.of("b"))
                    .add("b", ImmutableSet.of("c", "d"))
                    .build(),
                /*externalDeps*/ ImmutableSet.of("c", "d")),
            /*builtDeps*/ ImmutableSet.of("c", "d"),
            t -> ImmutableSet.of(DependencyTrackingBehavior.EXTERNAL_DEPENDENCIES));
    assertThat(ped.get("a")).isEmpty();
  }

  @Test
  public void test_external_include_self() {
    PendingExternalDeps ped =
        new PendingExternalDeps<String>(
            new ExternalTransitiveClosure<String>(
                new DepsGraph.Builder<String>()
                    .add("a", ImmutableSet.of("b"))
                    .add("b", ImmutableSet.of("c", "d"))
                    .build(),
                /*externalDeps*/ ImmutableSet.of("a", "c", "d")),
            /*builtDeps*/ ImmutableSet.of(),
            t -> ImmutableSet.of(DependencyTrackingBehavior.EXTERNAL_DEPENDENCIES));
    assertThat(ped.get("a")).containsExactly("a", "c", "d");
  }
}
