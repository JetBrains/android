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

class SkippingWarning1Parser extends AbstractAaptOutputParser {
  /**
   * Error message emitted when aapt skips a file because for example it's name is
   * invalid, such as a layout file name which starts with _.
   * <p/>
   * This error message is used by AAPT in Tools 19 and earlier.
   */
  private static final Pattern MSG_PATTERN = Pattern.compile("    \\(skipping (.+) .+ '(.*)'\\)");

  @Override
  public boolean parse(@NotNull String line, @NotNull OutputLineReader reader, @NotNull Collection<CompilerMessage> messages)
    throws ParsingFailedException {
    Matcher m = MSG_PATTERN.matcher(line);
    if (!m.matches()) {
      return false;
    }
    String sourcePath = m.group(2);
    // Certain files can safely be skipped without marking the project as having errors.
    // See isHidden() in AaptAssets.cpp:
    String type = m.group(1);
    if (type.equals("backup")         // main.xml~, etc
        || type.equals("hidden")      // .gitignore, etc
        || type.equals("index")) {    // thumbs.db, etc
      return true;
    }
    CompilerMessage msg = createWarningMessage(line, sourcePath, null);
    messages.add(msg);
    return true;
  }
}
