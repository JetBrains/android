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
package com.google.idea.blaze.qsync;

import static com.google.common.truth.Truth.assertThat;

import java.io.IOException;
import java.nio.file.Path;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class QuerySyncTestUtilsTest {

  @Test
  public void testPackageReader_javaPath() throws IOException {
    assertThat(
            QuerySyncTestUtils.PATH_INFERRING_PACKAGE_READER.readPackage(
                Path.of("/some/project/java/com/my/package/Class.java")))
        .isEqualTo("com.my.package");
  }

  @Test
  public void testPackageReader_javatestsPath() throws IOException {
    assertThat(
            QuerySyncTestUtils.PATH_INFERRING_PACKAGE_READER.readPackage(
                Path.of("/some/project/javatests/com/my/test/package/Test.java")))
        .isEqualTo("com.my.test.package");
  }
}
