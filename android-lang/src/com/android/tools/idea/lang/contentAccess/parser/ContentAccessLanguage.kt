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
package com.android.tools.idea.lang.contentAccess.parser

import com.android.tools.idea.lang.androidSql.ANDROID_SQL_ICON
import com.android.tools.idea.lang.androidSql.AndroidSqlLanguage
import com.android.tools.idea.lang.androidSql.parser.AndroidSqlParserDefinition
import com.intellij.extapi.psi.PsiFileBase
import com.intellij.lang.Language
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.fileTypes.LanguageFileType
import com.intellij.openapi.project.Project
import com.intellij.psi.FileViewProvider
import com.intellij.psi.tree.IFileElementType
import javax.swing.Icon

/**
 * It's a subset of [AndroidSqlLanguage] that accepts only WHERE clause (without WHERE keyword).
 *
 * Examples: "columnName > 0", "id = :param", "favorite_website = 'developer.android.com' AND customer_id > 6000"
 */
object ContentAccessLanguage : Language(AndroidSqlLanguage.INSTANCE, "ContentAccess")

class ContentAccessFileType : LanguageFileType(ContentAccessLanguage) {
  companion object {
    /** Static field used in XML to register the instance. */
    @JvmStatic
    val INSTANCE = ContentAccessFileType()
  }

  override fun getName(): String = "ContentAccess"
  override fun getDescription(): String = "ContentAccess"
  override fun getDefaultExtension(): String = ""
  override fun getIcon(): Icon = ANDROID_SQL_ICON
}

private val CONTENT_ACCESS_FILE_NODE_TYPE = IFileElementType(ContentAccessLanguage)

class ContentAccessFile(viewProvider: FileViewProvider) : PsiFileBase(viewProvider, ContentAccessLanguage) {
  override fun getFileType(): FileType = ContentAccessFileType.INSTANCE
  override fun getIcon(flags: Int): Icon? = ANDROID_SQL_ICON
}

class ContentAccessParserDefinition : AndroidSqlParserDefinition() {
  override fun createParser(project: Project?) = ContentAccessParser()
  override fun createFile(viewProvider: FileViewProvider) = ContentAccessFile(viewProvider)
  override fun getFileNodeType(): IFileElementType = CONTENT_ACCESS_FILE_NODE_TYPE
}