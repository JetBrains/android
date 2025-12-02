/*
 * Copyright 2025 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.base.model;

import static com.google.common.truth.Truth.assertThat;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class BazelVersionCompatTest {

  @Test
  public void testBazel6() {
    BazelVersionCompat compat = new BazelVersionCompat((major, minor, bugfix) -> {
      if (major == 6 && minor == 0 && bugfix == 0) return true;
      if (major < 6) return true;
      return false;
    });
    assertThat(compat.getAspectPrefix()).isEqualTo("@@");
    assertThat(compat.getRepositoryOverrideFlag()).isEqualTo("--override_repository");
  }

  @Test
  public void testBazel7() {
    BazelVersionCompat compat = new BazelVersionCompat((major, minor, bugfix) -> {
      if (major == 7 && minor == 0 && bugfix == 0) return true;
      if (major < 7) return true;
      return false;
    });
    assertThat(compat.getAspectPrefix()).isEqualTo("@@");
    assertThat(compat.getRepositoryOverrideFlag()).isEqualTo("--override_repository");
  }

  @Test
  public void testBazel8() {
    BazelVersionCompat compat = new BazelVersionCompat((major, minor, bugfix) -> {
      if (major == 8 && minor == 0 && bugfix == 0) return true;
      if (major < 8) return true;
      return false;
    });
    assertThat(compat.getAspectPrefix()).isEqualTo("@");
    assertThat(compat.getRepositoryOverrideFlag()).isEqualTo("--inject_repository");
  }

  @Test
  public void testMakeInjectRepositoryFlag() {
    BazelVersionCompat compat = new BazelVersionCompat((major, minor, bugfix) -> major >= 8);
    assertThat(compat.makeInjectRepositoryFlag("repo", "path"))
        .isEqualTo("--inject_repository=repo=path");

    compat = new BazelVersionCompat((major, minor, bugfix) -> major < 8);
    assertThat(compat.makeInjectRepositoryFlag("repo", "path"))
        .isEqualTo("--override_repository=repo=path");
  }

  @Test
  public void testMakeAspectFromInjectedRepositoryFlag() {
    BazelVersionCompat compat = new BazelVersionCompat((major, minor, bugfix) -> major >= 8);
    assertThat(compat.makeAspectFromInjectedRepositoryFlag("repo", "aspect"))
        .isEqualTo("--aspects=@repo//:aspect");

    compat = new BazelVersionCompat((major, minor, bugfix) -> major >= 6 && major < 8);
    assertThat(compat.makeAspectFromInjectedRepositoryFlag("repo", "aspect"))
        .isEqualTo("--aspects=@@repo//:aspect");
  }
}
