/*
 * Copyright (C) 2021 The Android Open Source Project
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
package org.jetbrains.android.refactoring

import com.android.tools.idea.concurrency.executeOnPooledThread
import com.android.tools.idea.testing.caret
import com.google.common.truth.Truth
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.psi.PsiElement
import com.intellij.usages.UsageTargetUtil
import com.intellij.usages.impl.rules.UsageType
import com.intellij.usages.impl.rules.UsageTypeProvider
import com.intellij.usages.impl.rules.UsageTypeProviderEx
import org.jetbrains.android.AndroidTestCase

class GradleUsageTypeProviderTest: AndroidTestCase() {
  /**
   * Tests for [GradleUsageTypeProvider]
   */
  fun testGroovyElement() {
    val file = myFixture.addFileToProject("Foo.gradle", "class F${caret}oo {}")
    myFixture.configureFromExistingVirtualFile(file.virtualFile)
    val elementAtCaret = file.findElementAt(myFixture.caretOffset)
    val usageType = getUsageType(elementAtCaret!!)
    Truth.assertThat(usageType).isNotNull()
    Truth.assertThat(usageType.toString()).isEqualTo("In Gradle build script")
  }

  fun testKotlinScriptElement() {
    val file = myFixture.addFileToProject("Foo.gradle.kts", "class F${caret}oo {}")
    myFixture.configureFromExistingVirtualFile(file.virtualFile)
    val elementAtCaret = file.findElementAt(myFixture.caretOffset)
    val usageType = getUsageType(elementAtCaret!!)
    Truth.assertThat(usageType).isNotNull()
    Truth.assertThat(usageType.toString()).isEqualTo("In Gradle build script")
  }

  // whatever is returned on a normal Kotlin file, it should not be related to Gradle (a null result is OK)
  fun testKotlinClassNameElement() {
    val file = myFixture.addFileToProject("Foo.kt", "class F${caret}oo {}")
    myFixture.configureFromExistingVirtualFile(file.virtualFile)
    val elementAtCaret = file.findElementAt(myFixture.caretOffset)
    val usageType = getUsageType(elementAtCaret!!)
    if (usageType != null) {
      Truth.assertThat(usageType.toString()).doesNotContainMatch("Gradle")
    }
  }

  fun testKotlinClassReferenceElement() {
    val file = myFixture.addFileToProject("Foo.kt", "class Foo {}\n\nclass Bar : F${caret}oo {}")
    myFixture.configureFromExistingVirtualFile(file.virtualFile)
    val elementAtCaret = file.findElementAt(myFixture.caretOffset)
    val usageType = getUsageType(elementAtCaret!!)
    if (usageType != null) {
      Truth.assertThat(usageType.toString()).doesNotContainMatch("Gradle")
    }
  }

  private fun getUsageType(element: PsiElement) : UsageType? {
    return executeOnPooledThread {
      runReadAction {
        UsageTypeProvider.EP_NAME.extensionList.firstNotNullOfOrNull {
          when (it) {
            is UsageTypeProviderEx ->
              it.getUsageType(
                element,
                UsageTargetUtil.findUsageTargets { dataId -> (myFixture.editor as EditorEx).dataContext.getData(dataId) } ?: emptyArray()
              )
            else -> it.getUsageType(element)
          }
        }
      }
    }.get()
  }
}