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
package com.google.idea.blaze.base.issueparser;

import static com.google.common.base.Preconditions.checkState;
import static com.google.idea.blaze.base.scope.output.IssueOutput.Category.ERROR;
import static com.google.idea.blaze.base.scope.output.IssueOutput.Category.NOTE;
import static com.google.idea.blaze.base.scope.output.IssueOutput.Category.WARNING;

import com.google.common.base.Ascii;
import com.google.common.collect.ImmutableList;
import com.google.idea.blaze.base.command.BlazeInvocationContext;
import com.google.idea.blaze.base.model.primitives.Label;
import com.google.idea.blaze.base.model.primitives.TargetExpression;
import com.google.idea.blaze.base.model.primitives.WorkspacePath;
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import com.google.idea.blaze.base.projectview.ProjectViewManager;
import com.google.idea.blaze.base.projectview.ProjectViewSet;
import com.google.idea.blaze.base.projectview.section.Section;
import com.google.idea.blaze.base.projectview.section.SectionKey;
import com.google.idea.blaze.base.projectview.section.sections.TargetSection;
import com.google.idea.blaze.base.run.filter.FileResolver;
import com.google.idea.blaze.base.scope.output.IssueOutput;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.annotation.Nullable;

/** Parses blaze output for compile errors. */
public class BlazeIssueParser {

  public static ImmutableList<BlazeIssueParser.Parser> defaultIssueParsers(
      Project project,
      WorkspaceRoot workspaceRoot,
      BlazeInvocationContext.ContextType invocationContext) {
    ProjectViewSet projectViewSet = ProjectViewManager.getInstance(project).getProjectViewSet();
    if (projectViewSet == null) {
      // some parsers will work regardless, but don't even bother splitting them if there's no
      // project view available
      return ImmutableList.of();
    }

    ImmutableList.Builder<BlazeIssueParser.Parser> parsers =
        ImmutableList.<BlazeIssueParser.Parser>builder()
            .add(
                new BlazeIssueParser.PythonCompileParser(project),
                new BlazeIssueParser.DefaultCompileParser(project),
                new BlazeIssueParser.TracebackParser(),
                new BlazeIssueParser.BuildParser(),
                new BlazeIssueParser.SkylarkErrorParser(),
                new BlazeIssueParser.LinelessBuildParser(),
                new BlazeIssueParser.ProjectViewLabelParser(projectViewSet),
                new BlazeIssueParser.InvalidTargetProjectViewPackageParser(
                    projectViewSet, "no such package '(.*)': BUILD file not found on package path"),
                new BlazeIssueParser.InvalidTargetProjectViewPackageParser(
                    projectViewSet, "no targets found beneath '(.*?)'"),
                new BlazeIssueParser.InvalidTargetProjectViewPackageParser(
                    projectViewSet, "ERROR: invalid target format '(.*?)'"),
                new BlazeIssueParser.InvalidTargetProjectViewPackageParser(
                    projectViewSet, "ERROR: Skipping '(.*?)'"),
                new BlazeIssueParser.FileNotFoundBuildParser(workspaceRoot))
            .addAll(BlazeIssueParserProvider.getAllIssueParsers(project));
    if (invocationContext == BlazeInvocationContext.ContextType.Sync) {
      parsers.add(BlazeIssueParser.GenericErrorParser.INSTANCE);
    }
    return parsers.build();
  }

  /** Result from parsing the current line */
  public static class ParseResult {

    public static final ParseResult NEEDS_MORE_INPUT = new ParseResult(true, null);

    public static final ParseResult NO_RESULT = new ParseResult(false, null);

    private final boolean needsMoreInput;
    @Nullable private final IssueOutput output;

    private ParseResult(boolean needsMoreInput, @Nullable IssueOutput output) {
      this.needsMoreInput = needsMoreInput;
      this.output = output;
    }

    public static ParseResult output(IssueOutput output) {
      return new ParseResult(false, output);
    }
  }

  /** Used by BlazeIssueParser. Generally implemented by subclassing SingleLineParser */
  public interface Parser {
    ParseResult parse(String currentLine, List<String> previousLines);
  }

  /** Base for a Parser that consumes a single contextless line at a time, matched via regex */
  public abstract static class SingleLineParser implements Parser {
    final Pattern pattern;

    public SingleLineParser(String regex) {
      pattern = Pattern.compile(regex);
    }

    @Override
    public final ParseResult parse(String currentLine, List<String> multilineMatchResult) {
      checkState(
          multilineMatchResult.isEmpty(), "SingleLineParser recieved multiple lines of input");
      return parse(currentLine);
    }

    ParseResult parse(String line) {
      Matcher matcher = pattern.matcher(line);
      if (matcher.find()) {
        IssueOutput issue = createIssue(matcher);
        return issue != null ? ParseResult.output(issue) : ParseResult.NO_RESULT;
      }
      return ParseResult.NO_RESULT;
    }

