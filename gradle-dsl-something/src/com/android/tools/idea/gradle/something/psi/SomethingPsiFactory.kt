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
package com.android.tools.idea.gradle.something.psi

import com.android.tools.idea.gradle.something.SomethingFileType
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFileFactory
import com.intellij.psi.util.PsiTreeUtil

class SomethingPsiFactory(private val project: Project) {
  private fun createFile(text: CharSequence): SomethingFile =
    PsiFileFactory.getInstance(project)
      .createFileFromText(
        "placeholder.something",
        SomethingFileType.INSTANCE,
        text,
        System.currentTimeMillis(),
        false
      ) as SomethingFile

  private inline fun <reified T : SomethingElement> createFromText(code: String): T? =
    createFile(code).descendantOfType()

  private inline fun <reified T : PsiElement> PsiElement.descendantOfType(): T? =
    PsiTreeUtil.findChildOfType(this, T::class.java, false)

  fun createStringLiteral(value: String): SomethingLiteral =
    createFromText("placeholder = \"$value\"") ?: error("Failed to create Something string from $value")

  fun createIntLiteral(value: Int): SomethingLiteral =
    createFromText("placeholder = $value") ?: error("Failed to create Something Int from $value")

  fun createBooleanLiteral(value: Boolean): SomethingLiteral =
    createFromText("placeholder = $value") ?: error("Failed to create Something Boolean from $value")

}
