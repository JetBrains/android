/*
 * Copyright (C) 2013 The Android Open Source Project
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
package com.android.tools.idea.jps.parser;

import com.android.tools.idea.jps.AndroidGradleJps;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.incremental.messages.BuildMessage;
import org.jetbrains.jps.incremental.messages.CompilerMessage;

/**
 * Creates a {@link org.jetbrains.jps.incremental.messages.CompilerMessage} from a parsed line of Javac output.
 */
class JavaProblemMessageFactory extends ProblemMessageFactory {
  JavaProblemMessageFactory(@NotNull LineReader outputReader) {
    super(outputReader);
  }

  @Nullable
  @Override
  CompilerMessage createMessage(@NotNull String msgText,
                                @NotNull BuildMessage.Kind severity,
                                @Nullable String sourcePath,
                                @Nullable String lineNumber) {
    long locationLine = -1L;
    if (lineNumber != null) {
      try {
        locationLine = Integer.parseInt(lineNumber);
      }
      catch (NumberFormatException e) {
        return null;
      }
    }
    int errorIndex = -1;
    // We look at most 4 lines below, searching for the location of the error, marked by "^"
    for (int i = 1; i < 5; i++) {
      String line = getOutputReader().peek(i);
      if (line == null) {
        break;
      }
      if (line.trim().equals("^")) {
        errorIndex = line.indexOf('^');
        break;
      }
    }
    long locationColumn = -1;
    if (errorIndex != -1) {
      locationColumn = errorIndex + 1;
    }
    return AndroidGradleJps.createCompilerMessage(sourcePath, msgText, severity, locationLine, locationColumn);
  }
}
