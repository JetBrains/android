/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.lang.androidSql.parser;

import static com.android.tools.idea.lang.androidSql.parser.AndroidSqlParser.name;
import static com.android.tools.idea.lang.androidSql.psi.AndroidSqlPsiTypes.IDENTIFIER;
import static com.android.tools.idea.lang.androidSql.psi.AndroidSqlPsiTypes.REPLACE;

import com.intellij.lang.PsiBuilder;
import com.intellij.lang.parser.GeneratedParserUtilBase;

public class AndroidSqlParserUtil extends GeneratedParserUtilBase {

  public static boolean parseFunctionName(PsiBuilder builder, int level) {
    if (!recursion_guard_(builder, level, "function_name")) return false;
    boolean result;
    result = name(builder, level + 1);
    if (!result && builder.getTokenType() == REPLACE) {
      builder.remapCurrentToken(IDENTIFIER);
      builder.advanceLexer();
      result = true;
    }
    return result;
  }
}
