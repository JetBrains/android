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
package com.android.tools.idea.gradle.output.parser.javac;

import com.android.SdkConstants;
import com.android.ide.common.blame.Message;
import com.android.ide.common.blame.SourceFilePosition;
import com.android.ide.common.blame.SourcePosition;
import com.android.ide.common.blame.parser.ParsingFailedException;
import com.android.ide.common.blame.parser.PatternAwareOutputParser;
import com.android.ide.common.blame.parser.util.OutputLineReader;
import com.android.utils.ILogger;
import com.intellij.openapi.util.text.StringUtil;
import java.util.ArrayList;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.List;

/**
 * Parses javac's output.
 */
public class JavacOutputParser implements PatternAwareOutputParser {
  private static final char COLON = ':';

  private static final String WARNING_PREFIX = "warning:"; // default value

  @Override
  public boolean parse(@NotNull String line, @NotNull OutputLineReader reader, @NotNull List<Message> messages, @NotNull ILogger logger)
    throws ParsingFailedException {
    int colonIndex1 = line.indexOf(COLON);
    if (colonIndex1 == 1) { // drive letter (Windows)
      colonIndex1 = line.indexOf(COLON, colonIndex1 + 1);
    }

    if (colonIndex1 >= 0) { // looks like found something like a file path.
      String part1 = line.substring(0, colonIndex1).trim();
      if (part1.equalsIgnoreCase("error") /* jikes */ || part1.equalsIgnoreCase("Caused by")) {
        // +1 so we don't include the colon
        String text = line.substring(colonIndex1 + 1).trim();
        addMessage(new Message(Message.Kind.ERROR, text, SourceFilePosition.UNKNOWN), messages);
        return true;
      }
      if (part1.equalsIgnoreCase("warning")) {
        // +1 so we don't include the colon
        String text = line.substring(colonIndex1 + 1).trim();
        addMessage(new Message(Message.Kind.WARNING, text, SourceFilePosition.UNKNOWN), messages);
        return true;
      }
      if (part1.equalsIgnoreCase("javac")) {
        addMessage(new Message(Message.Kind.ERROR, line, SourceFilePosition.UNKNOWN), messages);
        return true;
      }

      int colonIndex2 = line.indexOf(COLON, colonIndex1 + 1);
      if (colonIndex2 >= 0) {
        File file = new File(part1);
        if (!file.isFile()) {
          // the part one is not a file path.
          return false;
        }
        try {
          int lineNumber = Integer.parseInt(line.substring(colonIndex1 + 1, colonIndex2).trim()); // 1-based.
          String text = line.substring(colonIndex2 + 1).trim();
          Message.Kind kind = Message.Kind.ERROR;

          if (text.startsWith(WARNING_PREFIX)) {
            text = text.substring(WARNING_PREFIX.length()).trim();
            kind = Message.Kind.WARNING;
          }

          // Only slurp up line pointer (^) information if this is really javac
          if (!file.getPath().endsWith(SdkConstants.DOT_JAVA)) {
            // Fall back to the MergingExceptionParser (which handles similar messages in a more general way)
            return false;
          }

          List<String> messageList = new ArrayList<>();
          messageList.add(text);
          int column; // 0-based.
          String prevLine = null;
          do {
            String nextLine = reader.readLine();
            if (nextLine == null) {
              return false;
            }
            if (nextLine.trim().equals("^")) {
              column = nextLine.indexOf('^');
              String messageEnd = reader.readLine();

              while (isMessageEnd(messageEnd)) {
                messageList.add(messageEnd.trim());
                messageEnd = reader.readLine();
              }

              if (messageEnd != null) {
                reader.pushBack();
              }
              break;
            }
            if (prevLine != null) {
              messageList.add(prevLine);
            }
            prevLine = nextLine;
          } while (true);

          if (column >= 0) {
            messageList = convertMessages(messageList);
            String msgText = StringUtil.join(messageList, System.lineSeparator());
            Message msg = new Message(kind, msgText, new SourceFilePosition(file, new SourcePosition(lineNumber - 1, column, -1)));
            addMessage(msg, messages);
            return true;
          }

        } catch (NumberFormatException ignored) {
        }
      }
    }

    if (line.endsWith("java.lang.OutOfMemoryError")) {
      addMessage(new Message(Message.Kind.ERROR, "Out of memory.", SourceFilePosition.UNKNOWN), messages);
      return true;
    }

    return false;
  }

  private static void addMessage(@NotNull Message message, @NotNull List<Message> messages) {
    boolean duplicatesPrevious = false;
    int messageCount = messages.size();
    if (messageCount > 0) {
      Message lastMessage = messages.get(messageCount - 1);
      duplicatesPrevious = lastMessage.equals(message);
    }
    if (!duplicatesPrevious) {
      messages.add(message);
    }
  }

  @Contract("null -> false")
  private static boolean isMessageEnd(@Nullable String line) {
    return line != null && !line.isEmpty() && Character.isWhitespace(line.charAt(0));
  }

  @NotNull
  private static List<String> convertMessages(@NotNull List<String> messages) {
    if(messages.size() <= 1) {
      return messages;
    }
    final String line0 = messages.get(0);
    final String line1 = messages.get(1);
    final int colonIndex = line1.indexOf(':');
    if (colonIndex > 0){
      @NonNls String part1 = line1.substring(0, colonIndex).trim();
      // jikes
      if ("symbol".equals(part1)){
        String symbol = line1.substring(colonIndex + 1).trim();
        messages.remove(1);
        if(messages.size() >= 2) {
          messages.remove(1);
        }
        messages.set(0, line0 + " " + symbol);
      }
    }
    return messages;
  }
}
