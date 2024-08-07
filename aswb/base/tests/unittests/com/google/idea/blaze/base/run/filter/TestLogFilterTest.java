/*
 * Copyright 2019 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.base.run.filter;

import static com.google.common.truth.Truth.assertThat;

import com.google.idea.blaze.base.BlazeTestCase;
import com.google.idea.blaze.base.io.VirtualFileSystemProvider;
import com.intellij.execution.filters.Filter.Result;
import com.intellij.execution.filters.OpenFileHyperlinkInfo;
import com.intellij.mock.MockLocalFileSystem;
import java.io.File;
import java.util.HashMap;
import java.util.Map;
import javax.annotation.Nullable;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Unit tests for {@link TestLogFilter}. */
@RunWith(JUnit4.class)
public class TestLogFilterTest extends BlazeTestCase {

  private static final File mockFile = new File("filename");
  private static final Map<String, File> filePathToFile = new HashMap<>();

  @Override
  protected void initTest(Container applicationServices, Container projectServices) {
    registerExtensionPoint(FileResolver.EP_NAME, FileResolver.class)
        .registerExtension((project, path) -> filePathToFile.get(path));
    applicationServices.register(VirtualFileSystemProvider.class, MockLocalFileSystem::new);
  }

  @After
  public final void doTearDown() {
    filePathToFile.clear();
  }

  @Test
  public void testMatchesAbsoluteFilePath() {
    filePathToFile.put("/absolute/path/testlogs/file/test.log", mockFile);
    Result match = findMatch("/absolute/path/testlogs/file/test.log");
    assertLinksToMockFile(match);
  }

  @Test
  public void testHandlesLeadingAndTrailingWhitespace() {
    filePathToFile.put("/absolute/path/testlogs/file/test.log", mockFile);
    Result match = findMatch("   /absolute/path/testlogs/file/test.log  ");
    assertLinksToMockFile(match);
  }

  @Test
  public void testIgnoresFileWithDifferentName() {
    // extra 's' in the file extension
    filePathToFile.put("/absolute/path/testlogs/file/test.logs", mockFile);
    assertThat(findMatch("/absolute/path/testlogs/file/test.logs")).isNull();
  }

  @Test
  public void testIgnoresFileWithoutTestlogsPathComponent() {
    filePathToFile.put("/absolute/path/file/test.log", mockFile);
    assertThat(findMatch("/absolute/path/file/test.log")).isNull();
  }

  @Test
  public void testIgnoresRelativeFilePath() {
    filePathToFile.put("file/testlogs/path/test.log", mockFile);
    assertThat(findMatch("file/testlogs/path/test.log")).isNull();
  }

  @Test
  public void testNewFormat() {
    filePathToFile.put("/absolute/path/testlogs/file/test.log", mockFile);
    Result match =
        findMatch(
            "FAIL: //some/path/to/target:TestTarget (see /absolute/path/testlogs/file/test.log)");
    assertLinksToMockFile(match);
  }

  @Nullable
  private Result findMatch(String line) {
    return new TestLogFilter(project).applyFilter(line, line.length());
  }

  private void assertLinksToMockFile(@Nullable Result result) {
    assertThat(result).isNotNull();
    assertThat(result.getFirstHyperlinkInfo()).isInstanceOf(OpenFileHyperlinkInfo.class);

    OpenFileHyperlinkInfo link = (OpenFileHyperlinkInfo) result.getFirstHyperlinkInfo();
    assertThat(link.getVirtualFile().getName()).isEqualTo(mockFile.getName());
  }
}
