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
package com.google.idea.blaze.base.vcs.git;

import com.google.common.collect.Lists;
import com.google.idea.blaze.base.async.process.LineProcessingOutputStream;
import com.google.idea.blaze.base.model.primitives.WorkspacePath;
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import com.intellij.openapi.util.text.StringUtil;
import java.io.File;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.annotation.Nullable;

/** Finds file modifications from git status output */
public class GitStatusLineProcessor implements LineProcessingOutputStream.LineProcessor {

  private static final Pattern REGEX = Pattern.compile("^(A|M|D)\\s*(.*?)$");

  private final WorkspaceRoot workspaceRoot;
  private final String gitRoot;

  public final List<WorkspacePath> addedFiles = Lists.newArrayList();
  public final List<WorkspacePath> modifiedFiles = Lists.newArrayList();
  public final List<WorkspacePath> deletedFiles = Lists.newArrayList();

  public GitStatusLineProcessor(WorkspaceRoot workspaceRoot, String gitRoot) {
    this.workspaceRoot = workspaceRoot;
    this.gitRoot = gitRoot;
  }

  @Override
  public boolean processLine(String line) {
    Matcher matcher = REGEX.matcher(line);
    if (matcher.find()) {
      String type = matcher.group(1);
      String file = matcher.group(2);
      file = StringUtil.trimEnd(file, '/');

      WorkspacePath workspacePath = getWorkspacePath(file);
      if (workspacePath == null) {
        return true;
      }
      switch (type) {
        case "A":
          addedFiles.add(workspacePath);
          break;
        case "M":
          modifiedFiles.add(workspacePath);
          break;
        case "D":
          deletedFiles.add(workspacePath);
          break;
      }
    }
    return true;
  }

  @Nullable
  private WorkspacePath getWorkspacePath(String gitPath) {
    File absoluteFile = new File(gitRoot, gitPath);
    return workspaceRoot.workspacePathForSafe(absoluteFile);
  }
}