    @Nullable
    protected abstract IssueOutput createIssue(Matcher matcher);
  }

  public static File fileFromAbsolutePath(String absolutePath) {
    return new File(absolutePath);
  }

  @Nullable
  public static File fileFromRelativePath(WorkspaceRoot workspaceRoot, String relativePath) {
    try {
      final WorkspacePath workspacePath = new WorkspacePath(relativePath);
      return workspaceRoot.fileForPath(workspacePath);
    } catch (IllegalArgumentException e) {
      // Ignore -- malformed error message
      return null;
    }
  }

  /** Returns the file referenced by the target */
  @Nullable
  private static File fileFromTarget(WorkspaceRoot workspaceRoot, String targetString) {
    Label label = Label.createIfValid(targetString);
    if (label == null || label.isExternal()) {
      return null;
    }
    try {
      final WorkspacePath combined =
          new WorkspacePath(label.blazePackage(), label.targetName().toString());
      return workspaceRoot.fileForPath(combined);
    } catch (IllegalArgumentException e) {
      return null;
    }
  }

  /** Falls back to returning -1 if no integer can be parsed. */
  public static int parseOptionalInt(@Nullable String intString) {
    if (intString == null) {
      return -1;
    }
    try {
      return Integer.parseInt(intString);
    } catch (NumberFormatException e) {
      return -1;
    }
  }

  static class PythonCompileParser extends SingleLineParser {
    private final Project project;

    PythonCompileParser(Project project) {
      super(
          "^File \"([^:]*\\.py)\", " // file path
              + "line ([0-9]+), " // line number
              + "(.*)$"); // message
      this.project = project;
    }

    @Override
    protected IssueOutput createIssue(Matcher matcher) {
      final File file = FileResolver.resolveToFile(project, matcher.group(1));
      return IssueOutput.issue(ERROR, matcher.group(3))
          .inFile(file)
          .onLine(Integer.parseInt(matcher.group(2)))
          .consoleHyperlinkRange(
              union(fileHighlightRange(matcher, 1), matchedTextRange(matcher, 0, 2)))
          .build();
    }
  }

  static class DefaultCompileParser extends SingleLineParser {
    private final Project project;

    DefaultCompileParser(Project project) {
      super(
          "^" // start
              + "([^:]+)" // file path
              + ":([0-9]+)" // line number
              + "(?::([0-9]+))?" // optional column number
              + "(?::| -) " // colon or hyphen separator
              + "(?i:" // optional case insensitive message type
              + "(fatal error|error|warning|note|internal problem|context|info)"
              + "(?::| -)? " // optional colon or hyphen separator
              + ")?"
              + "(.*)$"); // message
      this.project = project;
    }

    @Override
    protected IssueOutput createIssue(Matcher matcher) {
      final File file = FileResolver.resolveToFile(project, matcher.group(1));
      IssueOutput.Category category = messageCategory(matcher.group(4));
      return IssueOutput.issue(category, matcher.group(5))
          .inFile(file)
          .onLine(Integer.parseInt(matcher.group(2)))
          .inColumn(parseOptionalInt(matcher.group(3)))
          .consoleHyperlinkRange(
              union(fileHighlightRange(matcher, 1), matchedTextRange(matcher, 2, 3)))
          .build();
    }

    private static IssueOutput.Category messageCategory(@Nullable String messageType) {
      if (messageType == null) {
        return ERROR;
      }
      switch (Ascii.toLowerCase(messageType)) {
        case "warning":
          return WARNING;
        case "note":
        case "message":
        case "context":
        case "info":
          return NOTE;
        case "error":
        case "fatal error":
        case "internal problem":
          return ERROR;
        default: // fall out
      }
      return ERROR;
    }
  }

  static class TracebackParser implements Parser {
    private static final Pattern PATTERN =
        Pattern.compile(
            "(ERROR): (.*?):([0-9]+):([0-9]+): (Traceback \\(most recent call last\\):)");

    @Override
    public ParseResult parse(String currentLine, List<String> previousLines) {
      if (previousLines.isEmpty()) {
        if (PATTERN.matcher(currentLine).find()) {
          return ParseResult.NEEDS_MORE_INPUT;
        } else {
          return ParseResult.NO_RESULT;
        }
      }

      if (currentLine.startsWith("\t")) {
        return ParseResult.NEEDS_MORE_INPUT;
      } else {
        Matcher matcher = PATTERN.matcher(previousLines.get(0));
        checkState(
            matcher.find(), "Found a match in the first line previously, but now it isn't there.");
        StringBuilder message = new StringBuilder(matcher.group(5));
        for (int i = 1; i < previousLines.size(); ++i) {
          message.append(System.lineSeparator()).append(previousLines.get(i));
        }
        message.append(System.lineSeparator()).append(currentLine);
        return ParseResult.output(
            IssueOutput.error(message.toString())
                .inFile(new File(matcher.group(2)))
                .onLine(Integer.parseInt(matcher.group(3)))
                .inColumn(parseOptionalInt(matcher.group(4)))
                .build());
      }
    }
  }

