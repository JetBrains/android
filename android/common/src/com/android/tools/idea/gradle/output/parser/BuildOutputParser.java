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
import com.android.tools.idea.gradle.output.parser.androidPlugin.DexExceptionParser;
import com.android.tools.idea.gradle.output.parser.androidPlugin.ManifestMergeFailureParser;
import com.android.tools.idea.gradle.output.parser.androidPlugin.MergingExceptionParser;
import com.android.tools.idea.gradle.output.parser.androidPlugin.XmlValidationErrorParser;
import com.android.tools.idea.gradle.output.parser.javac.JavacOutputParser;
import com.google.common.collect.Lists;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;

/**
 * Parses Gradle's build output and creates the messages to be displayed in the "Messages" tool window.
 */
public class BuildOutputParser {
  private static final PatternAwareOutputParser[] PARSERS = {
    new GradleOutputParser(), new AaptOutputParser(), new XmlValidationErrorParser(), new BuildFailureParser(),
    new MergingExceptionParser(), new ManifestMergeFailureParser(), new DexExceptionParser(), new JavacOutputParser(),
  };

  /**
   * Parses the given Gradle output and creates the messages to be displayed in the "Messages" tool window.
   *
   * @param output the given Gradle output.
   * @return error messages created from the given output. An empty list is returned if this parser did not recognize any errors in the
   * output or if an error occurred while parsing the given output.
   */
  @NotNull
  public List<GradleMessage> parseGradleOutput(@NotNull String output) {
    OutputLineReader outputReader = new OutputLineReader(output);

    if (outputReader.getLineCount() == 0) {
      return Collections.emptyList();
    }

    List<GradleMessage> messages = Lists.newArrayList();
    String line;
    while ((line = outputReader.readLine()) != null) {
      if (line.isEmpty()) {
        continue;
      }
      boolean handled = false;
      for (PatternAwareOutputParser parser : PARSERS) {
        try {
          if (parser.parse(line, outputReader, messages)) {
            handled = true;
            break;
          }
        }
        catch (ParsingFailedException e) {
          return Collections.emptyList();
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
