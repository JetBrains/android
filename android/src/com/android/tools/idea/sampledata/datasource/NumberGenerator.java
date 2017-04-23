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

import java.io.OutputStream;
import java.io.PrintStream;
import java.util.Random;
import java.util.function.Function;

public class NumberGenerator implements Function<OutputStream, Exception> {
  private final String myFormat;
  private final int myMax;
  private final int myMin;

  public NumberGenerator(String format, int min, int max) {
    myFormat = format + "\n";
    myMax = max;
    myMin = min;
  }

  @Override
  public Exception apply(OutputStream stream) {
    //noinspection IOResourceOpenedButNotSafelyClosed (Closed by the caller)
    PrintStream printStream = new PrintStream(stream);
    Random rand = new Random();
    for (int i = 0; i < 500; i++) {
      printStream.printf(myFormat, myMin + rand.nextInt((myMax - myMin) + 1));
    }

    return null;
  }
}
