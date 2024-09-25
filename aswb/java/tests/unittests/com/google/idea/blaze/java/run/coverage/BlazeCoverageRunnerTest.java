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
package com.google.idea.blaze.java.run.coverage;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.base.Joiner;
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import com.google.idea.blaze.base.sync.workspace.WorkspacePathResolver;
import com.google.idea.blaze.base.sync.workspace.WorkspacePathResolverImpl;
import com.intellij.rt.coverage.data.LineData;
import com.intellij.rt.coverage.data.ProjectData;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Unit tests for {@link BlazeCoverageRunner} */
@RunWith(JUnit4.class)
public class BlazeCoverageRunnerTest {

  private static final File root = new File("/root");
  private static final WorkspacePathResolver mockResolver =
      new WorkspacePathResolverImpl(new WorkspaceRoot(root));

  @Test
  public void testParseSingleFile() throws IOException {
    ProjectData data =
        BlazeCoverageRunner.parseCoverage(
            mockResolver,
            inputStream(
                "SF:path/to/file.txt", "DA:4,0", "DA:8,0", "DA:9,1", "DA:23,3", "end_of_record"));
    assertThat(data.getClasses()).hasSize(1);

    LineData[] lines = (LineData[]) data.getClassData("/root/path/to/file.txt").getLines();
    assertThat(lines).hasLength(24);
    assertEquals(lines[4], lineData(4, 0));
    assertEquals(lines[9], lineData(9, 1));
    assertEquals(lines[23], lineData(23, 3));
  }

  @Test
  public void testParseMultipleFiles() throws IOException {
    ProjectData data =
        BlazeCoverageRunner.parseCoverage(
            mockResolver,
            inputStream(
                "SF:path/to/file.txt",
                "DA:4,0",
                "DA:8,0",
                "DA:9,1",
                "DA:23,3",
                "end_of_record",
                "SF:path/to/another/file.txt",
                "DA:1,1",
                "DA:2,2",
                "DA:5,0",
                "DA:123,1",
                "end_of_record"));
    assertThat(data.getClasses()).hasSize(2);
    assertThat(data.getClassData("/root/path/to/file.txt").getLines()).hasLength(24);
    assertThat(data.getClassData("/root/path/to/another/file.txt").getLines()).hasLength(124);
  }

  @Test
  public void testIgnoreUnrecognizedPrefixes() throws IOException {
    ProjectData data =
        BlazeCoverageRunner.parseCoverage(
            mockResolver,
            inputStream(
                "SF:path/to/file.txt",
                "DA:4,0",
                "DA:8,0",
                "FS:0",
                "unrecognized junk",
                "DA:9,1",
                "DA:23,3",
                "end_of_record"));
    assertThat(data.getClasses()).hasSize(1);

    LineData[] lines = (LineData[]) data.getClassData("/root/path/to/file.txt").getLines();
    assertThat(lines).hasLength(24);
    assertEquals(lines[4], lineData(4, 0));
    assertEquals(lines[9], lineData(9, 1));
    assertEquals(lines[23], lineData(23, 3));
  }

  private static void assertEquals(LineData line1, LineData line2) {
    assertThat(line1.getHits()).isEqualTo(line2.getHits());
    assertThat(line1.getLineNumber()).isEqualTo(line2.getLineNumber());
  }

  private static LineData lineData(int line, int hits) {
    LineData data = new LineData(line, null);
    data.setHits(hits);
    return data;
  }

  private static InputStream inputStream(String... lines) {
    String string = Joiner.on('\n').join(lines);
    return new ByteArrayInputStream(string.getBytes(StandardCharsets.UTF_8));
  }
}
