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
import com.google.common.base.Strings;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.incremental.messages.CompilerMessage;

import java.util.Collection;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

class Error6Parser extends AbstractAaptOutputParser {
  /**
   * 4-line aapt error.
   * <pre>
   * ERROR: 9-path image &lt;path&gt; malformed
   * </pre>
   * <p/>
   * Line 2 and 3 are taken as-is while line 4 is ignored. It repeats with
   * <pre>
   * ERROR: failure processing &lt;path&gt;
   * </pre>
   */
  private static final Pattern MSG_PATTERN = Pattern.compile("^ERROR:\\s+9-patch\\s+image\\s+(.+)\\s+malformed\\.$");

  @Override
  public boolean parse(@NotNull String line, @NotNull OutputLineReader reader, @NotNull Collection<CompilerMessage> messages)
    throws ParsingFailedException {
    Matcher m = MSG_PATTERN.matcher(line);
    if (!m.matches()) {
      return false;
    }
    String sourcePath = m.group(1);
    String msgText = line; // default message is the line in case we don't find anything else
    if (reader.hasNextLine()) {
      msgText = Strings.nullToEmpty(reader.readLine()).trim();
      if (reader.hasNextLine()) {
        msgText = msgText + " - " + Strings.nullToEmpty(reader.readLine()).trim();
        // skip the next line
        reader.skipNextLine();
      }
    }
    CompilerMessage msg = createErrorMessage(msgText, sourcePath, null);
    messages.add(msg);
    return true;
  }
}
