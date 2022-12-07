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
package com.android.tools.idea.nav.safeargs.psi.kotlin.gradle

import com.android.flags.junit.FlagRule
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.nav.safeargs.TestDataPaths
import com.android.tools.idea.nav.safeargs.project.NavigationResourcesModificationListener
import com.android.tools.idea.projectsystem.getMainModule
import com.android.tools.idea.testing.AndroidGradleProjectRule
import com.android.tools.idea.testing.caret
import com.android.tools.idea.testing.fileUnderGradleRoot
import com.android.tools.idea.testing.findAppModule
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.RunsInEdt
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture
import com.intellij.usages.PsiElementUsageTarget
import com.intellij.usages.UsageTargetUtil
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import java.io.File

@RunsInEdt
class SafeArgsKtFindingUsageTest {
  private val projectRule = AndroidGradleProjectRule()

  @get:Rule
  val restoreSafeArgsFlagRule = FlagRule(StudioFlags.NAV_SAFE_ARGS_SUPPORT)

  // The tests need to run on the EDT thread but we must initialize the project rule off of it
  @get:Rule
  val ruleChain = RuleChain.outerRule(projectRule).around(EdtRule())!!

  private val fixture get() = projectRule.fixture as JavaCodeInsightTestFixture

  @Before
  fun setUp() {
    StudioFlags.NAV_SAFE_ARGS_SUPPORT.override(true)
    fixture.testDataPath = TestDataPaths.TEST_DATA_ROOT
    projectRule.load(TestDataPaths.SIMPLE_KOTLIN_PROJECT) { projectRoot ->
      // Create a placeholder class that we can use as a hook to replace with various
      // versions throughout these tests.
      File(projectRoot, "app/src/main/java/com/example/myapplication/FooClass.kt").apply {
        parentFile.mkdirs()
        createNewFile()
        writeText(
          // language=kotlin
          """
            package com.example.myapplication
            
            import com.example.mylibrary.FirstFragmentArgs
                    
            class FooClass {
                fun myTest() {
                    val argsClass1 = First${caret}FragmentArgs.
                    val argsClass2 = FirstFragmentArgs().
                }
            }
          """.trimIndent())
      }
    }
    NavigationResourcesModificationListener.ensureSubscribed(fixture.project)
  }

  @Test
  fun testFindingUsages() {
    projectRule.requestSyncAndWait()

    val appModuleMain = fixture.project.findAppModule().getMainModule()
    val file = appModuleMain.fileUnderGradleRoot("src/main/java/com/example/myapplication/FooClass.kt")
    fixture.configureFromExistingVirtualFile(file!!)
    val targets = UsageTargetUtil.findUsageTargets { (fixture.editor as EditorEx).dataContext.getData(it) }
    val presentation = fixture.getUsageViewTreeTextRepresentation((targets.first() as PsiElementUsageTarget).element)
    assertThat(presentation).isEqualTo("""
      <root> (3)
       XML tag
        <FirstFragmentArgs> of file nav_graph.xml
       Usages in Project Files (3)
        Nested class/object (1)
         ${appModuleMain.name} (1)
          com.example.myapplication (1)
           FooClass.kt (1)
            FooClass (1)
             myTest (1)
              7val argsClass1 = FirstFragmentArgs.
        New instance creation (1)
         ${appModuleMain.name} (1)
          com.example.myapplication (1)
           FooClass.kt (1)
            FooClass (1)
             myTest (1)
              8val argsClass2 = FirstFragmentArgs().
        Usage in import (1)
         ${appModuleMain.name} (1)
          com.example.myapplication (1)
           FooClass.kt (1)
            3import com.example.mylibrary.FirstFragmentArgs

    """.trimIndent())
  }
}