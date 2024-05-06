/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.tools.idea.sampledata.datasource;

import com.google.common.collect.ImmutableList;
import com.google.common.io.CharStreams;
import org.jetbrains.annotations.NotNull;

import java.io.*;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;

/**
 * Data source that combines the lines of two different input streams.
 */
public class CombinerDataSource implements Function<OutputStream, Exception> {
  private final ImmutableList<String> myCombined;

  public CombinerDataSource(@NotNull InputStream inputA, @NotNull InputStream inputB) {
    ImmutableList<String> combined;
    try {
      List<String> linesA = CharStreams.readLines(new InputStreamReader(inputA));
      List<String> linesB = CharStreams.readLines(new InputStreamReader(inputB));

      Collections.shuffle(linesA);
      Collections.shuffle(linesB);

      assert !linesA.isEmpty() && !linesB.isEmpty();

      int len = Math.max(linesA.size(), linesB.size());
      ImmutableList.Builder<String> combinedBuilder = ImmutableList.builder();

      int indexA = 0, indexB = 0;
      for (int i = 0; i < len; i++) {
        if (indexA == linesA.size()) {
          indexA = 0;
        }
        if (indexB == linesB.size()) {
          indexB = 0;
        }

        combinedBuilder.add(linesA.get(indexA) + " " + linesB.get(indexB));
        indexA++;
        indexB++;
      }
      combined = combinedBuilder.build();
    }
    catch (IOException ignored) {
      combined = ImmutableList.of();
    }

    myCombined = combined;
  }

  @Override
  public Exception apply(OutputStream stream) {
    //noinspection IOResourceOpenedButNotSafelyClosed (Closed by the caller)
    PrintStream printStream = new PrintStream(stream);
    myCombined.forEach(printStream::println);

    return null;
  }
}
