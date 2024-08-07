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
package com.google.idea.blaze.base.lang.projectview.lexer;

import com.google.idea.blaze.base.lang.projectview.language.ProjectViewLanguage;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;

/** Lexical elements for the project view language. */
public class ProjectViewTokenType extends IElementType {

  // only start-of-line (ignoring whitespace) comments are valid
  public static final ProjectViewTokenType COMMENT = create("comment");
  public static final ProjectViewTokenType WHITESPACE = create("whitespace");
  public static final ProjectViewTokenType NEWLINE = create("newline");
  public static final ProjectViewTokenType COLON = create(":");

  // any amount of whitespace at the start of a line, followed by a non-'#', non-newline character
  public static final ProjectViewTokenType INDENT = create("indent");

  // all remaining characters that aren't preceded by a start-of-line comments
  public static final ProjectViewTokenType IDENTIFIER = create("identifier");

  public static final ProjectViewTokenType LIST_KEYWORD = create("list_keyword");
  public static final ProjectViewTokenType SCALAR_KEYWORD = create("scalar_keyword");

  private static ProjectViewTokenType create(String debugName) {
    return new ProjectViewTokenType(debugName);
  }

  private ProjectViewTokenType(String debugName) {
    super(debugName, ProjectViewLanguage.INSTANCE);
  }

  public static final TokenSet IDENTIFIERS =
      TokenSet.create(IDENTIFIER, LIST_KEYWORD, SCALAR_KEYWORD);
}
