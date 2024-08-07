/*
 * Copyright 2016 The Bazel Authors. All rights reserved.
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

import com.google.idea.blaze.base.BlazeTestCase;
import java.io.File;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mockito;
import org.mockito.stubbing.Answer;

/** Tests execution root path. */
@RunWith(JUnit4.class)
public class ExecutionRootPathTest extends BlazeTestCase {
  @Test
  public void testSingleLevelPathEndInSlash() {
    ExecutionRootPath executionRootPath = new ExecutionRootPath("foo");
    assertThat(executionRootPath.getAbsoluteOrRelativeFile()).isEqualTo(new File("foo/"));

    ExecutionRootPath executionRootPath2 = new ExecutionRootPath("foo/");
    assertThat(executionRootPath2.getAbsoluteOrRelativeFile()).isEqualTo(new File("foo/"));
  }

  @Test
  public void testMultiLevelPathEndInSlash() {
    ExecutionRootPath executionRootPath = new ExecutionRootPath("foo/bar");
    assertThat(executionRootPath.getAbsoluteOrRelativeFile()).isEqualTo(new File("foo/bar/"));

    ExecutionRootPath executionRootPath2 = new ExecutionRootPath("foo/bar/");
    assertThat(executionRootPath2.getAbsoluteOrRelativeFile()).isEqualTo(new File("foo/bar/"));
  }

  @Test
  public void testAbsoluteFileDoesNotGetRerooted() {
    ExecutionRootPath executionRootPath = new ExecutionRootPath("/root/foo/bar");
    File rootedFile = executionRootPath.getFileRootedAt(new File("/core/dev"));
    assertThat(rootedFile).isEqualTo(new File("/root/foo/bar"));
  }

  @Test
  public void testRelativeFileGetsRerooted() {
    ExecutionRootPath executionRootPath = new ExecutionRootPath("foo/bar");
    File rootedFile = executionRootPath.getFileRootedAt(new File("/root"));
    assertThat(rootedFile).isEqualTo(new File("/root/foo/bar"));
  }

  @Test
  public void testCreateRelativePathWithTwoRelativePaths() {
    ExecutionRootPath relativePathFragment =
        ExecutionRootPath.createAncestorRelativePath(
            createMockDirectory("code/lib/fastmath"),
            createMockDirectory("code/lib/fastmath/lib1"));
    assertThat(relativePathFragment).isNotNull();
    assertThat(relativePathFragment.getAbsoluteOrRelativeFile()).isEqualTo(new File("lib1"));
  }

  @Test
  public void testCreateRelativePathWithTwoRelativePathsWithNoRelativePath() {
    ExecutionRootPath relativePathFragment =
        ExecutionRootPath.createAncestorRelativePath(
            createMockDirectory("obj/lib/fastmath"), createMockDirectory("code/lib/slowmath"));
    assertThat(relativePathFragment).isNull();
  }

  @Test
  public void testCreateRelativePathWithTwoAbsolutePaths() {
    ExecutionRootPath relativePathFragment =
        ExecutionRootPath.createAncestorRelativePath(
            createMockDirectory("/code/lib/fastmath"),
            createMockDirectory("/code/lib/fastmath/lib1"));
    assertThat(relativePathFragment).isNotNull();
    assertThat(relativePathFragment.getAbsoluteOrRelativeFile()).isEqualTo(new File("lib1"));
  }

  @Test
  public void testCreateRelativePathWithTwoAbsolutePathsWithNoRelativePath() {
    ExecutionRootPath relativePathFragment =
        ExecutionRootPath.createAncestorRelativePath(
            createMockDirectory("/obj/lib/fastmath"), createMockDirectory("/code/lib/slowmath"));
    assertThat(relativePathFragment).isNull();
  }

  @Test
  public void testCreateRelativePathWithOneAbsolutePathAndOneRelativePathReturnsNull1() {
    ExecutionRootPath relativePathFragment =
        ExecutionRootPath.createAncestorRelativePath(
            createMockDirectory("/code/lib/fastmath"),
            createMockDirectory("code/lib/fastmath/lib1"));
    assertThat(relativePathFragment).isNull();
  }

  @Test
  public void testCreateRelativePathWithOneAbsolutePathAndOneRelativePathReturnsNull2() {
    ExecutionRootPath relativePathFragment =
        ExecutionRootPath.createAncestorRelativePath(
            createMockDirectory("code/lib/fastmath"), createMockDirectory("/code/lib/slowmath"));
    assertThat(relativePathFragment).isNull();
  }

  private static File createMockDirectory(String path) {
    File org = new File(path);
    File spy = Mockito.spy(org);
    Mockito.when(spy.isDirectory()).then((Answer<Boolean>) invocationOnMock -> true);
    return spy;
  }
}
