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
import com.android.tools.idea.gradle.output.parser.CompilerOutputParser;
import com.android.tools.idea.gradle.output.parser.OutputLineReader;
import com.android.tools.idea.gradle.output.parser.ParsingFailedException;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A parser for exceptions thrown by dex. They are of the form:
 *
 * <pre>
 * UNEXPECTED TOP-LEVEL EXCEPTION:
 * [Stack trace]
 * </pre>
 */
public class DexExceptionParser implements CompilerOutputParser {
  private static final Pattern ERROR = Pattern.compile("UNEXPECTED TOP-LEVEL EXCEPTION:");
  private static final Pattern ALREADY_ADDED_EXCEPTION = Pattern.compile("already added: L(.+);");

  @Override
  public boolean parse(@NotNull String line, @NotNull OutputLineReader reader, @NotNull List<GradleMessage> messages)
    throws ParsingFailedException {
    Matcher m1 = ERROR.matcher(line);
    if (!m1.matches()) {
      return false;
    }
    String stackTrace = AndroidPluginOutputParser.digestStackTrace(reader);
    if (stackTrace == null) {
      return false;
    }
    Matcher m2 = ALREADY_ADDED_EXCEPTION.matcher(stackTrace);
    if (!m2.matches()) {
      return false;
    }
    String message = String.format("Class %1s has already been added to output. Please remove duplicate copies.",
                                   m2.group(1).replace('/', '.').replace('$', '.'));
    messages.add(new GradleMessage(GradleMessage.Kind.ERROR, message, null, -1, -1));
    return true;
  }
}
