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
package com.google.idea.blaze.base.lang.projectview.parser;

import com.google.idea.blaze.base.lang.projectview.language.ProjectViewKeywords;
import com.google.idea.blaze.base.lang.projectview.lexer.ProjectViewTokenType;
import com.google.idea.blaze.base.lang.projectview.psi.ProjectViewElementTypes;
import com.google.idea.blaze.base.projectview.section.ScalarSectionParser;
import com.intellij.lang.PsiBuilder;
import com.intellij.openapi.application.ApplicationManager;
import javax.annotation.Nullable;

/** Project view psi parser. */
public class ProjectViewPsiParser {

  private final PsiBuilder builder;

  public ProjectViewPsiParser(PsiBuilder builder) {
    this.builder = builder;
  }

  public void parseFile() {
    builder.setDebugMode(ApplicationManager.getApplication().isUnitTestMode());
    while (!builder.eof()) {
      if (matches(ProjectViewTokenType.NEWLINE)) {
        continue;
      }
      parseSection();
    }
  }

  /** A block is one of: - scalar section - list section */
  private void parseSection() {
    PsiBuilder.Marker marker = builder.mark();
    if (matches(ProjectViewTokenType.LIST_KEYWORD)) {
      expect(ProjectViewTokenType.COLON);
      skipPastNewline();
      parseListItems();
      marker.done(ProjectViewElementTypes.LIST_SECTION);
      return;
    }
    if (currentToken() == ProjectViewTokenType.SCALAR_KEYWORD) {
      ScalarSectionParser<?> parser =
          ProjectViewKeywords.SCALAR_KEYWORD_MAP.get(builder.getTokenText());
      if (parser != null) {
        parseScalarSection(parser);
        marker.done(ProjectViewElementTypes.SCALAR_SECTION);
        return;
      }
    }
    // handle each of the error cases
    if (matches(ProjectViewTokenType.INDENT)) {
      skipBlockAndError(
          marker, "Invalid indentation. Indented lines must be preceded by a list keyword");
      return;
    }
    if (matches(ProjectViewTokenType.COLON)) {
      skipBlockAndError(marker, "Invalid section: lines cannot begin with a colon.");
      return;
    }
    skipBlockAndError(marker, "Unrecognized keyword: " + builder.getTokenText());
  }

  private void parseListItems() {
    while (!builder.eof()) {
      if (matches(ProjectViewTokenType.NEWLINE)) {
        continue;
      }
      if (!matches(ProjectViewTokenType.INDENT)) {
        return;
      }
      PsiBuilder.Marker marker = builder.mark();
      skipToNewlineToken();
      marker.done(ProjectViewElementTypes.LIST_ITEM);
      builder.advanceLexer();
    }
  }

  private void parseScalarSection(ScalarSectionParser<?> parser) {
    boolean whitespaceDivider = builder.rawLookup(1) == ProjectViewTokenType.WHITESPACE;
    builder.advanceLexer();

    char divider = parser.getDivider();
    if (divider == ' ') {
      if (!whitespaceDivider) {
        builder.error("Whitespace divider expected after '" + parser.getName() + "'");
        builder.advanceLexer();
      }
      parseScalarItem();
      return;
    }
    if (whitespaceDivider || !Character.toString(divider).equals(builder.getTokenText())) {
      builder.error(String.format("'%s' expected", divider));
    }
    if (!whitespaceDivider) {
      builder.advanceLexer();
    }
    parseScalarItem();
  }

  private void parseScalarItem() {
    PsiBuilder.Marker marker = builder.mark();
    skipToNewlineToken();
    marker.done(ProjectViewElementTypes.SCALAR_ITEM);
    builder.advanceLexer();
  }

  /** Consumes the current token iff it matches the expected type. Otherwise, returns false */
  private boolean matches(ProjectViewTokenType kind) {
    if (currentToken() == kind) {
      builder.advanceLexer();
      return true;
    }
    return false;
  }

  /**
   * Consumes the current token if it's of the expected type. Otherwise, returns false and reports
   * an error.
   */
  private boolean expect(ProjectViewTokenType kind) {
    if (matches(kind)) {
      return true;
    }
    builder.error(String.format("'%s' expected", kind));
    return false;
  }

  /** Checks if the upcoming sequence of tokens match that expected. Doesn't advance the parser. */
  private boolean atTokenSequence(ProjectViewTokenType... kinds) {
    for (int i = 0; i < kinds.length; i++) {
      if (builder.lookAhead(i) != kinds[i]) {
        return false;
      }
    }
    return true;
  }

  @Nullable
  private ProjectViewTokenType currentToken() {
    return (ProjectViewTokenType) builder.getTokenType();
  }

  private void skipBlockAndError(PsiBuilder.Marker marker, String message) {
    skipToNextBlock();
    marker.error(message);
  }

  /** Skip to the start of the next unindented line */
  private void skipToNextBlock() {
    while (!builder.eof()) {
      if (atTokenSequence(ProjectViewTokenType.NEWLINE, ProjectViewTokenType.IDENTIFIER)) {
        builder.advanceLexer();
        return;
      }
      builder.advanceLexer();
    }
  }

  /** Skip to the start of the next line */
  private void skipPastNewline() {
    while (!builder.eof()) {
      if (matches(ProjectViewTokenType.NEWLINE)) {
        return;
      }
      builder.advanceLexer();
    }
  }

  /** Skip to the end of the current line */
  private void skipToNewlineToken() {
    while (!builder.eof()) {
      if (currentToken() == ProjectViewTokenType.NEWLINE) {
        return;
      }
      builder.advanceLexer();
    }
  }
}
