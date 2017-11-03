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

import com.intellij.codeInsight.template.emmet.generators.LoremGenerator;

import java.io.OutputStream;
import java.io.PrintStream;
import java.util.Random;
import java.util.function.Function;

public class LoremIpsumGenerator implements Function<OutputStream, Exception> {
  final LoremGenerator myLoremGenerator = new LoremGenerator();
  /**
   * If true, each line of the data source will contain a random length and will start at a different point.
   * When false, each line will contain an extra word compared to the previous one.
   */
  private final boolean isRandom;

  public LoremIpsumGenerator(boolean random) {
    isRandom = random;
  }

  @Override
  public Exception apply(OutputStream stream) {
    //noinspection IOResourceOpenedButNotSafelyClosed (Closed by the caller)
    PrintStream printStream = new PrintStream(stream);
    Random random = new Random();
    for (int i = 0; i < 500; i++) {
      int wordCount = (isRandom ? random.nextInt(500) : i) + 1;
      printStream.println(myLoremGenerator.generate(wordCount, !isRandom));
    }

    return null;
  }
}
