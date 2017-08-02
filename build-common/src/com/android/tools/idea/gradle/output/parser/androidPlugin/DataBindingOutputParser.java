/*
 * Copyright (C) 2015 The Android Open Source Project
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
import com.android.ide.common.blame.SourceFilePosition;
import com.android.ide.common.blame.SourcePosition;
import com.android.ide.common.blame.parser.ParsingFailedException;
import com.android.ide.common.blame.parser.PatternAwareOutputParser;
import com.android.ide.common.blame.parser.util.OutputLineReader;
import com.android.utils.ILogger;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class DataBindingOutputParser implements PatternAwareOutputParser {
  public static final String ERROR_LOG_PREFIX = "****/ data binding error ****";
  public static final String ERROR_LOG_SUFFIX = "****\\ data binding error ****";
  public static final String MSG_KEY = "msg:";
  public static final String LOCATION_KEY = "loc:";
  public static final String FILE_KEY = "file:";

  @Override
  public boolean parse(@NotNull String line, @NotNull OutputLineReader reader, @NotNull List<Message> messages, @NotNull ILogger logger)
    throws ParsingFailedException {
    int errorStart = line.indexOf(ERROR_LOG_PREFIX);
    if (errorStart >= 0) {
      logger.verbose("found data binding error start");
      int errorEnd = line.indexOf(ERROR_LOG_SUFFIX, errorStart + ERROR_LOG_PREFIX.length());
      if (errorEnd >= 0) {
        logger.verbose("found data binding error end");
        return parseErrorIn(line.substring(errorStart + ERROR_LOG_PREFIX.length(), errorEnd), messages);
      }
    }
    return false;
  }

  private static boolean parseErrorIn(@NotNull String output, @NotNull List<Message> messages) {
    String message;
    String file = "";
    List<Location> locations = new ArrayList<Location>();
    int msgStart = output.indexOf(MSG_KEY);
    if (msgStart < 0) {
      message = output;
    }
    else {
      int fileStart = output.indexOf(FILE_KEY, msgStart + MSG_KEY.length());
      if (fileStart < 0) {
        message = output;
      }
      else {
        message = output.substring(msgStart + MSG_KEY.length(), fileStart);
        int locStart = output.indexOf(LOCATION_KEY, fileStart + FILE_KEY.length());
        if (locStart < 0) {
          file = output.substring(fileStart + FILE_KEY.length()).trim();
        }
        else {
          file = output.substring(fileStart + FILE_KEY.length(), locStart).trim();
          int nextLoc = 0;
          while (nextLoc >= 0) {
            nextLoc = output.indexOf(LOCATION_KEY, locStart + LOCATION_KEY.length());
            Location loc;
            if (nextLoc < 0) {
              loc = Location.fromUserReadableString(output.substring(locStart + LOCATION_KEY.length()));
            }
            else {
              loc = Location.fromUserReadableString(output.substring(locStart + LOCATION_KEY.length(), nextLoc));
            }
            if (loc.isValid()) {
              locations.add(loc);
            }
            locStart = nextLoc;
          }
        }
      }
    }
    if (StringUtil.isEmpty(file)) {
      return false;
    }
    List<SourceFilePosition> sourceFilePositions = new ArrayList<SourceFilePosition>();
    File sourceFile = new File(file);
    if (locations.isEmpty()) {
      messages.add(new Message(Message.Kind.ERROR, message, SourceFilePosition.UNKNOWN));
    }
    else {
      for (Location location : locations) {
        sourceFilePositions.add(new SourceFilePosition(sourceFile,
                                                       new SourcePosition(location.startLine, location.startOffset, 0, location.endLine,
                                                                          location.endOffset, 0)));
      }
      SourceFilePosition first = sourceFilePositions.get(0);
      if (locations.size() == 1) {
        messages.add(new Message(Message.Kind.ERROR, message, first));
      }
      else {
        SourceFilePosition[] rest = new SourceFilePosition[sourceFilePositions.size() - 1];
        for (int i = 1; i < sourceFilePositions.size(); i++) {
          rest[i - 1] = sourceFilePositions.get(i);
        }
        messages.add(new Message(Message.Kind.ERROR, message, first, rest));
      }
    }
    return true;
  }

  private static class Location {
    public static final int NaN = -1;
    public int startLine;
    public int startOffset;
    public int endLine;
    public int endOffset;
    public Location parentLocation;

    // for XML unmarshalling
    public Location() {
      startOffset = endOffset = startLine = endLine = NaN;
    }

    public Location(@NotNull Location other) {
      startOffset = other.startOffset;
      endOffset = other.endOffset;
      startLine = other.startLine;
      endLine = other.endLine;
    }

    public Location(int startLine, int startOffset, int endLine, int endOffset) {
      this.startOffset = startOffset;
      this.startLine = startLine;
      this.endLine = endLine;
      this.endOffset = endOffset;
    }

    @Override
    public String toString() {
      return "Location{" +
             "startLine=" + startLine +
             ", startOffset=" + startOffset +
             ", endLine=" + endLine +
             ", endOffset=" + endOffset +
             ", parentLocation=" + parentLocation +
             '}';
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }

      Location location = (Location)o;

      if (endLine != location.endLine) {
        return false;
      }
      if (endOffset != location.endOffset) {
        return false;
      }
      if (startLine != location.startLine) {
        return false;
      }
      if (startOffset != location.startOffset) {
        return false;
      }
      if (parentLocation != null ? !parentLocation.equals(location.parentLocation) : location.parentLocation != null) {
        return false;
      }

      return true;
    }

    @Override
    public int hashCode() {
      int result = startLine;
      result = 31 * result + startOffset;
      result = 31 * result + endLine;
      result = 31 * result + endOffset;
      return result;
    }

    public boolean isValid() {
      return startLine != NaN && endLine != NaN && startOffset != NaN && endOffset != NaN;
    }

    public boolean contains(@NotNull Location other) {
      if (startLine > other.startLine) {
        return false;
      }
      if (startLine == other.startLine && startOffset > other.startOffset) {
        return false;
      }
      if (endLine < other.endLine) {
        return false;
      }
      if (endLine == other.endLine && endOffset < other.endOffset) {
        return false;
      }
      return true;
    }

    @Nullable
    private Location getValidParentAbsoluteLocation() {
      if (parentLocation == null) {
        return null;
      }
      if (parentLocation.isValid()) {
        return parentLocation.toAbsoluteLocation();
      }
      return parentLocation.getValidParentAbsoluteLocation();
    }

    @Nullable
    public Location toAbsoluteLocation() {
      Location absoluteParent = getValidParentAbsoluteLocation();
      if (absoluteParent == null) {
        return this;
      }
      Location copy = new Location(this);
      boolean sameLine = copy.startLine == copy.endLine;
      if (copy.startLine == 0) {
        copy.startOffset += absoluteParent.startOffset;
      }
      if (sameLine) {
        copy.endOffset += absoluteParent.startOffset;
      }

      copy.startLine += absoluteParent.startLine;
      copy.endLine += absoluteParent.startLine;
      return copy;
    }

    @NotNull
    public static Location fromUserReadableString(@NotNull String str) {
      int glue = str.indexOf('-');
      if (glue == -1) {
        return new Location();
      }
      String start = str.substring(0, glue);
      String end = str.substring(glue + 1);
      int[] point = new int[]{-1, -1};
      Location location = new Location();
      parsePoint(start, point);
      location.startLine = point[0];
      location.startOffset = point[1];
      point[0] = point[1] = -1;
      parsePoint(end, point);
      location.endLine = point[0];
      location.endOffset = point[1];
      return location;
    }

    private static boolean parsePoint(@NotNull String content, int[] into) {
      int index = content.indexOf(':');
      if (index == -1) {
        return false;
      }
      into[0] = Integer.parseInt(content.substring(0, index).trim());
      into[1] = Integer.parseInt(content.substring(index + 1).trim());
      return true;
    }
  }

}
