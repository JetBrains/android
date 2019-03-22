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
package com.android.tools.idea.res

import com.android.tools.idea.testing.AndroidProjectRule
import com.intellij.ide.impl.dataRules.PsiFileRule
import com.intellij.openapi.fileTypes.FileTypeRegistry
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiFileFactory
import com.intellij.psi.PsiManager
import com.intellij.testFramework.PsiTestUtil
import com.intellij.testFramework.runInEdtAndGet
import com.intellij.util.IncorrectOperationException
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class ResourceGroupVirtualDirectoryTest {

  @get:Rule
  val rule = AndroidProjectRule.inMemory()

  @Test
  fun hashIsCorrect() {
    val files = listOf<PsiFile>(rule.fixture.addFileToProject("/res/drawable-hdpi/res1.png", ""),
                                rule.fixture.addFileToProject("/res/drawable-mdpi/res1.png", ""),
                                rule.fixture.addFileToProject("/res/drawable-ldpi/res1.png", ""))

    val original = ResourceGroupVirtualDirectory("res1", files)
    assertEquals(original, ResourceGroupVirtualDirectory("res1", files))
    assertEquals(original.hashCode(), ResourceGroupVirtualDirectory("res1", files).hashCode())
    assertEquals(original, ResourceGroupVirtualDirectory("res1", files.toList()))
    assertEquals(original.hashCode(), ResourceGroupVirtualDirectory("res1", files.toList()).hashCode())


    val otherFiles = listOf<PsiFile>(rule.fixture.addFileToProject("/res/drawable-hdpi/res2.png", ""),
                                     rule.fixture.addFileToProject("/res/drawable-mdpi/res2.png", ""),
                                     rule.fixture.addFileToProject("/res/drawable-ldpi/res2.png", ""))

    assertNotEquals(original, ResourceGroupVirtualDirectory("res1", otherFiles))
    assertNotEquals(original.hashCode(), ResourceGroupVirtualDirectory("res1", otherFiles).hashCode())
    assertNotEquals(original, ResourceGroupVirtualDirectory("res1", otherFiles.toList()))
    assertNotEquals(original.hashCode(), ResourceGroupVirtualDirectory("res1", otherFiles.toList()).hashCode())
    assertNotEquals(original, null)
  }
}