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
package com.google.idea.blaze.base.model.primitives;

import static com.google.common.truth.Truth.assertThat;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Unit tests for {@link PackagePathValidator}. */
@RunWith(JUnit4.class)
public class PackagePathValidatorTest {

  @Test
  public void testPassingValidations() {
    assertThat(PackagePathValidator.validatePackageName("foo")).isNull();
    assertThat(PackagePathValidator.validatePackageName("foo_123")).isNull();
    assertThat(PackagePathValidator.validatePackageName("Foo")).isNull();
    assertThat(PackagePathValidator.validatePackageName("FOO")).isNull();
    assertThat(PackagePathValidator.validatePackageName("foO")).isNull();
    assertThat(PackagePathValidator.validatePackageName("foo-bar")).isNull();
    assertThat(PackagePathValidator.validatePackageName("Foo-Bar")).isNull();
    assertThat(PackagePathValidator.validatePackageName("FOO-BAR")).isNull();
    assertThat(PackagePathValidator.validatePackageName("bar.baz")).isNull();
    assertThat(PackagePathValidator.validatePackageName("a/..b")).isNull();
    assertThat(PackagePathValidator.validatePackageName("a/.b")).isNull();
    assertThat(PackagePathValidator.validatePackageName("a/b.")).isNull();
    assertThat(PackagePathValidator.validatePackageName("a/b..")).isNull();
    assertThat(PackagePathValidator.validatePackageName("a$( )/b..")).isNull();
  }

  @Test
  public void testFailingValidations() {
    assertThat(PackagePathValidator.validatePackageName("/foo"))
        .contains("package names may not start with '/'");
    assertThat(PackagePathValidator.validatePackageName("foo/"))
        .contains("package names may not end with '/'");
    assertThat(PackagePathValidator.validatePackageName("foo:bar"))
        .contains(PackagePathValidator.PACKAGE_NAME_ERROR);
    assertThat(PackagePathValidator.validatePackageName("baz@12345"))
        .contains(PackagePathValidator.PACKAGE_NAME_ERROR);

    assertThat(PackagePathValidator.validatePackageName("bar/../baz"))
        .contains(PackagePathValidator.PACKAGE_NAME_DOT_ERROR);
    assertThat(PackagePathValidator.validatePackageName("bar/.."))
        .contains(PackagePathValidator.PACKAGE_NAME_DOT_ERROR);
    assertThat(PackagePathValidator.validatePackageName("../bar"))
        .contains(PackagePathValidator.PACKAGE_NAME_DOT_ERROR);
    assertThat(PackagePathValidator.validatePackageName("bar/..."))
        .contains(PackagePathValidator.PACKAGE_NAME_DOT_ERROR);

    assertThat(PackagePathValidator.validatePackageName("bar/./baz"))
        .contains(PackagePathValidator.PACKAGE_NAME_DOT_ERROR);
    assertThat(PackagePathValidator.validatePackageName("bar/."))
        .contains(PackagePathValidator.PACKAGE_NAME_DOT_ERROR);
    assertThat(PackagePathValidator.validatePackageName("./bar"))
        .contains(PackagePathValidator.PACKAGE_NAME_DOT_ERROR);
  }
}
