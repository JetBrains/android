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
package com.google.idea.blaze.cpp.includes;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.idea.blaze.cpp.CLanguageCommenter;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiComment;
import com.jetbrains.cidr.lang.psi.OCFile;
import com.jetbrains.cidr.lang.psi.OCIncludeDirective;
import com.jetbrains.cidr.lang.psi.visitors.OCRecursiveVisitor;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses IWYU pragmas specified in a given file.
 *
 * <p>See:
 * https://github.com/include-what-you-use/include-what-you-use/blob/master/docs/IWYUPragmas.md
 */
final class IwyuPragmas {
  private static final String IWYU_PREFIX = "IWYU pragma:";

  public final OCFile file;
  public final Optional<PrivatePragma> privatePragma;
  public final ImmutableSet<KeepPragma> keeps;
  public final ImmutableSet<ExportPragma> exports;
  public final Optional<IncludePath> associatedHeader;

  private IwyuPragmas(
      OCFile file,
      Optional<PrivatePragma> privatePragma,
      ImmutableSet<KeepPragma> keeps,
      ImmutableSet<ExportPragma> exports,
      Optional<IncludePath> associatedHeader) {
    this.file = file;
    this.privatePragma = privatePragma;
    this.keeps = keeps;
    this.exports = exports;
    this.associatedHeader = associatedHeader;
  }

  public static IwyuPragmas parse(OCFile file) {
    BuildingVisitor builder = new BuildingVisitor(file);
    file.accept(builder);
    return builder.build();
  }

  /** Parse pragmas that are trailing comments */
  interface TrailingPragmaParser {
    /**
     * Checks if the pragmaContent is parsed by this parser, and updates the builder state if it is
     * actually parsed.
     *
     * @param builder builder to update on success
     * @param directive include directive with a trailing pragma comment
     * @param pragmaContent content from the pragma comment to parse
     * @return true if handled by this parser
     */
    boolean tryParse(BuildingVisitor builder, OCIncludeDirective directive, String pragmaContent);
  }

  /** Parse pragmas are standalone comments */
  interface StandalonePragmaParser {
    /**
     * Checks if the pragmaContent is parsed by this parser, and updates the builder state if it is
     * actually parsed.
     *
     * @param builder builder to update on success
     * @param pragmaContent content from the pragma comment to parse
     * @return true if handled by this parser
     */
    boolean tryParse(BuildingVisitor builder, String pragmaContent);
  }

  /** Represents a "keep" pragma */
  @AutoValue
  public abstract static class KeepPragma {
    /* TODO: keep pragmas can also be attached to a forward include. We don't yet handle that */
    private static final TrailingPragmaParser PARSER = new Parser();

    abstract IncludePath includePath();

    static KeepPragma create(IncludePath includePath) {
      return new AutoValue_IwyuPragmas_KeepPragma(includePath);
    }

    private static class Parser implements TrailingPragmaParser {
      private static final Pattern KEEP_PATTERN = Pattern.compile("^\\s*keep\\s*$");

      @Override
      public boolean tryParse(
          BuildingVisitor builder, OCIncludeDirective directive, String pragmaContent) {
        Matcher matcher = KEEP_PATTERN.matcher(pragmaContent);
        if (!matcher.find()) {
          return false;
        }
        builder.keeps.add(
            KeepPragma.create(
                IncludePath.create(directive.getReferenceText(), directive.getDelimiters())));
        return true;
      }
    }
  }

  /** Represents an "export" pragma */
  @AutoValue
  public abstract static class ExportPragma {
    private static final TrailingPragmaParser TRAIL_PARSER = new TrailParser();
    private static final StandalonePragmaParser RANGE_PARSER = new RangeParser();

    abstract IncludePath includePath();

    static ExportPragma create(IncludePath includePath) {
      return new AutoValue_IwyuPragmas_ExportPragma(includePath);
    }

    private static class TrailParser implements TrailingPragmaParser {

      private static final Pattern EXPORT_PATTERN = Pattern.compile("^\\s*export\\s*$");

      @Override
      public boolean tryParse(
          BuildingVisitor builder, OCIncludeDirective directive, String pragmaContent) {
        Matcher matcher = EXPORT_PATTERN.matcher(pragmaContent);
        if (!matcher.find()) {
          return false;
        }
        builder.exports.add(
            ExportPragma.create(
                IncludePath.create(directive.getReferenceText(), directive.getDelimiters())));
        return true;
      }
    }

    private static class RangeParser implements StandalonePragmaParser {

      private static final Pattern BEGIN_PATTERN = Pattern.compile("^\\s*begin_exports\\s*$");
      private static final Pattern END_PATTERN = Pattern.compile("^\\s*end_exports\\s*$");

      @Override
      public boolean tryParse(BuildingVisitor builder, String pragmaContent) {
        Matcher matcher = BEGIN_PATTERN.matcher(pragmaContent);
        if (matcher.matches()) {
          builder.includesInRange.clear();
          builder.collectRange = true;
          return true;
        }
        matcher = END_PATTERN.matcher(pragmaContent);
        if (matcher.matches()) {
          builder.collectRange = false;
          for (OCIncludeDirective directive : builder.includesInRange) {
            builder.exports.add(
                ExportPragma.create(
                    IncludePath.create(directive.getReferenceText(), directive.getDelimiters())));
          }
          builder.includesInRange.clear();
          return true;
        }
        return false;
      }
    }
  }

  /** Represents the "private" pragma */
  public static class PrivatePragma {
    private static final StandalonePragmaParser PARSER = new Parser();

