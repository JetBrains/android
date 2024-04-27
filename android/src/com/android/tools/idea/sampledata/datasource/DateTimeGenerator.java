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

import org.jetbrains.annotations.NotNull;

import java.io.OutputStream;
import java.io.PrintStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalUnit;
import java.util.Random;
import java.util.function.Function;

public class DateTimeGenerator implements Function<OutputStream, Exception> {
  private final long myNow = System.currentTimeMillis();
  private final DateTimeFormatter myFormat;
  private final TemporalUnit myIncrements;
  private final int myIncrementStep;

  public DateTimeGenerator(@NotNull DateTimeFormatter format, int incrementStep, @NotNull TemporalUnit increments) {
    myFormat = format;
    myIncrementStep = incrementStep;
    myIncrements = increments;
  }

  public DateTimeGenerator(@NotNull DateTimeFormatter format, @NotNull TemporalUnit increments) {
    this(format, 1, increments);
  }

  @Override
  public Exception apply(OutputStream stream) {
    //noinspection IOResourceOpenedButNotSafelyClosed (Closed by the caller)
    PrintStream printStream = new PrintStream(stream);
    Random random = new Random();
    long amount = random.nextInt(200) * myIncrementStep;
    LocalDateTime time = LocalDateTime.now().minus(amount, myIncrements);
    for (int i = 0; i < 500; i++) {
      printStream.println(time.format(myFormat));
      time = time.plus(random.nextInt(10) * myIncrementStep, myIncrements);
    }

    return null;
  }
}
