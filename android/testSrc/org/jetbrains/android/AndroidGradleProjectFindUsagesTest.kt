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
import com.intellij.openapi.project.guessProjectDir

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
    myFixture.moveCaret("R.color.abc_tint|_default")
    val usages = AndroidResourcesFindUsagesTest.findUsages(myFixture.file.virtualFile, myFixture)
    val treeTextRepresentation = myFixture.getUsageViewTreeTextRepresentation(usages)
    assertThat(treeTextRepresentation)
      .isEqualTo("Usage (4 usages)\n" +
                 " Found usages (4 usages)\n" +
                 "  Resource reference Android resources XML (2 usages)\n" +
                 "   color (1 usage)\n" +
                 "    abc_tint_default.xml (1 usage)\n" +
                 "     1<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                 "   color-v23 (1 usage)\n" +
                 "    abc_tint_default.xml (1 usage)\n" +
                 "     1<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                 "  Resource reference in code (2 usages)\n" +
                 "   testResourceDefinedInAarUsingModuleRClass.app (1 usage)\n" +
                 "    com.example.google.androidx (1 usage)\n" +
                 "     MainActivity.kt (1 usage)\n" +
                 "      MainActivity (1 usage)\n" +
                 "       onCreate (1 usage)\n" +
                 "        12val color = R.color.abc_tint_default\n" +
                 "   testResourceDefinedInAarUsingModuleRClass.library (1 usage)\n" +
                 "    google.mylibrary (1 usage)\n" +
                 "     Library (1 usage)\n" +
                 "      foo() (1 usage)\n" +
                 "       6int color = R.color.abc_tint_default;\n")
  }

  fun testResourceDefinedInAarUsingLibRClass() {
    val activityPath = "app/src/main/java/com/example/google/androidx/MainActivity.kt"
    val file = project.guessProjectDir()!!.findFileByRelativePath(activityPath)
    myFixture.openFileInEditor(file!!)

    // Resource from androidx library used in both app and lib modules
    myFixture.moveCaret("R.color.abc_tint_default|")

    // Adding the fully qualified resource reference from the module R class.
    myFixture.type("\n    com.example.google.androidx.R.color.abc_tint_default")

    // Adding the non-transitive representation of the same resource written above, ie. from Aar R class. Although they have different
    // fully qualified paths, they reference the same resource.
    myFixture.type("\n    androidx.appcompat.R.color.abc_tint_default")
    val usages = AndroidResourcesFindUsagesTest.findUsages(myFixture.file.virtualFile, myFixture)
    val treeTextRepresentation = myFixture.getUsageViewTreeTextRepresentation(usages)
    assertThat(treeTextRepresentation)
      .isEqualTo("Usage (6 usages)\n" +
                 " Found usages (6 usages)\n" +
                 "  Resource reference Android resources XML (2 usages)\n" +
                 "   color (1 usage)\n" +
                 "    abc_tint_default.xml (1 usage)\n" +
                 "     1<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                 "   color-v23 (1 usage)\n" +
                 "    abc_tint_default.xml (1 usage)\n" +
                 "     1<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                 "  Resource reference in code (4 usages)\n" +
                 "   testResourceDefinedInAarUsingLibRClass.app (3 usages)\n" +
                 "    com.example.google.androidx (3 usages)\n" +
                 "     MainActivity.kt (3 usages)\n" +
                 "      MainActivity (3 usages)\n" +
                 "       onCreate (3 usages)\n" +
                 "        12val color = R.color.abc_tint_default\n" +
                 "        13com.example.google.androidx.R.color.abc_tint_default\n" +
                 "        14androidx.appcompat.R.color.abc_tint_default\n" +
                 "   testResourceDefinedInAarUsingLibRClass.library (1 usage)\n" +
                 "    google.mylibrary (1 usage)\n" +
                 "     Library (1 usage)\n" +
                 "      foo() (1 usage)\n" +
                 "       6int color = R.color.abc_tint_default;\n")
  }
}