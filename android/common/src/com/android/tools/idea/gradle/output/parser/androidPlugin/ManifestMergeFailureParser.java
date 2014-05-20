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
package com.android.tools.idea.gradle.output.parser.androidPlugin;

import com.android.tools.idea.gradle.output.GradleMessage;
import com.android.tools.idea.gradle.output.parser.PatternAwareOutputParser;
import com.android.tools.idea.gradle.output.parser.OutputLineReader;
import com.android.tools.idea.gradle.output.parser.ParsingFailedException;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A parser for errors that happen during manifest merging
 * <p>
 * The error will be in one of these formats:
 * <pre>
 * [path:line] message
 * </pre>
 */
public class ManifestMergeFailureParser implements PatternAwareOutputParser {
  // Only allow : in the second position (Windows drive letter)
  private static final Pattern ERROR = Pattern.compile("\\[([^:].[^:]+):(\\d+)\\] (.+)");
  @Override
  public boolean parse(@NotNull String line, @NotNull OutputLineReader reader, @NotNull List<GradleMessage> messages)
    throws ParsingFailedException {
    Matcher m = ERROR.matcher(line);
    if (m.matches()) {
      String sourcePath = m.group(1);
      int lineNumber = Integer.parseInt(m.group(2));
      String message = m.group(3);
      messages.add(new GradleMessage(GradleMessage.Kind.ERROR, message, sourcePath, lineNumber, -1));
      return true;
    }
    return false;
  }
}