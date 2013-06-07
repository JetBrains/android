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
package com.android.tools.idea.jps.output.parser;

import com.android.tools.idea.jps.output.parser.aapt.AaptOutputParser;
import com.android.tools.idea.jps.output.parser.androidPlugin.AndroidPluginOutputParser;
import com.android.tools.idea.jps.output.parser.javac.JavacOutputParser;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.incremental.messages.CompilerMessage;

import java.util.Collection;

/**
 * Parses Gradle's error output and creates error/warning messages when appropriate.
 */
public class GradleErrorOutputParser {
  private static final CompilerOutputParser[] PARSERS = {new AaptOutputParser(), new AndroidPluginOutputParser(), new JavacOutputParser()};

  /**
   * Parses the given Gradle output and creates error/warning messages when appropriate. This parser can parse errors from java and aapt.
   *
   * @param output the given error output.
   * @return error messages created from the given output. An empty list is returned if this parser did not recognize any errors in the
   *         output or if an error occurred while parsing the given output.
   */
  @NotNull
  public Collection<CompilerMessage> parseErrorOutput(@NotNull String output) {
    OutputLineReader outputReader = new OutputLineReader(output);

    if (outputReader.getLineCount() == 0) {
      return ImmutableList.of();
    }

    Collection<CompilerMessage> messages = Lists.newArrayList();
    String line;
    while ((line = outputReader.readLine()) != null) {
      for (CompilerOutputParser parser : PARSERS) {
        try {
          if (parser.parse(line, outputReader, messages)) {
            break;
          }
        }
        catch (ParsingFailedException e) {
          return ImmutableList.of();
        }
      }
    }
    return messages;
  }
}
