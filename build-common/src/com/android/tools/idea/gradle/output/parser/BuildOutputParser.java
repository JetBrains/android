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
package com.android.tools.idea.gradle.output.parser;

import com.android.ide.common.blame.Message;
import com.android.ide.common.blame.parser.PatternAwareOutputParser;
import com.android.ide.common.blame.parser.ToolOutputParser;
import org.jetbrains.android.sdk.MessageBuildingSdkLog;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Parses Gradle's build output and creates the messages to be displayed in the "Messages" tool window.
 */
public class BuildOutputParser{
  private final ToolOutputParser parser;

  public BuildOutputParser(@NotNull Iterable<PatternAwareOutputParser> parsers) {
    parser = new ToolOutputParser(parsers, new MessageBuildingSdkLog());
  }

  @NotNull
  public List<Message> parseGradleOutput(@NotNull String output) {
    return parser.parseToolOutput(output);
  }

  @NotNull
  public List<Message> parseGradleOutput(@NotNull String output, boolean ignoreUnrecognizedText) {
    return parser.parseToolOutput(output, ignoreUnrecognizedText);
  }
}
