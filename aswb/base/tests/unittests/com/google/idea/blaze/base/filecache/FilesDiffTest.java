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
package com.google.idea.blaze.base.filecache;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.idea.blaze.base.BlazeTestCase;
import com.google.idea.blaze.base.async.executor.BlazeExecutor;
import com.google.idea.blaze.base.async.executor.MockBlazeExecutor;
import com.google.idea.blaze.base.io.FileOperationProvider;
import com.google.idea.common.experiments.ExperimentService;
import com.google.idea.common.experiments.MockExperimentService;
import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests for {@link FilesDiff} */
@RunWith(JUnit4.class)
public class FilesDiffTest extends BlazeTestCase {
  private MockFileOperationProvider fileModificationProvider;

  private static class MockFileOperationProvider extends FileOperationProvider {
    Map<File, Long> times = new HashMap<>();

    @CanIgnoreReturnValue
    public MockFileOperationProvider put(File file, long time) {
      times.put(file, time);
      return this;
    }

    @Override
    public long getFileModifiedTime(@NotNull File file) {
      return times.get(file);
    }
  }

  @Override
  protected void initTest(
      @NotNull Container applicationServices, @NotNull Container projectServices) {
    super.initTest(applicationServices, projectServices);
    applicationServices.register(ExperimentService.class, new MockExperimentService());
    applicationServices.register(BlazeExecutor.class, new MockBlazeExecutor());

    this.fileModificationProvider = new MockFileOperationProvider();
    applicationServices.register(FileOperationProvider.class, fileModificationProvider);
  }

  @Test
  public void testDiffWithDiffMethodTimestamp() throws Exception {
    ImmutableMap<File, Long> oldState =
        ImmutableMap.<File, Long>builder()
            .put(new File("file1"), 13L)
            .put(new File("file2"), 17L)
            .put(new File("file3"), 21L)
            .build();
    List<File> fileList = ImmutableList.of(new File("file1"), new File("file2"));
    fileModificationProvider.put(new File("file1"), 13).put(new File("file2"), 122);

    FilesDiff<File, File> diff = FilesDiff.diffFileTimestamps(oldState, fileList);

    assertThat(diff.getUpdatedFiles()).containsExactly(new File("file2"));
    assertThat(diff.getRemovedFiles()).containsExactly(new File("file3"));
  }
}
