/*
 * Copyright (C) 2020 The Android Open Source Project
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
// generatedFilesHeader.txt
package com.android.tools.idea.lang.contentAccess.parser

import com.android.tools.idea.lang.androidSql.parser.AndroidSqlParser
import com.intellij.lang.PsiBuilder
import com.intellij.psi.tree.IElementType

/**
 * Parser for ContentAccess language. See places of use in [contentAccessInjections.xml]
 *
 * ContentAccess language accepts as a root tag expression in WHERE CLAUSE (without WHERE).
 * Examples: "columnName > 0", "id = :param", "favorite_website = 'developer.android.com' AND customer_id > 6000"
 *
 * To parse such root we are reusing [AndroidSqlParser.expression].
 */
class ContentAccessParser : AndroidSqlParser() {
  override fun parse_root_(t: IElementType, b: PsiBuilder): Boolean = expression(b, 0, 0)
}