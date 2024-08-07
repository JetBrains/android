/*
 * Copyright 2021 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.base.projectview;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.ImmutableList;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Test for {@link ProjectViewStorageManager}. */
@RunWith(JUnit4.class)
public class ProjectViewStorageManagerTest {

  private static final com.google.common.collect.ImmutableList<String>
      VALID_PROJECT_VIEW_FILENAMES =
          ImmutableList.of(
              ".blazeproject",
              ".asproject",
              ".bazelproject",
              "foo.blazeproject",
              "bar.baz.bazelproject");

  private static final com.google.common.collect.ImmutableList<String>
      INVALID_PROJECT_VIEW_FILENAMES =
          ImmutableList.of(
              ".buckproject",
              ".bazel",
              "blazeproject",
              "bazelproject",
              "blazeproject.foo",
              "bar.bazelproject.baz",
              ".",
              "");

  @Test
  public void testIsProjectViewFile() {
    VALID_PROJECT_VIEW_FILENAMES.forEach(
        filename -> assertThat(ProjectViewStorageManager.isProjectViewFile(filename)).isTrue());

    INVALID_PROJECT_VIEW_FILENAMES.forEach(
        filename -> assertThat(ProjectViewStorageManager.isProjectViewFile(filename)).isFalse());
  }
}
