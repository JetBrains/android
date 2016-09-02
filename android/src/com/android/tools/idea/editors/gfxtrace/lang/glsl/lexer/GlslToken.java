/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.editors.gfxtrace.lang.glsl.lexer;

import com.android.tools.idea.editors.gfxtrace.lang.glsl.language.GlslLanguage;
import com.google.common.collect.ImmutableMap;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;

public class GlslToken extends IElementType {

  /**
   * Use flyweight here.
   */
  private static ImmutableMap<TokenKind, GlslToken> myTypes = createMap();

  public static GlslToken fromKind(@NotNull TokenKind kind) {
    return myTypes.get(kind);
  }

  private static ImmutableMap<TokenKind, GlslToken> createMap() {
    ImmutableMap.Builder<TokenKind, GlslToken> builder = ImmutableMap.builder();
    for (TokenKind kind : TokenKind.values()) {
      builder.put(kind, new GlslToken(kind));
    }
    return builder.build();
  }

  public final TokenKind myKind;

  public GlslToken(TokenKind kind) {
    super(kind.name(), GlslLanguage.INSTANCE);
    myKind = kind;
  }

  @Override
  public String toString() {
    return myKind.toString();
  }
}
