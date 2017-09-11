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
package com.android.tools.idea.lang.roomSql.psi

import com.intellij.extapi.psi.ASTWrapperPsiElement
import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement

interface RoomNameElement : PsiElement {
  val nameAsString: String
  val nameIsQuoted: Boolean
}

abstract class AbstractRoomNameElement(node: ASTNode) : ASTWrapperPsiElement(node), RoomNameElement {
  override val nameAsString: String
    get() {
      val leaf = firstChild.node!!
      return when (leaf.elementType) {
        RoomPsiTypes.BRACKET_LITERAL -> leaf.text.substring(1, leaf.textLength - 1)
        RoomPsiTypes.BACKTICK_LITERAL -> leaf.text.substring(1, leaf.textLength - 1).replace("``", "`")
        RoomPsiTypes.SINGLE_QUOTE_STRING_LITERAL -> leaf.text.substring(1, leaf.textLength - 1).replace("''", "'")
        RoomPsiTypes.DOUBLE_QUOTE_STRING_LITERAL -> leaf.text.substring(1, leaf.textLength - 1).replace("\"\"", "\"")
        else -> leaf.text
      }
    }

  override val nameIsQuoted: Boolean
    get() = firstChild.node.elementType != RoomPsiTypes.IDENTIFIER
}
