/*
 * Copyright (C) 2014 The Android Open Source Project
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

import com.android.ide.common.blame.Message;
import com.android.ide.common.blame.SourceFilePosition;
import com.android.ide.common.blame.parser.ParsingFailedException;
import com.android.ide.common.blame.parser.PatternAwareOutputParser;
import com.android.ide.common.blame.parser.util.OutputLineReader;
import com.android.utils.ILogger;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.regex.Pattern;

public class GradleOutputParser implements PatternAwareOutputParser {
  private static final Pattern ERROR_COUNT_PATTERN = Pattern.compile("[\\d]+ error(s)?");

  @Override
  public boolean parse(@NotNull String line, @NotNull OutputLineReader reader, @NotNull List<Message> messages, @NotNull ILogger logger)
    throws ParsingFailedException {
    if (ignoreMessage(line)) {
      return true;
    }
    if (line.endsWith("is an incubating feature.") || line.contains("has been deprecated and is scheduled to be removed in Gradle")) {
      // These are warnings about using incubating/internal Gradle APIs. They do not add any value to our users. In fact, we got reports
      // that those warnings are confusing.
      // We just hide the message.
      return true;
    }
    if (line.startsWith("Total time: ") || line.startsWith("BUILD ")) {
      messages.add(new Message(Message.Kind.INFO, line, SourceFilePosition.UNKNOWN));
      return true;
    }

    return false;
  }

  private static boolean ignoreMessage(@NotNull String line) {
    return line.trim().equalsIgnoreCase("FAILED") || ERROR_COUNT_PATTERN.matcher(line).matches();
  }
}
