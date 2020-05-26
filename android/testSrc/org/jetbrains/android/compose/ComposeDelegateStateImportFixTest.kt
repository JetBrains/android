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
package org.jetbrains.android.compose

import com.google.common.truth.Truth.assertThat
import com.intellij.testFramework.fixtures.JavaCodeInsightFixtureTestCase
import org.jetbrains.kotlin.idea.KotlinFileType

class ComposeDelegateStateImportFixTest : JavaCodeInsightFixtureTestCase() {
  fun test() {
    myFixture.addFileToProject(
      "/androidx/compose/State.kt",
      //language=kotlin
      """
      package androidx.compose
      
      class MutableState<T>
      
      fun <T> state(v:() -> T) = MutableState<T>()
      
      inline operator fun <T> MutableState<T>.getValue(thisObj: Any?, property: KProperty<*>) = true

    """.trimIndent())

    myFixture.configureByText(
      KotlinFileType.INSTANCE,
      //language=kotlin
      """
        import androidx.compose.state
        
        val myVal by state { false }
      """.trimIndent()
    )
    val error = myFixture.doHighlighting().find { it.description?.contains("[DELEGATE_SPECIAL_FUNCTION_MISSING]") == true }
    assertThat(error).isNotNull()

    val fix = myFixture.getAllQuickFixes().find { it.text == "Import getValue" }
    assertThat(fix).isNotNull()
    fix!!.invoke(project, myFixture.editor, myFixture.file)

    myFixture.checkResult(
      //language=kotlin
      """
        import androidx.compose.getValue
        import androidx.compose.state
        
        val myVal by state { false }
      """.trimIndent()
    )
  }
}