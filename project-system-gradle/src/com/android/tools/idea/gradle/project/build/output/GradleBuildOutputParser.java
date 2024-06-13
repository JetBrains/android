/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.gradle.project.build.output;

import static com.android.ide.common.blame.MessageJsonSerializer.STDOUT_ERROR_TAG;
import static com.android.tools.idea.gradle.project.build.output.AndroidGradlePluginOutputParser.ANDROID_GRADLE_PLUGIN_MESSAGES_GROUP;
import static com.android.tools.idea.gradle.project.build.output.BuildOutputParserUtils.BUILD_FAILED_WITH_EXCEPTION_LINE;
import static com.android.tools.idea.gradle.project.build.output.BuildOutputParserUtils.MESSAGE_GROUP_ERROR_SUFFIX;
import static com.android.tools.idea.gradle.project.build.output.BuildOutputParserUtils.MESSAGE_GROUP_INFO_SUFFIX;
import static com.android.tools.idea.gradle.project.build.output.BuildOutputParserUtils.MESSAGE_GROUP_STATISTICS_SUFFIX;
import static com.android.tools.idea.gradle.project.build.output.BuildOutputParserUtils.MESSAGE_GROUP_WARNING_SUFFIX;

import com.android.ide.common.blame.Message;
import com.android.ide.common.blame.Message.Kind;
import com.android.ide.common.blame.MessageJsonSerializer;
import com.android.ide.common.blame.SourceFilePosition;
import com.android.ide.common.blame.SourcePosition;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import com.intellij.build.FilePosition;
import com.intellij.build.events.BuildEvent;
import com.intellij.build.events.MessageEvent;
import com.intellij.build.events.impl.FileMessageEventImpl;
import com.intellij.build.events.impl.MessageEventImpl;
import com.intellij.build.output.BuildOutputInstantReader;
import com.intellij.build.output.BuildOutputParser;
import java.io.File;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Parser got errors returned by the Android Gradle Plugin in AGPBI json format.
 */
public class GradleBuildOutputParser implements BuildOutputParser {
  @NotNull private static final String DEFAULT_MESSAGE_GROUP = ANDROID_GRADLE_PLUGIN_MESSAGES_GROUP + MESSAGE_GROUP_WARNING_SUFFIX;

  /**
   * Contains the future gradle plugin output extracted from the json object per build Id, the json string is outputted before those output
   * lines. Those lines should be consumed and ignored so no other parsers would consume them.
   */
  @NotNull private final Map<Object, Set<String>> futureOutputMap = new HashMap<>();

  /**
   * Contains buildIds which contained an error parsed by this parser.
   */
  @NotNull private final Set<Object> buildIdsWithAGPErrors = new HashSet<>();

  @NotNull private final Gson myGson;

  public GradleBuildOutputParser() {
    // Since the error message can contain characters like <, > and ' we need to disable html escaping so the error message will be the same
    // after serializing and deserializing.
    GsonBuilder gsonBuilder = new GsonBuilder().disableHtmlEscaping();
    MessageJsonSerializer.registerTypeAdapters(gsonBuilder);
    myGson = gsonBuilder.create();
  }

  @Override
  public boolean parse(@NotNull String line,
                       @NotNull BuildOutputInstantReader reader,
                       @NotNull Consumer<? super BuildEvent> messageConsumer) {
    String currentLine = line.trim();
    if (currentLine.startsWith(STDOUT_ERROR_TAG)) {
      processMessage(currentLine, reader.getParentEventId(), messageConsumer);
      return true;
    }

    // consume the build failed message if there were some errors parsed before, this makes sure that GradleBuildScriptErrorParser will not
    // re-parse and duplicate the errors
    if (currentLine.startsWith(BUILD_FAILED_WITH_EXCEPTION_LINE) && buildIdsWithAGPErrors.contains(reader.getParentEventId())) {
      BuildOutputParserUtils.consumeRestOfOutput(reader);
      return true;
    }

    // consume the line without producing a message, and remove it from the map
    if (futureOutputMap.getOrDefault(reader.getParentEventId(), Collections.emptySet()).contains(currentLine)) {
      futureOutputMap.get(reader.getParentEventId()).remove(currentLine);
      return true;
    }

    return false;
  }

