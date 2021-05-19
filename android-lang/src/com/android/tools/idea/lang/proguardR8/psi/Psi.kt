/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.lang.proguardR8.psi


import com.android.tools.idea.lang.proguardR8.ProguardR8FileType
import com.android.tools.idea.lang.proguardR8.ProguardR8Language
import com.intellij.extapi.psi.PsiFileBase
import com.intellij.openapi.fileTypes.FileType
import com.intellij.psi.FileViewProvider
import com.intellij.psi.tree.IElementType
import com.intellij.psi.tree.IFileElementType

class ProguardR8TokenType(debugName: String) : IElementType(debugName, ProguardR8Language.INSTANCE) {
  override fun toString(): String = when (val token = super.toString()) {
    "," -> "comma"
    "." -> "dot"
    ";" -> "semicolon"
    ":" -> "colon"
    "'" -> "single quote"
    "\"" -> "double quote"
    "*" -> "asterisk"
    "**" -> "double asterisk"
    "{" -> "opening brace"
    "}" -> "closing brace"
    "(" -> "left parenthesis"
    ")" -> "right parenthesis"
    else -> token
  }
}

class ProguardR8AstNodeType(debugName: String) : IElementType(debugName, ProguardR8Language.INSTANCE)

val PROGUARD_R8_FILE_NODE_TYPE = IFileElementType(ProguardR8Language.INSTANCE)

class ProguardR8PsiFile(viewProvider: FileViewProvider) : PsiFileBase(viewProvider, ProguardR8Language.INSTANCE) {
  override fun getFileType(): FileType = ProguardR8FileType.INSTANCE
}
