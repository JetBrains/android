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

class Error8Parser extends AbstractAaptOutputParser {
  /**
   * 2-line aapt error
   * <pre>
   * ERROR: Invalid configuration: foo
   *                               ^^^
   * </pre>
   * There's no need to parse the 2nd line.
   */
  private static final Pattern MSG_PATTERN = Pattern.compile("^Invalid configuration: (.+)$");

  @Override
  public boolean parse(@NotNull String line, @NotNull OutputLineReader reader, @NotNull Collection<CompilerMessage> messages)
    throws ParsingFailedException {
    Matcher m = MSG_PATTERN.matcher(line);
    if (!m.matches()) {
      return false;
    }
    String badConfig = m.group(1);
    String msgText = String.format("APK Configuration filter '%1$s' is invalid", badConfig);
    // skip the next line
    reader.skipNextLine();
    CompilerMessage msg = createErrorMessage(msgText, null, null);
    messages.add(msg);
    return true;
  }
}