    public final Optional<IncludePath> includeOther;

    PrivatePragma(IncludePath includeOther) {
      this.includeOther = Optional.of(includeOther);
    }

    PrivatePragma() {
      this.includeOther = Optional.empty();
    }

    private static class Parser implements StandalonePragmaParser {
      private static final Pattern PRIVATE_PATTERN =
          Pattern.compile("^\\s*private\\s*(,\\s*include\\s*(?<includename>.*)\\s*)?$");

      @Override
      public boolean tryParse(BuildingVisitor builder, String pragmaContent) {
        Matcher matcher = PRIVATE_PATTERN.matcher(pragmaContent);
        if (!matcher.find()) {
          return false;
        }
        String alternateInclude = matcher.group("includename");
        if (alternateInclude != null) {
          builder.privatePragma =
              Optional.of(new PrivatePragma(IncludePath.create(alternateInclude)));
        } else {
          builder.privatePragma = Optional.of(new PrivatePragma());
        }
        return true;
      }
    }
  }

  /** Represents the "associated" pragma */
  public static class AssociatedPragma {
    private static final TrailingPragmaParser PARSER = new Parser();

    private static class Parser implements TrailingPragmaParser {
      private static final Pattern ASSOCIATED_PATTERN = Pattern.compile("^\\s*associated\\s*$");

      @Override
      public boolean tryParse(
          BuildingVisitor builder, OCIncludeDirective directive, String pragmaContent) {
        Matcher matcher = ASSOCIATED_PATTERN.matcher(pragmaContent);
        if (!matcher.find()) {
          return false;
        }
        builder.associatedHeader =
            Optional.of(
                IncludePath.create(directive.getReferenceText(), directive.getDelimiters()));
        return true;
      }
    }
  }

  private static final ImmutableList<TrailingPragmaParser> TRAILING_PARSERS =
      ImmutableList.of(KeepPragma.PARSER, ExportPragma.TRAIL_PARSER, AssociatedPragma.PARSER);
  private static final ImmutableList<StandalonePragmaParser> STANDALONE_PARSERS =
      ImmutableList.of(ExportPragma.RANGE_PARSER, PrivatePragma.PARSER);

  private static class BuildingVisitor extends OCRecursiveVisitor {

    final OCFile file;
    final CLanguageCommenter commenter;

    Optional<PrivatePragma> privatePragma = Optional.empty();
    ImmutableSet.Builder<KeepPragma> keeps = ImmutableSet.builder();
    ImmutableSet.Builder<ExportPragma> exports = ImmutableSet.builder();
    Optional<IncludePath> associatedHeader = Optional.empty();

    List<OCIncludeDirective> includesInRange = new ArrayList<>();
    boolean collectRange;

    BuildingVisitor(OCFile file) {
      this.file = file;
      this.commenter = new CLanguageCommenter();
    }

    IwyuPragmas build() {
      return new IwyuPragmas(file, privatePragma, keeps.build(), exports.build(), associatedHeader);
    }

    @Override
    public void visitImportDirective(OCIncludeDirective directive) {
      if (collectRange) {
        includesInRange.add(directive);
      }
      visitTrailingComments(directive);
      super.visitImportDirective(directive);
    }

    @Override
    public void visitComment(PsiComment comment) {
      String text = trimCommentContent(comment.getText());
      if (text.startsWith(IWYU_PREFIX)) {
        String pragmaContent = StringUtil.trimStart(text, IWYU_PREFIX);
        for (StandalonePragmaParser parser : STANDALONE_PARSERS) {
          if (parser.tryParse(this, pragmaContent)) {
            break;
          }
        }
      }
      super.visitComment(comment);
    }

    // In older CIDR implementations, trailing comments are not separate PsiComment nodes. They
    // are simply part of the "directive content" PsiElement (which also has #include path).
    // Thus, we have to handle it at the OCIncludeDirective level instead of waiting for
    // visitComment() to run. In newer CIDR implementations, trailing comments are a separate
    // PsiComment node, but they are still a child of the OCIncludeDirective, so pragma should be
    // found in the directive's getText().
    private void visitTrailingComments(OCIncludeDirective directive) {
      String fullText = directive.getText();
      String pathText = directive.getReferenceText();
      if (pathText.isEmpty()) {
        return;
      }
      String afterPath = fullText.substring(fullText.indexOf(pathText) + pathText.length());
      OCIncludeDirective.Delimiters delimiters = directive.getDelimiters();
      int delimIndex = afterPath.indexOf(delimiters.getAfterText());
      if (delimIndex == -1) {
        return;
      }
      afterPath = afterPath.substring(delimIndex + delimiters.getAfterText().length()).trim();
      String trimmed = trimCommentContent(afterPath);
      if (trimmed.startsWith(IWYU_PREFIX)) {
        String pragmaContent = StringUtil.trimStart(trimmed, IWYU_PREFIX);
        for (TrailingPragmaParser parser : TRAILING_PARSERS) {
          if (parser.tryParse(this, directive, pragmaContent)) {
            break;
          }
        }
      }
    }

    private String trimCommentContent(String text) {
      if (text.startsWith(commenter.getLineCommentPrefix())) {
        return StringUtil.trimStart(text, commenter.getLineCommentPrefix()).trim();
      } else if (text.startsWith(commenter.getBlockCommentPrefix())) {
        return StringUtil.trimEnd(
                StringUtil.trimStart(text, commenter.getBlockCommentPrefix()),
                commenter.getBlockCommentSuffix())
            .trim();
      }
      return text.trim();
    }
  }
}
