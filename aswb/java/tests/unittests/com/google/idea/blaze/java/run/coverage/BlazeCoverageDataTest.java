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
import com.google.common.collect.ImmutableMap;
import com.google.idea.blaze.java.run.coverage.BlazeCoverageData.FileData;
import gnu.trove.TIntIntHashMap;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.function.Function;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Unit tests for {@link BlazeCoverageData} */
@RunWith(JUnit4.class)
public class BlazeCoverageDataTest {

  @Test
  public void testParseSingleFile() throws IOException {
    BlazeCoverageData data =
        BlazeCoverageData.parse(
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
    assertThat(data.perFileData).hasSize(2);

    FileData fileData = data.perFileData.get("path/to/file.txt");
    assertThat(fileData.source).isEqualTo("path/to/file.txt");
    assertThat(toMap(fileData.lineHits)).containsExactly(4, 0, 8, 0, 9, 1, 23, 3);

    fileData = data.perFileData.get("path/to/another/file.txt");
    assertThat(fileData.source).isEqualTo("path/to/another/file.txt");
    assertThat(toMap(fileData.lineHits)).containsExactly(1, 1, 2, 2, 5, 0, 123, 1);
  }

  @Test
  public void testEmptyFilesIgnored() throws IOException {
    BlazeCoverageData data =
        BlazeCoverageData.parse(
            inputStream(
                "SF:path/to/file.txt",
                "FS:0",
                "unrecognized junk",
                "end_of_record",
                "SF:path/to/another/file.txt",
                "DA:1,1",
                "DA:2,2",
                "DA:5,0",
                "DA:123,1",
                "end_of_record"));
    assertThat(data.perFileData.keySet()).containsExactly("path/to/another/file.txt");
  }

  private static ImmutableMap<Integer, Integer> toMap(TIntIntHashMap troveMap) {
    return Arrays.stream(troveMap.keys())
        .boxed()
        .collect(ImmutableMap.toImmutableMap(Function.identity(), troveMap::get));
  }

  private static InputStream inputStream(String... lines) {
    String string = Joiner.on('\n').join(lines);
    return new ByteArrayInputStream(string.getBytes(StandardCharsets.UTF_8));
  }
}