  private void processMessage(String line, Object buildId, @NotNull Consumer<? super MessageEvent> messageConsumer) {
    String jsonString = line.substring(STDOUT_ERROR_TAG.length()).trim();
    if (jsonString.isEmpty()) {
      return;
    }
    try {
      Message msg = myGson.fromJson(jsonString, Message.class);

      Set<String> futureOutput = futureOutputMap.computeIfAbsent(buildId, k -> new HashSet<>());
      if (msg.getKind() == Kind.ERROR) {
        buildIdsWithAGPErrors.add(buildId);
      }
      futureOutput.addAll(Arrays.asList(msg.getRawMessage().split("\\n")));
      String message = msg.getText().lines().findFirst().orElse(msg.getText());
      String detailedMessage = msg.getRawMessage().isEmpty() ? msg.getText() : msg.getRawMessage();
      boolean validPosition = false;
      for (SourceFilePosition sourceFilePosition : msg.getSourceFilePositions()) {
        FilePosition filePosition = convertToFilePosition(sourceFilePosition);
        if (filePosition != null) {
          validPosition = true;
          messageConsumer.accept(
            new FileMessageEventImpl(buildId, convertKind(msg.getKind()), getMessageGroup(msg), message, detailedMessage, filePosition)
          );
        }
      }
      if (!validPosition) {
        messageConsumer.accept(
          new MessageEventImpl(buildId, convertKind(msg.getKind()), getMessageGroup(msg), message, detailedMessage)
        );
      }
    }
    catch (JsonParseException ignored) {
      messageConsumer.accept(new MessageEventImpl(buildId, MessageEvent.Kind.WARNING, getMessageGroup(null), line, ""));
    }
  }

  @NotNull
  private static String getMessageGroup(@Nullable Message msg) {
    if (msg == null) {
      return DEFAULT_MESSAGE_GROUP;
    }
    String messageGroup = msg.getToolName() == null ? ANDROID_GRADLE_PLUGIN_MESSAGES_GROUP : msg.getToolName();
    switch (msg.getKind()) {
      case WARNING:
        return messageGroup + MESSAGE_GROUP_WARNING_SUFFIX;
      case STATISTICS:
        return messageGroup + MESSAGE_GROUP_STATISTICS_SUFFIX;
      case SIMPLE:
      case INFO:
        return messageGroup + MESSAGE_GROUP_INFO_SUFFIX;
      case ERROR:
      case UNKNOWN:
      default:
        return messageGroup + MESSAGE_GROUP_ERROR_SUFFIX;
    }
  }

  /**
   * Convert from {@link Message.Kind} to {@link MessageEvent.Kind}
   * @param kind a value from {@link Message.Kind}.
   * @return the equivalent {@link MessageEvent.Kind} or {@link MessageEvent.Kind#ERROR} if no correspondence exists.
   */
  @Contract(pure = true)
  @NotNull
  private static MessageEvent.Kind convertKind(@NotNull Kind kind) {
    switch (kind) {
      case WARNING:
        return MessageEvent.Kind.WARNING;
      case INFO:
        return MessageEvent.Kind.INFO;
      case STATISTICS:
        return MessageEvent.Kind.STATISTICS;
      case SIMPLE:
        return MessageEvent.Kind.SIMPLE;
      case ERROR:
      case UNKNOWN:
      default:
        return MessageEvent.Kind.ERROR;
    }
  }

  /**
   * Convert from {@link SourceFilePosition} to {@link FilePosition}
   * @param sourceFilePosition
   * @return converted FilePosition or null if the position is empty
   */
  @Nullable
  private static FilePosition convertToFilePosition(@NotNull SourceFilePosition sourceFilePosition) {
    File sourceFile = sourceFilePosition.getFile().getSourceFile();
    if (sourceFile == null) {
      return null;
    }
    SourcePosition position = sourceFilePosition.getPosition();
    int startLine = position.getStartLine();
    int endLine = position.getEndLine();
    int startColumn = position.getStartColumn();
    int endColumn = position.getEndColumn();
    return new FilePosition(sourceFile, startLine, startColumn, endLine, endColumn);
  }
}
