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
package com.android.tools.idea.gradle.output.parser.androidPlugin;

import com.android.tools.idea.gradle.output.GradleMessage;
import com.android.tools.idea.gradle.output.parser.OutputLineReader;
import com.android.tools.idea.gradle.output.parser.CompilerOutputParser;
import com.android.tools.idea.gradle.output.parser.ParsingFailedException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Parses output from the Android Gradle plugin.
 */
public class AndroidPluginOutputParser implements CompilerOutputParser {
  private static final CompilerOutputParser[] PARSERS = {
    new XmlValidationErrorParser(), new GradleBuildFailureParser(), new MergingExceptionParser(), new ManifestMergeFailureParser(),
    new DexExceptionParser()
  };

  @Override
  public boolean parse(@NotNull String line, @NotNull OutputLineReader reader, @NotNull List<GradleMessage> messages) {
    for (CompilerOutputParser parser : PARSERS) {
      try {
        if (parser.parse(line, reader, messages)) {
          return true;
        }
      }
      catch (ParsingFailedException e) {
        // If there's an exception, it means a parser didn't like the input, so just ignore and let other parsers have a crack at it.
      }
    }
    return false;
  }

  @Nullable
  public static String digestStackTrace(OutputLineReader reader) {
    String message = null;
    String next = reader.peek(0);
    if (next == null) {
      return null;
    }
    int index = next.indexOf(':');
    if (index == -1) {
      return null;
    }

    String exceptionName = next.substring(0, index);
    if (exceptionName.endsWith("Exception") || exceptionName.endsWith("Error")) {
      message = next.substring(index + 1).trim();
      reader.readLine();

      // Digest stack frames below it
      while (true) {
        String peek = reader.peek(0);
        if (peek != null && peek.startsWith("\tat")) {
          reader.readLine();
        } else {
          break;
        }
      }
    }

    return message;
  }
}
