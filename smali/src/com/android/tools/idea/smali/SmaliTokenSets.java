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

import static com.android.tools.idea.smali.psi.SmaliTypes.AM_ABSTRACT;
import static com.android.tools.idea.smali.psi.SmaliTypes.AM_BRIDGE;
import static com.android.tools.idea.smali.psi.SmaliTypes.AM_FINAL;
import static com.android.tools.idea.smali.psi.SmaliTypes.AM_INTERFACE;
import static com.android.tools.idea.smali.psi.SmaliTypes.AM_NATIVE;
import static com.android.tools.idea.smali.psi.SmaliTypes.AM_PRIVATE;
import static com.android.tools.idea.smali.psi.SmaliTypes.AM_PROTECTED;
import static com.android.tools.idea.smali.psi.SmaliTypes.AM_PUBLIC;
import static com.android.tools.idea.smali.psi.SmaliTypes.AM_STATIC;
import static com.android.tools.idea.smali.psi.SmaliTypes.AM_SYNCHRONIZED;
import static com.android.tools.idea.smali.psi.SmaliTypes.AM_SYNTHETIC;
import static com.android.tools.idea.smali.psi.SmaliTypes.AM_TRANSIENT;
import static com.android.tools.idea.smali.psi.SmaliTypes.AM_VOLATILE;
import static com.android.tools.idea.smali.psi.SmaliTypes.CHAR;
import static com.android.tools.idea.smali.psi.SmaliTypes.COMMENT;
import static com.android.tools.idea.smali.psi.SmaliTypes.DOT_ANNOTATION;
import static com.android.tools.idea.smali.psi.SmaliTypes.DOT_ANNOTATION_END;
import static com.android.tools.idea.smali.psi.SmaliTypes.DOT_CLASS;
import static com.android.tools.idea.smali.psi.SmaliTypes.DOT_FIELD;
import static com.android.tools.idea.smali.psi.SmaliTypes.DOT_IMPLEMENTS;
import static com.android.tools.idea.smali.psi.SmaliTypes.DOT_LINE;
import static com.android.tools.idea.smali.psi.SmaliTypes.DOT_METHOD;
import static com.android.tools.idea.smali.psi.SmaliTypes.DOT_METHOD_END;
import static com.android.tools.idea.smali.psi.SmaliTypes.DOT_PARAM;
import static com.android.tools.idea.smali.psi.SmaliTypes.DOT_PROLOGUE;
import static com.android.tools.idea.smali.psi.SmaliTypes.DOT_REGISTERS;
import static com.android.tools.idea.smali.psi.SmaliTypes.DOT_SOURCE;
import static com.android.tools.idea.smali.psi.SmaliTypes.DOT_SUPER;
import static com.android.tools.idea.smali.psi.SmaliTypes.DOUBLE_QUOTED_STRING;
import static com.android.tools.idea.smali.psi.SmaliTypes.FALSE;
import static com.android.tools.idea.smali.psi.SmaliTypes.HEX_NUMBER;
import static com.android.tools.idea.smali.psi.SmaliTypes.L_CURLY;
import static com.android.tools.idea.smali.psi.SmaliTypes.L_PARENTHESIS;
import static com.android.tools.idea.smali.psi.SmaliTypes.REGULAR_NUMBER;
import static com.android.tools.idea.smali.psi.SmaliTypes.R_CURLY;
import static com.android.tools.idea.smali.psi.SmaliTypes.R_PARENTHESIS;
import static com.android.tools.idea.smali.psi.SmaliTypes.TRUE;

import com.intellij.psi.tree.TokenSet;

public final class SmaliTokenSets {
  private SmaliTokenSets() {
  }

  static final TokenSet KEYWORD_TOKENS = TokenSet.create(DOT_CLASS, DOT_SOURCE, DOT_SUPER, DOT_FIELD, DOT_METHOD, DOT_METHOD_END,
                                                         DOT_ANNOTATION, DOT_ANNOTATION_END, DOT_IMPLEMENTS, DOT_LINE, DOT_PARAM,
                                                         DOT_PROLOGUE, DOT_REGISTERS, TRUE, FALSE);

  static final TokenSet ACCESS_MODIFIER_TOKENS = TokenSet.create(AM_ABSTRACT, AM_FINAL, AM_INTERFACE, AM_NATIVE, AM_PRIVATE, AM_PROTECTED,
                                                                 AM_PUBLIC, AM_STATIC, AM_SYNCHRONIZED, AM_TRANSIENT, AM_VOLATILE,
                                                                 AM_BRIDGE, AM_SYNTHETIC);

  static final TokenSet COMMENT_TOKENS = TokenSet.create(COMMENT);

  static final TokenSet STRING_TOKENS = TokenSet.create(DOUBLE_QUOTED_STRING, CHAR);

  static final TokenSet NUMBER_TOKENS = TokenSet.create(REGULAR_NUMBER, HEX_NUMBER);

  static final TokenSet BRACES_TOKENS = TokenSet.create(L_CURLY, R_CURLY);

  static final TokenSet PARENTHESES_TOKENS = TokenSet.create(L_PARENTHESIS, R_PARENTHESIS);
}
