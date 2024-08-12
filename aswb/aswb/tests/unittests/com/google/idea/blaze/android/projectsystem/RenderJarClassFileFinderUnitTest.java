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
package com.google.idea.blaze.android.projectsystem;

import static com.google.common.truth.Truth.assertThat;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Unit tests for {@link RenderJarClassFileFinder} */
@RunWith(JUnit4.class)
public class RenderJarClassFileFinderUnitTest {
  @Test
  public void testOuterRClass_isDetectedCorrectly() {
    assertThat(RenderJarClassFileFinder.isResourceClass("com.foo.bar.R")).isTrue();
    assertThat(RenderJarClassFileFinder.isResourceClass("com.foo.bar.NonR")).isFalse();
  }

  @Test
  public void testInnerRClass_isDetectedCorrectly() {
    assertThat(RenderJarClassFileFinder.isResourceClass("com.foo.bar.R$id")).isTrue();
    assertThat(RenderJarClassFileFinder.isResourceClass("com.foo.bar.R$dimen")).isTrue();
    assertThat(RenderJarClassFileFinder.isResourceClass("com.foo.bar.NonR$InnerClass")).isFalse();
  }
}
