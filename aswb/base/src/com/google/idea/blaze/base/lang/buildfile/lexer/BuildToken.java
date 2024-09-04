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
package com.google.idea.blaze.base.lang.buildfile.lexer;

import com.google.common.collect.ImmutableMap;
import com.google.idea.blaze.base.lang.buildfile.language.BuildFileType;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;

/** The IElementTypes used by the BUILD language */
public class BuildToken extends IElementType {

  private static ImmutableMap<TokenKind, BuildToken> types = createMap();

  private static ImmutableMap<TokenKind, BuildToken> createMap() {
    ImmutableMap.Builder<TokenKind, BuildToken> builder = ImmutableMap.builder();
    for (TokenKind kind : TokenKind.values()) {
      builder.put(kind, new BuildToken(kind));
    }
    return builder.build();
  }

  public static BuildToken fromKind(TokenKind kind) {
    return types.get(kind);
  }

  public static final BuildToken IDENTIFIER = fromKind(TokenKind.IDENTIFIER);

  public static final TokenSet WHITESPACE_AND_NEWLINE =
      TokenSet.create(fromKind(TokenKind.WHITESPACE), fromKind(TokenKind.NEWLINE));

  public static final BuildToken COMMENT = fromKind(TokenKind.COMMENT);

  public final TokenKind kind;

  private BuildToken(TokenKind kind) {
    super(kind.name(), BuildFileType.INSTANCE.getLanguage());
    this.kind = kind;
  }

  @Override
  public String toString() {
    return kind.toString();
  }
}
