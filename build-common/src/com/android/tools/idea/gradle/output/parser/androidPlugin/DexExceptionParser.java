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

import com.android.ide.common.blame.Message;
import com.android.ide.common.blame.SourceFile;
import com.android.ide.common.blame.SourceFilePosition;
import com.android.ide.common.blame.SourcePosition;
import com.android.ide.common.blame.parser.ParsingFailedException;
import com.android.ide.common.blame.parser.PatternAwareOutputParser;
import com.android.ide.common.blame.parser.util.OutputLineReader;
import com.android.ide.common.blame.parser.util.ParserUtil;
import com.android.utils.ILogger;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A parser for exceptions thrown by dex. They are of the form:
 * <p/>
 * <pre>
 * UNEXPECTED TOP-LEVEL EXCEPTION:
 * [Stack trace]
 * </pre>
 */
public class DexExceptionParser implements PatternAwareOutputParser {
  private static final Pattern ERROR = Pattern.compile("UNEXPECTED TOP-LEVEL EXCEPTION:");
  private static final Pattern ALREADY_ADDED_EXCEPTION = Pattern.compile("already added: L(.+);");

  @Override
  public boolean parse(@NotNull String line, @NotNull OutputLineReader reader, @NotNull List<Message> messages, @NotNull ILogger logger)
    throws ParsingFailedException {
    Matcher m1 = ERROR.matcher(line);
    if (!m1.matches()) {
      return false;
    }
    String stackTrace = ParserUtil.digestStackTrace(reader);
    if (stackTrace == null) {
      return false;
    }
    Matcher m2 = ALREADY_ADDED_EXCEPTION.matcher(stackTrace);
    if (!m2.matches()) {
      return false;
    }
    String message = String.format("Class %1s has already been added to output. Please remove duplicate copies.",
                                   m2.group(1).replace('/', '.').replace('$', '.'));
    messages.add(new Message(Message.Kind.ERROR, message, new SourceFilePosition(SourceFile.UNKNOWN, SourcePosition.UNKNOWN)));
    return true;
  }
}
