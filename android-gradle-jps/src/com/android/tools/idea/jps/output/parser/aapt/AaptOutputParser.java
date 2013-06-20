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
package com.android.tools.idea.jps.output.parser.aapt;

import com.android.tools.idea.jps.output.parser.CompilerOutputParser;
import com.android.tools.idea.jps.output.parser.OutputLineReader;
import com.android.tools.idea.jps.output.parser.ParsingFailedException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.incremental.messages.CompilerMessage;

import java.util.Collection;

/**
 * Parses aapt's output.
 */
public class AaptOutputParser implements CompilerOutputParser {
  private static final AbstractAaptOutputParser[] PARSERS = {
    new SkippingHiddenFileParser(),
    new Error1Parser(),
    // this needs to be tested before ERROR_2 since they both start with 'ERROR:'
    new Error6Parser(),
    new Error2Parser(),
    new Error3Parser(),
    new Error4Parser(),
    new Warning1Parser(),
    new Error5Parser(),
    new Error7Parser(),
    new Error8Parser(),
    new SkippingWarning2Parser(),
    new SkippingWarning1Parser(),
    new BadXmlBlockParser()
  };

  @Override
  public boolean parse(@NotNull String line, @NotNull OutputLineReader reader, @NotNull Collection<CompilerMessage> messages) {
    for (AbstractAaptOutputParser parser : PARSERS) {
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
}
