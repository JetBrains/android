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
package com.google.idea.blaze.common;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.ImmutableList;
import java.nio.file.Path;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class TargetTreeTest {

  @Test
  public void test_size() {
    TargetTree tt = TargetTree.EMPTY;
    assertThat(tt.size()).isEqualTo(0);

    tt = TargetTree.Builder.root().add(Label.of("//a/b/c:c")).add(Label.of("//a/b/d:d")).build();
    assertThat(tt.size()).isEqualTo(2);

    tt = TargetTree.Builder.root().add(Label.of("//a/b/c:c")).add(Label.of("//a/b/c:d")).build();
    assertThat(tt.size()).isEqualTo(2);
  }

  @Test
  public void test_isEmpty() {
    TargetTree tt = TargetTree.EMPTY;
    assertThat(tt.isEmpty()).isTrue();

    tt = TargetTree.Builder.root().add(Label.of("//a/b:b")).build();
    assertThat(tt.isEmpty()).isFalse();
  }

  @Test
  public void test_get() {
    TargetTree tt =
        TargetTree.Builder.root()
            .add(Label.of("//a/b:b"))
            .add(Label.of("//a/b/c:c"))
            .add(Label.of("//a/b/c:e"))
            .add(Label.of("//a/b/c/d:d"))
            .build();
    assertThat(tt.get(Path.of("a/b/c")))
        .containsExactly(Label.of("//a/b/c:c"), Label.of("//a/b/c:e"));
  }

  @Test
  public void test_getSubpackages_selfAndChildren() {
    TargetTree tt =
        TargetTree.Builder.root()
            .add(Label.of("//a/b:b"))
            .add(Label.of("//a/b/c:c"))
            .add(Label.of("//a/b/d:d"))
            .add(Label.of("//z/y/z:z"))
            .build();
    assertThat(tt.getSubpackages(Path.of("a/b")))
        .containsExactly(Label.of("//a/b:b"), Label.of("//a/b/c:c"), Label.of("//a/b/d:d"));
  }

  @Test
  public void test_getSubpackages_directChildren() {
    TargetTree tt =
        TargetTree.Builder.root()
            .add(Label.of("//a/b/c:c"))
            .add(Label.of("//a/b/d:d"))
            .add(Label.of("//x/y/z:z"))
            .build();
    assertThat(tt.getSubpackages(Path.of("a/b")))
        .containsExactly(Label.of("//a/b/c:c"), Label.of("//a/b/d:d"));
  }

  @Test
  public void test_getSubpackages_indirectChildren() {
    TargetTree tt =
        TargetTree.Builder.root()
            .add(Label.of("//a/b/c:c"))
            .add(Label.of("//a/b/d:d"))
            .add(Label.of("//x/y/z:z"))
            .build();
    assertThat(tt.getSubpackages(Path.of("a")))
        .containsExactly(Label.of("//a/b/c:c"), Label.of("//a/b/d:d"));
  }

  @Test
  public void test_getSubpackages_none() {
    TargetTree tt =
        TargetTree.Builder.root()
            .add(Label.of("//a/b/c:c"))
            .add(Label.of("//a/b/d:d"))
            .add(Label.of("//x/y/z:z"))
            .build();
    assertThat(tt.getSubpackages(Path.of("b")).isEmpty()).isTrue();
  }

  @Test
  public void test_getSubpackages_subTree() {
    TargetTree tt =
        TargetTree.Builder.root()
            .add(Label.of("//a/b/c:c"))
            .add(Label.of("//a/b/d:d"))
            .add(Label.of("//a/b/c/d:d"))
            .add(Label.of("//a/b/c/e:e"))
            .add(Label.of("//x/y/z:z"))
            .build();
    assertThat(tt.getSubpackages(Path.of("a/b")))
        .containsExactly(
            Label.of("//a/b/c:c"),
            Label.of("//a/b/d:d"),
            Label.of("//a/b/c/d:d"),
            Label.of("//a/b/c/e:e"));
  }

  @Test
  public void test_iterator() {
    TargetTree tt =
        TargetTree.Builder.root()
            .add(Label.of("//a/b/c:c"))
            .add(Label.of("//a/b/d:d"))
            .add(Label.of("//a/b/c/d:d"))
            .add(Label.of("//a/b/c/e:e"))
            .add(Label.of("//x/y/z:z"))
            .build();
    assertThat(ImmutableList.copyOf(tt))
        .containsExactly(
            Label.of("//a/b/c:c"),
            Label.of("//a/b/d:d"),
            Label.of("//a/b/c/d:d"),
            Label.of("//a/b/c/e:e"),
            Label.of("//x/y/z:z"));
  }

  @Test
  public void test_iterator_empty() {
    TargetTree tt = TargetTree.Builder.root().build();
    assertThat(ImmutableList.copyOf(tt)).isEmpty();
  }
}
