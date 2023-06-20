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
package org.jetbrains.android

import com.android.tools.idea.testing.AndroidGradleTestCase
import com.android.tools.idea.testing.TestProjectPaths
import com.android.tools.idea.testing.moveCaret
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture
import com.intellij.testFramework.fixtures.impl.CodeInsightTestFixtureImpl
import com.intellij.usageView.UsageInfo
import com.intellij.usages.PsiElementUsageTarget
import com.intellij.usages.UsageTargetUtil

/**
 * Tests that require a gradle project to operate, eg. for including AARs
 */
class AndroidGradleProjectFindUsagesTest : AndroidGradleTestCase() {

  override fun setUp() {
    super.setUp()
    loadProject(TestProjectPaths.MULTIPLE_MODULE_DEPEND_ON_AAR)
  }

  fun testResourceDefinedInAarUsingModuleRClass() {
    val activityPath = "app/src/main/java/com/example/google/androidx/MainActivity.kt"
    val file = project.guessProjectDir()!!.findFileByRelativePath(activityPath)
    myFixture.openFileInEditor(file!!)

    // Resource from androidx library used in both app and lib modules
    myFixture.moveCaret("androidx.appcompat.R.color.abc_tint|_default")
    val usages = findUsages(myFixture.file.virtualFile, myFixture)
    val treeTextRepresentation = myFixture.getUsageViewTreeTextRepresentation(usages)
    assertThat(treeTextRepresentation)
      .isEqualTo("<root> (4)\n" +
                 " Usages in (4)\n" +
                 "  Resource reference Android resources XML (2)\n" +
                 "   color (1)\n" +
                 "    abc_tint_default.xml (1)\n" +
                 "     1<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                 "   color-v23 (1)\n" +
                 "    abc_tint_default.xml (1)\n" +
                 "     1<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                 "  Resource reference in code (2)\n" +
                 "   testResourceDefinedInAarUsingModuleRClass.app.main (1)\n" +
                 "    com.example.google.androidx (1)\n" +
                 "     MainActivity.kt (1)\n" +
                 "      MainActivity (1)\n" +
                 "       onCreate (1)\n" +
                 "        12val color = androidx.appcompat.R.color.abc_tint_default\n" +
                 "   testResourceDefinedInAarUsingModuleRClass.library.main (1)\n" +
                 "    google.mylibrary (1)\n" +
                 "     Library (1)\n" +
                 "      foo() (1)\n" +
                 "       6int color = androidx.appcompat.R.color.abc_tint_default;\n")
  }

  fun testResourceDefinedInAarUsingLibRClass() {
    val activityPath = "app/src/main/java/com/example/google/androidx/MainActivity.kt"
    val file = project.guessProjectDir()!!.findFileByRelativePath(activityPath)
    myFixture.openFileInEditor(file!!)

    // Resource from androidx library used in both app and lib modules
    myFixture.moveCaret("androidx.appcompat.R.color.abc_tint_default|")

    // Adding the fully qualified resource reference from the module R class.
    myFixture.type("\n    com.example.google.androidx.R.color.abc_tint_default")

    // Adding the non-transitive representation of the same resource written above, ie. from Aar R class. Although they have different
    // fully qualified paths, they reference the same resource.
    myFixture.type("\n    androidx.appcompat.R.color.abc_tint_default")
    val usages = findUsages(myFixture.file.virtualFile, myFixture)
    val treeTextRepresentation = myFixture.getUsageViewTreeTextRepresentation(usages)
    assertThat(treeTextRepresentation)
      .isEqualTo("<root> (5)\n" +
                 " Usages in (5)\n" +
                 "  Resource reference Android resources XML (2)\n" +
                 "   color (1)\n" +
                 "    abc_tint_default.xml (1)\n" +
                 "     1<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                 "   color-v23 (1)\n" +
                 "    abc_tint_default.xml (1)\n" +
                 "     1<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                 "  Resource reference in code (3)\n" +
                 "   testResourceDefinedInAarUsingLibRClass.app.main (2)\n" +
                 "    com.example.google.androidx (2)\n" +
                 "     MainActivity.kt (2)\n" +
                 "      MainActivity (2)\n" +
                 "       onCreate (2)\n" +
                 "        12val color = androidx.appcompat.R.color.abc_tint_default\n" +
                 "        14androidx.appcompat.R.color.abc_tint_default\n" +
                 "   testResourceDefinedInAarUsingLibRClass.library.main (1)\n" +
                 "    google.mylibrary (1)\n" +
                 "     Library (1)\n" +
                 "      foo() (1)\n" +
                 "       6int color = androidx.appcompat.R.color.abc_tint_default;\n")
  }

  fun findUsages(file: VirtualFile?, fixture: JavaCodeInsightTestFixture): Collection<UsageInfo?> {
    return findUsages(file, fixture, null)
  }

  fun findUsages(file: VirtualFile?, fixture: JavaCodeInsightTestFixture, scope: GlobalSearchScope?): Collection<UsageInfo?> {
    fixture.configureFromExistingVirtualFile(file!!)
    val targets = UsageTargetUtil.findUsageTargets { dataId: String? ->
      (fixture.editor as EditorEx).dataContext.getData(
        dataId!!)
    }
    assert(targets != null && targets.size > 0 && targets[0] is PsiElementUsageTarget)
    return (fixture as CodeInsightTestFixtureImpl).findUsages((targets!![0] as PsiElementUsageTarget).element, scope)
  }
}