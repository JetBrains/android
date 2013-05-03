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
package com.android.tools.idea.jps.output.parser;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.incremental.messages.CompilerMessage;

import java.util.Collection;

/**
 * Parses the output of a compiler.
 */
public interface CompilerOutputParser {
  /**
   * Parses the given output line.
   *
   * @param line     the line to parse.
   * @param reader   passed in case this parser needs to parse more lines in order to create a {@code CompilerMessage}.
   * @param messages stores the compiler messages created during parsing, if any.
   * @return indicates whether this parser was able to parser the given line.
   * @throws ParsingFailedException if something goes wrong (e.g. malformed output.)
   */
  boolean parse(@NotNull String line, @NotNull OutputLineReader reader, @NotNull Collection<CompilerMessage> messages)
    throws ParsingFailedException;
}
