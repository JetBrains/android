/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.smali;

import com.intellij.openapi.editor.colors.TextAttributesKey;

import static com.intellij.openapi.editor.DefaultLanguageHighlighterColors.*;
import static com.intellij.openapi.editor.colors.TextAttributesKey.createTextAttributesKey;

public final class SmaliHighlighterColors {
  public static final TextAttributesKey COMMENT_ATTR_KEY = createTextAttributesKey("SMALI_COMMENT", LINE_COMMENT);
  public static final TextAttributesKey JAVA_IDENTIFIER_ATTR_KEY = createTextAttributesKey("SMALI_JAVA_IDENTIFIER", CLASS_NAME);
  public static final TextAttributesKey KEYWORD_ATTR_KEY = createTextAttributesKey("SMALI_KEYWORD", KEYWORD);
  public static final TextAttributesKey STRING_ATTR_KEY = createTextAttributesKey("SMALI_STRING", STRING);
  public static final TextAttributesKey NUMBER_ATTR_KEY = createTextAttributesKey("SMALI_NUMBER", NUMBER);
  public static final TextAttributesKey BRACES_ATTR_KEY = createTextAttributesKey("SMALI_BRACES", BRACES);
  public static final TextAttributesKey PARENTHESES_ATTR_KEY = createTextAttributesKey("SMALI_PARENTHESIS", PARENTHESES);

  public static final TextAttributesKey CONSTANT_ATTR_KEY = createTextAttributesKey("SMALI_CONSTANT", CONSTANT);
  public static final TextAttributesKey STATIC_FIELD_ATTR_KEY = createTextAttributesKey("SMALI_STATIC_FIELD", STATIC_FIELD);
  public static final TextAttributesKey INSTANCE_FIELD_ATTR_KEY = createTextAttributesKey("SMALI_INSTANCE_FIELD", INSTANCE_FIELD);

  private SmaliHighlighterColors() {
  }
}
