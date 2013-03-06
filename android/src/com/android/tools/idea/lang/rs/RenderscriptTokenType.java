/*
 * Copyright (C) 2013 The Android Open Source Project
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

package com.android.tools.idea.lang.rs;

import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public class RenderscriptTokenType extends IElementType {
  public static final RenderscriptTokenType KEYWORD = new RenderscriptTokenType("keyword");
  public static final RenderscriptTokenType IDENTIFIER = new RenderscriptTokenType("identifier");
  public static final RenderscriptTokenType BRACE = new RenderscriptTokenType("brace");
  public static final RenderscriptTokenType SEPARATOR = new RenderscriptTokenType("separator");
  public static final RenderscriptTokenType OPERATOR = new RenderscriptTokenType("operator");
  public static final RenderscriptTokenType COMMENT = new RenderscriptTokenType("comment");
  public static final RenderscriptTokenType STRING = new RenderscriptTokenType("string");
  public static final RenderscriptTokenType CHARACTER = new RenderscriptTokenType("character");
  public static final RenderscriptTokenType NUMBER = new RenderscriptTokenType("number");
  public static final RenderscriptTokenType EOF = new RenderscriptTokenType("eof");
  public static final RenderscriptTokenType UNKNOWN = new RenderscriptTokenType("unknown");

  public RenderscriptTokenType(@NotNull @NonNls String debugName) {
    super(debugName, RenderscriptLanguage.INSTANCE);
  }

  @Override
  public String toString() {
    return super.toString();
  }
}
