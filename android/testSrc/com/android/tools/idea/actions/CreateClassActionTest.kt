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
package com.android.tools.idea.actions

import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.createAndroidProjectBuilder
import com.intellij.facet.FacetManager
import com.intellij.ide.IdeView
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiManager
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.RunsInEdt
import org.jetbrains.android.facet.AndroidFacet
import org.jetbrains.android.facet.AndroidRootUtil
import org.jetbrains.android.facet.IdeaSourceProvider
import org.junit.Assert
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain

@RunsInEdt
class CreateClassActionTest {

  private val projectRule = AndroidProjectRule.withAndroidModel(createAndroidProjectBuilder())

  @get:Rule
  val ruleChain = RuleChain.outerRule(projectRule).around(EdtRule())!!

  private val facet
    get() = FacetManager.getInstance(projectRule.module).getFacetByType(AndroidFacet.ID)!!

  @Test
  fun testGetDestinationDirectoryIdeHasOneDirectory() {
    val directory = PsiManager.getInstance(projectRule.project).findDirectory(AndroidRootUtil.getMainContentRoot(facet)!!)!!
    Assert.assertEquals(directory, CreateClassAction.getDestinationDirectory(mockIdeView(listOf(directory)), projectRule.module))
  }

  @Test
  fun testGetDestinationDirectoryIdeDoesntHaveOneDirectory() {
    val oldModel = facet.configuration.model

    val psiDirectories = IdeaSourceProvider
      .getCurrentSourceProviders(facet)
      .flatMap { it.javaDirectoryUrls }
      .map { VfsUtil.createDirectories(VfsUtilCore.urlToPath(it)) }
      .map { PsiManager.getInstance(projectRule.project).findDirectory(it)!! }

    val ide = mockIdeView(psiDirectories)

    try {
      val directory = CreateClassAction.getDestinationDirectory(ide, projectRule.module)!!
      Assert.assertEquals(psiDirectories.first().virtualFile.path, directory.virtualFile.path)
    }
    finally {
      facet.configuration.model = oldModel
    }
  }

  private fun mockIdeView(directories: Collection<PsiDirectory>): IdeView {
    return object : IdeView {
      override fun getDirectories(): Array<PsiDirectory> = directories.toTypedArray()
      override fun getOrChooseDirectory(): PsiDirectory? = directories.firstOrNull()
    }
  }
}
