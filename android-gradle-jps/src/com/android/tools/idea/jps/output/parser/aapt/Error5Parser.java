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

import com.android.SdkConstants;
import com.android.tools.idea.jps.output.parser.OutputLineReader;
import com.android.tools.idea.jps.output.parser.ParsingFailedException;
import com.google.common.collect.ImmutableList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.incremental.messages.CompilerMessage;

import java.util.Collection;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

class Error5Parser extends AbstractAaptOutputParser {
  /**
   * Single-line aapt error.
   * <pre>
   * &lt;path&gt;:&lt;line&gt;: error: Error: &lt;error&gt;
   * &lt;path&gt;:&lt;line&gt;: error: &lt;error&gt;
   * &lt;path&gt;:&lt;line&gt;: &lt;error&gt;
   * </pre>
   */
  private static final List<Pattern> MSG_PATTERNS = ImmutableList.of(
    Pattern.compile("^(.+?):(\\d+): error: Error:\\s+(.+)$"),
    Pattern.compile("^(.+?):(\\d+): error:\\s+(.+)$"),
    Pattern.compile("^(.+?):(\\d+):\\s+(.+)$")
  );

  @Override
  public boolean parse(@NotNull String line, @NotNull OutputLineReader reader, @NotNull Collection<CompilerMessage> messages)
    throws ParsingFailedException {
    for (Pattern pattern : MSG_PATTERNS) {
      Matcher m = pattern.matcher(line);
      if (m.matches()) {
        String sourcePath = m.group(1);
        String lineNumber = m.group(2);
        String msgText = m.group(3);
        if (sourcePath.endsWith(SdkConstants.DOT_JAVA)) {
          return false;
        }
        CompilerMessage msg = createErrorMessage(msgText, sourcePath, lineNumber);
        messages.add(msg);
        return true;
      }
    }
    return false;
  }
}
