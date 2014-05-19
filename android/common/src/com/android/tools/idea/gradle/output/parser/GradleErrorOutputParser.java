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
package com.android.tools.idea.gradle.output.parser;

import com.android.tools.idea.gradle.output.GradleMessage;
import com.android.tools.idea.gradle.output.parser.aapt.AaptOutputParser;
import com.android.tools.idea.gradle.output.parser.androidPlugin.AndroidPluginOutputParser;
import com.android.tools.idea.gradle.output.parser.javac.JavacOutputParser;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Parses Gradle's error output and creates error/warning messages when appropriate.
 */
public class GradleErrorOutputParser {
  private static final CompilerOutputParser[] PARSERS =
    {new AaptOutputParser(), new AndroidPluginOutputParser(), new JavacOutputParser(), new IgnoredMessagesOutputParser()};

  /**
   * Parses the given Gradle output and creates error/warning messages when appropriate. This parser can parse errors from java and aapt.
   *
   * @param output the given error output.
   * @return error messages created from the given output. An empty list is returned if this parser did not recognize any errors in the
   * output or if an error occurred while parsing the given output.
   */
  @NotNull
  public List<GradleMessage> parseErrorOutput(@NotNull String output) {
    OutputLineReader outputReader = new OutputLineReader(output);

    if (outputReader.getLineCount() == 0) {
      return ImmutableList.of();
    }

    List<GradleMessage> messages = Lists.newArrayList();
    String line;
    while ((line = outputReader.readLine()) != null) {
      if (line.isEmpty()) {
        continue;
      }
      boolean handled = false;
      for (CompilerOutputParser parser : PARSERS) {
        try {
          if (parser.parse(line, outputReader, messages)) {
            handled = true;
            break;
          }
        }
        catch (ParsingFailedException e) {
          return ImmutableList.of();
        }
      }
      if (!handled) {
        // If none of the standard parsers recognize the input, include it as info such
        // that users don't miss potentially vital output such as gradle plugin exceptions.
        // If there is predictable useless input we don't want to appear here, add a custom
        // parser to digest it.
        messages.add(new GradleMessage(GradleMessage.Kind.SIMPLE, line));
      }
    }
    return messages;
  }
}
