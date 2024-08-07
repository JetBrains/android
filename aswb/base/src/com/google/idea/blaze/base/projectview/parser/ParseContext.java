/*
 * Copyright 2016 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.idea.blaze.base.projectview.parser;

import com.google.common.base.Splitter;
import com.google.common.collect.Lists;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.base.scope.output.IssueOutput;
import com.google.idea.blaze.base.sync.workspace.WorkspacePathResolver;
import com.google.idea.blaze.base.ui.BlazeValidationError;
import java.io.File;
import java.util.List;
import javax.annotation.Nullable;

/** Context for the project view parser. */
public class ParseContext {
  private final BlazeContext context;
  private final WorkspacePathResolver workspacePathResolver;
  @Nullable private final File file;
  private final List<String> lines;

  @Nullable private Line currentLine;
  @Nullable private String currentRawLine;
  private int currentLineIndex;
  private int savedPosition = -1;

  /** A line that is being parsed */
  public static class Line {
    public final String text;
    public final int indent;

    public Line(String text, int indent) {
      this.text = text;
      this.indent = indent;
    }
  }

  public ParseContext(
      BlazeContext context,
      WorkspacePathResolver workspacePathResolver,
      @Nullable File file,
      String text) {
    this.context = context;
    this.workspacePathResolver = workspacePathResolver;
    this.file = file;
    this.lines = Lists.newArrayList(Splitter.on('\n').split(text));
    this.currentLine = null;
    this.currentLineIndex = -1;
    consume();
  }

  public Line current() {
    assert currentLine != null;
    return currentLine;
  }

  public String currentRawLine() {
    assert currentRawLine != null;
    return currentRawLine;
  }

  public void consume() {
    while (++currentLineIndex < lines.size()) {
      update();
      break;
    }
  }

  private void update() {
    String line = lines.get(currentLineIndex);
    int indent = 0;
    while (indent < line.length() && line.charAt(indent) == ' ') {
      ++indent;
    }
    currentLine = new Line(line.trim(), indent);
    currentRawLine = line;
  }

  public boolean atEnd() {
    return currentLineIndex >= lines.size();
  }

  public BlazeContext getContext() {
    return context;
  }

  public WorkspacePathResolver getWorkspacePathResolver() {
    return workspacePathResolver;
  }

  public void savePosition() {
    savedPosition = currentLineIndex;
  }

  public void clearSavedPosition() {
    savedPosition = -1;
  }

  public void resetToSavedPosition() {
    if (savedPosition != -1) {
      currentLineIndex = savedPosition;
      savedPosition = -1;
      update();
    }
  }

  @Nullable
  public File getProjectViewFile() {
    return file;
  }

  public void addErrors(List<BlazeValidationError> errors) {
    for (BlazeValidationError error : errors) {
      addError(error.getError());
    }
  }

  public void addError(String error) {
    IssueOutput.error(error).inFile(file).onLine(currentLineIndex + 1).submit(context);
  }

  public void addWarning(String error) {
    IssueOutput.warn(error).inFile(file).onLine(currentLineIndex + 1).submit(context);
  }
}
