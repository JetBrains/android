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
package org.jetbrains.android.intentions

import com.android.tools.idea.res.isRJavaClass
import com.android.tools.idea.testing.findClass
import com.android.tools.idea.testing.moveCaret
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.command.WriteCommandAction.runWriteCommandAction
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.roots.JavaProjectRootsUtil
import com.intellij.testFramework.PsiTestUtil
import com.intellij.testFramework.fixtures.impl.CodeInsightTestFixtureImpl.ensureIndexesUpToDate
import org.jetbrains.android.AndroidTestCase
import org.jetbrains.android.dom.manifest.Manifest
import org.jetbrains.jps.model.java.JavaSourceRootType
import org.jetbrains.jps.model.java.JpsJavaExtensionService

private const val CREATE_FIELD = "Create field"

class AndroidIntentionActionFilterTest : AndroidTestCase() {

  fun testLightClasses() {
    myFixture.addFileToProject("res/values/strings.xml", "<resources><string name='existing_res'></string></resources>")
    ensureIndexesUpToDate(project)

    runWriteCommandAction(project) {
      val permission = Manifest.getMainManifest(myFacet)!!.addPermission()
      permission.name.value = "existing_permission"
    }

    val psiClass = myFixture.addClass(
      """
      package p1.p2;

      class NewClass {
        void f() {
          int id1 = NewClass.normalField;
          int id2 = R.rField;
          int id3 = R.string.rStringField;
          String id4 = Manifest.manifestField;
          String id5 = Manifest.permission.manifestField;
        }
      }
      """.trimIndent()
    )

    // Sanity checks:
    val rClass = myFixture.findClass("p1.p2.R", psiClass)
    assertNotNull(rClass)
    assertTrue(isRJavaClass(rClass!!))
    assertNotNull(myFixture.findClass("p1.p2.R.string", psiClass))
    assertNotNull(myFixture.findClass("p1.p2.Manifest", psiClass))
    assertNotNull(myFixture.findClass("p1.p2.Manifest.permission", psiClass))

    myFixture.openFileInEditor(psiClass.containingFile.virtualFile)
    myFixture.moveCaret("normal|Field")
    assertThat(myFixture.availableIntentions.filter { it.text.contains(CREATE_FIELD) }).isNotEmpty()

    // Check we don't offer to create fields in R classes:
    myFixture.moveCaret("r|Field")
    assertThat(myFixture.availableIntentions.filter { it.text.contains(CREATE_FIELD) }).isEmpty()

    // Check we don't offer to create fields in inner R classes:
    myFixture.moveCaret("rString|Field")
    assertThat(myFixture.availableIntentions.filter { it.text.contains(CREATE_FIELD) }).isEmpty()

    // Check we don't offer to create fields in Manifest classes:
    myFixture.moveCaret("manifest|Field")
    assertThat(myFixture.availableIntentions.filter { it.text.contains(CREATE_FIELD) }).isEmpty()
  }

  fun testBuildConfig() {
    myFixture.addFileToProject(
      "genSrc/p1/p2/BuildConfig.java",
      """
      package p1.p2;

      public class BuildConfig {}
      """.trimIndent()
    )

    val genSrc = project.guessProjectDir()!!.findChild("genSrc")!!
    val properties = JpsJavaExtensionService.getInstance().createSourceRootProperties("", true)
    PsiTestUtil.addSourceRoot(myModule, genSrc, JavaSourceRootType.SOURCE, properties)

    val psiClass = myFixture.addClass(
      """
      package p1.p2;

      class NewClass {
        void f() {
          int x = NewClass.normalField;
          int y = BuildConfig.buildConfigField;
        }
      }
      """.trimIndent()
    )

    // Sanity checks:
    val buildConfig = myFixture.findClass("p1.p2.BuildConfig")
    assertTrue(JavaProjectRootsUtil.isInGeneratedCode(buildConfig.containingFile.virtualFile, project))
    myFixture.openFileInEditor(psiClass.containingFile.virtualFile)
    myFixture.moveCaret("normal|Field")
    assertThat(myFixture.availableIntentions.filter { it.text.contains(CREATE_FIELD) }).isNotEmpty()

    // Check we don't offer to create fields in BuildConfig classes:
    myFixture.moveCaret("buildConfig|Field")
    assertThat(myFixture.availableIntentions.filter { it.text.contains(CREATE_FIELD) }).isEmpty()
  }
}