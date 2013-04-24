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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.incremental.messages.CompilerMessage;

import java.util.List;

/**
 * Parses the Gradle's error output and creates error messages if appropriate.
 */
public class GradleErrorOutputParser {
  /**
   * Parses the given error output (from Gradle) and creates error messages if appropriate. This
   * parser can parse errors from java and aapt.
   *
   * @param output the given error output.
   * @return error messages created from the given output. An empty list is returned if this parser
   * did not recognize any errors in the output or if an error occurred while parsing the given
   * output.
   */
  @NotNull
  public List<CompilerMessage> parseErrorOutput(@NotNull String output) {
    LineReader outputReader = new LineReader(output);
    AaptProblemMessageFactory aaptMessageFactory = new AaptProblemMessageFactory(outputReader);
    JavaProblemMessageFactory javaMessageFactory = new JavaProblemMessageFactory(outputReader);

    if (outputReader.getLineCount() == 0) {
      return ImmutableList.of();
    }

    List<CompilerMessage> messages = Lists.newArrayList();
    String line;
    while ((line = outputReader.readLine()) != null) {
      List<ProblemParser> parsers = allParsers(aaptMessageFactory, javaMessageFactory);
      for (ProblemParser parser : parsers) {
        ProblemParser.ParsingResult result = parser.parse(line);
        if (!result.matched) {
          continue;
        }
        if (result.ignored) {
          break;
        }
        CompilerMessage msg = result.message;
        if (msg == null) {
          return ImmutableList.of();
        }
        messages.add(msg);
      }
    }
    return messages;
  }

  @NotNull
  private static List<ProblemParser> allParsers(@NotNull AaptProblemMessageFactory aaptMessageFactory,
                                                @NotNull JavaProblemMessageFactory javaMessageFactory) {
    List<ProblemParser> parsers = Lists.newArrayListWithExpectedSize(13);
    parsers.add(new SkippingHiddenFileParser());
    parsers.add(new Error1Parser(aaptMessageFactory));
    // this needs to be tested before ERROR_2 since they both start with 'ERROR:'
    parsers.add(new Error6Parser(aaptMessageFactory));
    parsers.add(new Error2Parser(aaptMessageFactory));
    parsers.add(new Error3Parser(aaptMessageFactory));
    parsers.add(new Error4Parser(aaptMessageFactory));
    parsers.add(new Warning1Parser(aaptMessageFactory));
    parsers.add(new Error5Parser(aaptMessageFactory, javaMessageFactory));
    parsers.add(new Error7Parser(aaptMessageFactory));
    parsers.add(new Error8Parser(aaptMessageFactory));
    parsers.add(new SkippingWarning2Parser(aaptMessageFactory));
    parsers.add(new SkippingWarning1Parser(aaptMessageFactory));
    parsers.add(new BadXmlBlockParser(aaptMessageFactory));
    return parsers;
  }
}
