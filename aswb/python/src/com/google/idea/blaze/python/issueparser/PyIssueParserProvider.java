/*
 * Copyright 2017 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.python.issueparser;

import com.google.common.collect.ImmutableList;
import com.google.idea.blaze.base.io.VfsUtils;
import com.google.idea.blaze.base.issueparser.BlazeIssueParser;
import com.google.idea.blaze.base.issueparser.BlazeIssueParser.Parser;
import com.google.idea.blaze.base.issueparser.BlazeIssueParser.SingleLineParser;
import com.google.idea.blaze.base.issueparser.BlazeIssueParserProvider;
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import com.google.idea.blaze.base.scope.output.IssueOutput;
import com.google.idea.blaze.python.PySdkUtils;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.Navigatable;
import com.intellij.pom.NavigatableAdapter;
import com.intellij.psi.PsiFile;
import com.intellij.psi.search.FilenameIndex;
import com.intellij.psi.search.GlobalSearchScope;
import com.jetbrains.python.sdk.PythonSdkUtil;
import java.io.File;
import java.util.Arrays;
import java.util.Comparator;
import java.util.regex.Matcher;
import javax.annotation.Nullable;

/** Finds python-specific errors in blaze build output. */
public class PyIssueParserProvider implements BlazeIssueParserProvider {

  @Override
  public ImmutableList<Parser> getIssueParsers(Project project) {
    return ImmutableList.of(new PyTracebackIssueParser(project));
  }

  private static class PyTracebackIssueParser extends SingleLineParser {

    final Project project;
    final WorkspaceRoot workspaceRoot;

    PyTracebackIssueParser(Project project) {
      super("File \"(.*?)\", line ([0-9]+), in (.*)");
      this.project = project;
      this.workspaceRoot = WorkspaceRoot.fromProject(project);
    }

    @Nullable
    @Override
    protected IssueOutput createIssue(Matcher matcher) {
      String fileNameOrPath = matcher.group(1);
      if (fileNameOrPath == null) {
        return null;
      }
      // don't try to find PsiFiles here, just assume it's a relative path
      File file = BlazeIssueParser.fileFromRelativePath(workspaceRoot, fileNameOrPath);
      TextRange highlightRange =
          BlazeIssueParser.union(
              BlazeIssueParser.fileHighlightRange(matcher, 1),
              BlazeIssueParser.matchedTextRange(matcher, 2, 2));
      return IssueOutput.error(matcher.group(0))
          .inFile(file)
          .onLine(parseLineNumber(matcher.group(2)))
          .navigatable(openFileNavigatable(fileNameOrPath, parseLineNumber(matcher.group(2))))
          .consoleHyperlinkRange(highlightRange)
          .build();
    }

    private Navigatable openFileNavigatable(String fileName, int line) {
      return new NavigatableAdapter() {
        @Override
        public void navigate(boolean requestFocus) {
          openFile(fileName, line, requestFocus);
        }
      };
    }

    private void openFile(String fileNameOrPath, int line, boolean requestFocus) {
      VirtualFile vf = findFile(fileNameOrPath);
      if (vf == null) {
        return;
      }
      new OpenFileDescriptor(project, vf, line - 1, -1).navigate(requestFocus);
    }

    @Nullable
    private VirtualFile findFile(String fileName) {
      // error messages can include just the file name, or the full workspace-relative path
      if (fileName.indexOf(File.separatorChar) == 0) {
        PsiFile file = findFileFromName(project, fileName);
        if (file != null) {
          return file.getVirtualFile();
        }
      }
      File file = BlazeIssueParser.fileFromRelativePath(workspaceRoot, fileName);
      return file == null ? null : VfsUtils.resolveVirtualFile(file, /* refreshIfNeeded= */ true);
    }

    @Nullable
    private static PsiFile findFileFromName(Project project, String fileName) {
      GlobalSearchScope projectScope = GlobalSearchScope.projectScope(project);
      Sdk sdk = PySdkUtils.getPythonSdk(project);
      return Arrays.stream(
              FilenameIndex.getFilesByName(project, fileName, GlobalSearchScope.allScope(project)))
          .min(Comparator.comparingInt((psi) -> rankResult(psi, projectScope, sdk)))
          .orElse(null);
    }

    /** Used to sort search results, in order: {project, library, sdk, no virtual file} */
    private static int rankResult(PsiFile file, GlobalSearchScope projectScope, Sdk sdk) {
      VirtualFile vf = file.getVirtualFile();
      if (vf == null) {
        return 3;
      }
      if (projectScope.contains(vf)) {
        return 0;
      }
      return PythonSdkUtil.isStdLib(vf, sdk) ? 2 : 1;
    }

    /** defaults to -1 if no line number can be parsed. */
    private static int parseLineNumber(@Nullable String string) {
      try {
        return string != null ? Integer.parseInt(string) : -1;
      } catch (NumberFormatException e) {
        return -1;
      }
    }
  }
}
