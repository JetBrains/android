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

import com.android.tools.idea.jps.output.parser.OutputLineReader;
import com.android.tools.idea.jps.output.parser.ParsingFailedException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.incremental.messages.CompilerMessage;

import java.util.Collection;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

class BadXmlBlockParser extends AbstractAaptOutputParser {
  private static final Pattern MSG_PATTERN = Pattern.compile("W/ResourceType\\(.*\\): Bad XML block: no root element node found");

  @Override
  public boolean parse(@NotNull String line, @NotNull OutputLineReader reader, @NotNull Collection<CompilerMessage> messages)
    throws ParsingFailedException {
    Matcher m = MSG_PATTERN.matcher(line);
    if (!m.matches()) {
      return false;
    }
    // W/ResourceType(12345): Bad XML block: no root element node found.
    // Sadly there's NO filename reference; this error typically describes the error *after* this line.
    if (reader.getLineCount() == 1) {
      // This is the only error message: dump to console and quit.
      throw new ParsingFailedException();
    }
    // Continue: the real culprit is displayed next and should get a marker.
    return true;
  }
}