  static class BuildParser extends SingleLineParser {
    BuildParser() {
      super("^ERROR: (/.*?BUILD):([0-9]+):([0-9]+): (.*)$");
    }

    @Nullable
    @Override
    protected IssueOutput createIssue(Matcher matcher) {
      if (matcher.group(4).startsWith("Couldn't build file ")
          && !matcher.group(4).contains("Executing genrule")) {
        // This is usually accompanied by a more useful error from the compiler.
        return null;
      }
      File file = fileFromAbsolutePath(matcher.group(1));
      return IssueOutput.error(matcher.group(4))
          .inFile(file)
          .onLine(Integer.parseInt(matcher.group(2)))
          .inColumn(parseOptionalInt(matcher.group(3)))
          .consoleHyperlinkRange(
              union(fileHighlightRange(matcher, 1), matchedTextRange(matcher, 2, 3)))
          .build();
    }
  }

  static class SkylarkErrorParser extends SingleLineParser {
    SkylarkErrorParser() {
      super("^ERROR: (/.*?\\.bzl):([0-9]+):([0-9]+): (.*)$");
    }

    @Override
    protected IssueOutput createIssue(Matcher matcher) {
      File file = fileFromAbsolutePath(matcher.group(1));
      return IssueOutput.error(matcher.group(4))
          .inFile(file)
          .onLine(Integer.parseInt(matcher.group(2)))
          .inColumn(parseOptionalInt(matcher.group(3)))
          .consoleHyperlinkRange(
              union(fileHighlightRange(matcher, 1), matchedTextRange(matcher, 2, 3)))
          .build();
    }
  }

  static class LinelessBuildParser extends SingleLineParser {
    LinelessBuildParser() {
      super("^ERROR: (.*?):char offsets [0-9]+--[0-9]+: (.*)$");
    }

    @Override
    protected IssueOutput createIssue(Matcher matcher) {
      return IssueOutput.error(matcher.group(2))
          .inFile(new File(matcher.group(1)))
          .consoleHyperlinkRange(fileHighlightRange(matcher, 1))
          .build();
    }
  }

  static class FileNotFoundBuildParser extends SingleLineParser {
    private final WorkspaceRoot workspaceRoot;

    FileNotFoundBuildParser(WorkspaceRoot workspaceRoot) {
      super("^ERROR: .*? Unable to load file '(.*?)': (.*)$");
      this.workspaceRoot = workspaceRoot;
    }

    @Override
    protected IssueOutput createIssue(Matcher matcher) {
      File file = fileFromTarget(workspaceRoot, matcher.group(1));
      return IssueOutput.error(matcher.group(2))
          .inFile(file)
          .consoleHyperlinkRange(fileHighlightRange(matcher, 1))
          .build();
    }
  }

  static class ProjectViewLabelParser extends SingleLineParser {

    @Nullable private final ProjectViewSet projectViewSet;

    ProjectViewLabelParser(@Nullable ProjectViewSet projectViewSet) {
      super("no such target '(.*)': target .*? not declared in package .*? defined by");
      this.projectViewSet = projectViewSet;
    }

    @Override
    protected IssueOutput createIssue(Matcher matcher) {
      File file = null;
      if (projectViewSet != null) {
        String targetString = matcher.group(1);
        final TargetExpression target = TargetExpression.fromStringSafe(targetString);
        if (target != null) {
          file =
              projectViewFileWithSection(
                  projectViewSet,
                  TargetSection.KEY,
                  targetSection -> targetSection.items().contains(target));
        }
      }

      return IssueOutput.error(matcher.group(0))
          .inFile(file)
          .consoleHyperlinkRange(fileHighlightRange(matcher, 1))
          .build();
    }
  }

  static class InvalidTargetProjectViewPackageParser extends SingleLineParser {
    private final ProjectViewSet projectViewSet;

    InvalidTargetProjectViewPackageParser(ProjectViewSet projectViewSet, String regex) {
      super(regex);
      this.projectViewSet = projectViewSet;
    }

    @Override
    protected IssueOutput createIssue(Matcher matcher) {
      final String packageString = matcher.group(1);
      File file =
          projectViewFileWithSection(
              projectViewSet,
              TargetSection.KEY,
              targetSection -> {
                for (TargetExpression targetExpression : targetSection.items()) {
                  if (targetExpression.toString().contains(packageString)) {
                    return true;
                  }
                }
                return false;
              });

      return IssueOutput.error(matcher.group(0)).inFile(file).build();
    }
  }

