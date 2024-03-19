/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.tools.idea.gradle.something.parser

import com.android.tools.idea.gradle.something.psi.SomethingBare
import com.android.tools.idea.gradle.something.psi.SomethingBlock
import com.android.tools.idea.gradle.something.psi.SomethingFactory
import com.android.tools.idea.gradle.something.psi.SomethingIdentifier
import com.android.tools.idea.gradle.something.psi.SomethingProperty
import com.android.tools.idea.gradle.something.psi.SomethingQualified
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.PsiElement
import com.intellij.psi.util.childLeafs

class PsiImplUtil {
  companion object {
    @JvmStatic
    fun getReceiver(property: SomethingProperty): SomethingProperty? = when (property) {
      is SomethingBare -> null
      is SomethingQualified -> property.property
      else -> error("foo")
    }
    @JvmStatic
    fun getField(property: SomethingProperty): SomethingIdentifier = when(property) {
      is SomethingBare -> property.identifier
      is SomethingQualified -> property.identifier!!
      else -> error("foo")
    }

    @JvmStatic
    fun getName(property: SomethingIdentifier): String? {
      return StringUtil.unescapeStringCharacters(property.text)
    }

    @JvmStatic
    fun getFactory(block: SomethingBlock): SomethingFactory? {
      return block.firstChild as? SomethingFactory
    }

    @JvmStatic
    fun getBlockEntriesStart(block: SomethingBlock): PsiElement? {
      return block.childLeafs.find { it.text == "{" }
    }
  }
}