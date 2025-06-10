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
package com.google.idea.blaze.common

import com.google.common.truth.Truth
import com.google.idea.blaze.common.TargetTree.Companion.create
import java.nio.file.Path
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class TargetTreeTest {
  @Test
  fun test_size() {
    var tt: TargetTree = TargetTreeImpl.EMPTY
    Truth.assertThat(tt.getTargets().toList().size).isEqualTo(0)

    tt = create(listOf(Label.of("//a/b/c:c"), Label.of("//a/b/d:d")))
    Truth.assertThat(tt.getTargets().toList().size).isEqualTo(2)

    tt = create(listOf(Label.of("//a/b/c:c"), Label.of("//a/b/c:d")))
    Truth.assertThat(tt.getTargets().toList().size).isEqualTo(2)
  }

  @Test
  fun test_isEmpty() {
    var tt: TargetTree = TargetTreeImpl.EMPTY
    Truth.assertThat(tt.getTargets().toList().isEmpty()).isTrue()

    tt = create(listOf(Label.of("//a/b:b")))
    Truth.assertThat(tt.getTargets().toList().isEmpty()).isFalse()
  }

  @Test
  fun test_getDirectTargets() {
    val tt =
      create(
        listOf(
          Label.of("//a/b:b"),
          Label.of("//a/b/c:c"),
          Label.of("//a/b/c:e"),
          Label.of("//a/b/c/d:d")
        )
      )
    Truth.assertThat(tt.getDirectTargets(Path.of("a/b/c")).toList())
      .containsExactly(Label.of("//a/b/c:c"), Label.of("//a/b/c:e"))
  }

  @Test
  fun test_getSubpackages_selfAndChildren() {
    val tt =
      create(
        listOf(
          Label.of("//a/b:b"),
          Label.of("//a/b/c:c"),
          Label.of("//a/b/d:d"),
          Label.of("//z/y/z:z")
        )
      )
    Truth.assertThat(tt.getSubpackages(Path.of("a/b")).toList())
      .containsExactly(Label.of("//a/b:b"), Label.of("//a/b/c:c"), Label.of("//a/b/d:d"))
  }

  @Test
  fun test_getSubpackages_directChildren() {
    val tt =
      create(
        listOf(
          Label.of("//a/b/c:c"),
          Label.of("//a/b/d:d"),
          Label.of("//x/y/z:z")
        )
      )
    Truth.assertThat(tt.getSubpackages(Path.of("a/b")).toList())
      .containsExactly(Label.of("//a/b/c:c"), Label.of("//a/b/d:d"))
  }

  @Test
  fun test_getSubpackages_indirectChildren() {
    val tt =
      create(
        listOf(
          Label.of("//a/b/c:c"),
          Label.of("//a/b/d:d"),
          Label.of("//a/b/k/l/m:n"),
          Label.of("//x/y/z:z")
        )
      )
    Truth.assertThat(tt.getSubpackages(Path.of("a")).toList())
      .containsExactly(Label.of("//a/b/c:c"), Label.of("//a/b/d:d"), Label.of("//a/b/k/l/m:n"))
  }

  @Test
  fun test_getSubpackages_none() {
    val tt =
      create(
        listOf(
          Label.of("//a/b/c:c"),
          Label.of("//a/b/d:d"),
          Label.of("//x/y/z:z")
        )
      )
    Truth.assertThat(tt.getSubpackages(Path.of("b")).toList().isEmpty()).isTrue()
  }

  @Test
  fun test_getSubpackages_subTree() {
    val tt =
      create(
        listOf(
          Label.of("//a/b/c:c"),
          Label.of("//a/b/d:d"),
          Label.of("//a/b/c/d:d"),
          Label.of("//a/b/c/e:e"),
          Label.of("//x/y/z:z")
        )
      )
    Truth.assertThat(tt.getSubpackages(Path.of("a/b")).toList())
      .containsExactly(
        Label.of("//a/b/c:c"),
        Label.of("//a/b/d:d"),
        Label.of("//a/b/c/d:d"),
        Label.of("//a/b/c/e:e")
      )
  }

  @Test
  fun test_iterator() {
    val tt =
      create(
        listOf(
          Label.of("//a/b/c:c"),
          Label.of("//a/b/d:d"),
          Label.of("//a/b/c/d:d"),
          Label.of("//a/b/c/e:e"),
          Label.of("//x/y/z:z")
        )
      )
    Truth.assertThat(tt.getTargets().toList())
      .containsExactly(
        Label.of("//a/b/c:c"),
        Label.of("//a/b/d:d"),
        Label.of("//a/b/c/d:d"),
        Label.of("//a/b/c/e:e"),
        Label.of("//x/y/z:z")
      )
  }

  @Test
  fun test_iterator_empty() {
    val tt = create(listOf())
    Truth.assertThat(tt.getTargets().toList()).isEmpty()
  }
}