  /**
   * Fallback parser, intended to be run last to catch any errors not specifically handled by other
   * parsers. Avoids parsing build/test failure notifications.
   */
  static class GenericErrorParser extends SingleLineParser {
    static final GenericErrorParser INSTANCE = new GenericErrorParser();

    // Match either specific blacklisted patterns we don't want, or the generic error message we do.
    // Then throw away the blacklisted matches later.
    private static final String PATTERN =
        "^ERROR: (?:"
            + "(//.+?: Exit [0-9]+\\.)|"
            + "(.*: Process exited with status [0-9]+\\.)|"
            + "(build interrupted\\.)|"
            + "(Couldn't start the build. Unable to run tests.)|"
            + "(/.*?BUILD:[0-9]+:[0-9]+: Couldn't build file .*)|"
            + "(.*))$";

    private GenericErrorParser() {
      super(PATTERN);
    }

    @Nullable
    @Override
    protected IssueOutput createIssue(Matcher matcher) {
      int lastGroup = matcher.groupCount();
      for (int i = 1; i < lastGroup; ++i) {
        if (matcher.group(i) != null) {
          return null;
        }
      }
      return IssueOutput.error(matcher.group(lastGroup)).build();
    }
  }

  @Nullable
  private static <T, SectionType extends Section<T>> File projectViewFileWithSection(
      ProjectViewSet projectViewSet,
      SectionKey<T, SectionType> key,
      Predicate<SectionType> predicate) {
    for (ProjectViewSet.ProjectViewFile projectViewFile : projectViewSet.getProjectViewFiles()) {
      ImmutableList<SectionType> sections = projectViewFile.projectView.getSectionsOfType(key);
      for (SectionType section : sections) {
        if (predicate.test(section)) {
          return projectViewFile.projectViewFile;
        }
      }
    }
    return null;
  }

  private final ImmutableList<Parser> parsers;
  /**
   * The parser that requested more lines of input during the last call to {@link
   * #parseIssue(String)}.
   */
  @Nullable private Parser multilineMatchingParser;

  private List<String> multilineMatchResult = new ArrayList<>();

  public BlazeIssueParser(ImmutableList<Parser> parsers) {
    this.parsers = parsers;
  }

  @Nullable
  public IssueOutput parseIssue(String line) {
    if (multilineMatchingParser != null) {
      ParseResult issue = multilineMatchingParser.parse(line, multilineMatchResult);
      if (issue.needsMoreInput) {
        multilineMatchResult.add(line);
        return null;
      }
      multilineMatchingParser = null;
      multilineMatchResult = new ArrayList<>();
      if (issue.output != null) {
        return issue.output;
      }
      // multi line match failed, continue with other parsers
    }

    for (Parser parser : parsers) {
      ParseResult issue = parser.parse(line, ImmutableList.of());
      if (issue.needsMoreInput) {
        multilineMatchingParser = parser;
        multilineMatchResult.add(line);
        return null;
      }
      if (issue.output != null) {
        return issue.output;
      }
    }

    return null;
  }

  /**
   * The union of the two ranges. If one of the ranges is null, returns the other. If both are null,
   * returns null.
   */
  @Nullable
  public static TextRange union(@Nullable TextRange range1, @Nullable TextRange range2) {
    if (range1 == null) {
      return range2;
    }
    if (range2 == null) {
      return range1;
    }
    return range1.union(range2);
  }

  /** The range of a filename to highlight. Links the full file path range. */
  @Nullable
  public static TextRange fileHighlightRange(Matcher matcher, int capturingGroup) {
    int start = matcher.start(capturingGroup);
    int end = matcher.end(capturingGroup);
    if (start == -1 || start >= end) {
      return null;
    }
    return TextRange.create(start, end);
  }

  /**
   * The match range of the given capturing groups. If either group doesn't match anything, will
   * attempt to match a narrower range, falling back to returning null.
   */
  @Nullable
  public static TextRange matchedTextRange(Matcher matcher, int startGroup, int endGroup) {
    int start = matcher.start(startGroup);
    int end = matchEndIndex(matcher, endGroup);
    if (start == -1 || start >= end) {
      return null;
    }
    return TextRange.create(start, end);
  }

  /**
   * The end index of the given capturing group, or if that group didn't match anything, the end
   * index of the previous matching group. If no such group can be found, returns -1.
   */
  private static int matchEndIndex(Matcher matcher, int group) {
    for (int ix = group; ix >= 0; ix--) {
      int endIx = matcher.end(ix);
      if (endIx != -1) {
        return endIx;
      }
    }
    return -1;
  }
}
