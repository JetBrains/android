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
package com.android.tools.idea.jps.parser;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.incremental.messages.BuildMessage.Kind;
import org.jetbrains.jps.incremental.messages.CompilerMessage;

/**
 * Creates a {@link org.jetbrains.jps.incremental.messages.CompilerMessage} from a parsed line of Gradle output.
 */
abstract class ProblemMessageFactory {
  private final LineReader myOutputReader;

  ProblemMessageFactory(@NotNull LineReader outputReader) {
    myOutputReader = outputReader;
  }

  /**
   * Creates a new {@link org.jetbrains.jps.incremental.messages.CompilerMessage} with severity 'WARNING'.
   *
   * @see #createMessage(String, org.jetbrains.jps.incremental.messages.BuildMessage.Kind, String, String)
   */
  @Nullable
  final CompilerMessage createWarningMessage(@NotNull String msgText, @Nullable String sourcePath, @Nullable String lineNumber) {
    return createMessage(msgText, Kind.WARNING, sourcePath, lineNumber);
  }

  /**
   * Creates a new {@link org.jetbrains.jps.incremental.messages.CompilerMessage} with severity 'ERROR'.
   *
   * @see #createMessage(String, org.jetbrains.jps.incremental.messages.BuildMessage.Kind, String, String)
   */
  @Nullable
  final CompilerMessage createErrorMessage(@NotNull String msgText, @Nullable String sourcePath, @Nullable String lineNumber) {
    return createMessage(msgText, Kind.ERROR, sourcePath, lineNumber);
  }

  /**
   * Creates a new {@link org.jetbrains.jps.incremental.messages.CompilerMessage} using the given information.
   *
   * @param msgText    the text of the message.
   * @param severity   the severity of the message.
   * @param sourcePath the absolute path of the file owning the message.
   * @param lineNumber the line number where the message will be.
   * @return the created {@code CompilerMessage} or {@code null} if something goes wrong.
   */
  @Nullable
  abstract CompilerMessage createMessage(@NotNull String msgText,
                                         @NotNull Kind severity,
                                         @Nullable String sourcePath,
                                         @Nullable String lineNumber);

  final LineReader getOutputReader() {
    return myOutputReader;
  }
}
