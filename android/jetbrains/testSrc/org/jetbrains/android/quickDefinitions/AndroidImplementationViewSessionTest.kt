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
package org.jetbrains.android.quickDefinitions

import com.android.SdkConstants.FN_ANDROID_MANIFEST_XML
import com.android.SdkConstants.FN_RESOURCE_TEXT
import com.android.testutils.TestUtils
import com.android.tools.idea.res.addAarDependency
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.caret
import com.android.tools.idea.testing.onEdt
import com.google.common.truth.Truth.assertThat
import com.intellij.codeInsight.ShowImplementationsTestUtil
import com.intellij.codeInsight.documentation.DocumentationManager
import com.intellij.codeInsight.hint.ImplementationViewComponent
import com.intellij.codeInsight.hint.ImplementationViewElement
import com.intellij.codeInsight.hint.ImplementationViewSession
import com.intellij.codeInsight.hint.PsiImplementationViewElement
import com.intellij.codeInsight.hint.actions.ShowImplementationsAction
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupManager
import com.intellij.ide.DataManager
import com.intellij.openapi.util.Ref
import com.intellij.psi.PsiElement
import com.intellij.testFramework.RunsInEdt
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture
import com.intellij.util.containers.stream
import org.intellij.lang.annotations.Language
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

/**
 * Tests for [AndroidImplementationViewSession] and [ShowImplementationsAction].
 */
@RunWith(JUnit4::class)
class AndroidImplementationViewSessionTest {

  @get:Rule
  val edtRule = AndroidProjectRule.withSdk().onEdt()

  private val fixture by lazy {
    edtRule.projectRule.fixture.apply {
      testDataPath = TestUtils.resolveWorkspacePath("tools/adt/idea/android/testData").toString()
    } as JavaCodeInsightTestFixture
  }

  private val module by lazy { edtRule.projectRule.module }

  @Language("JAVA")
  private val constraintLayout =
    """
      package androidx.constraintlayout.widget;

      public class ConstraintLayout extends android.view.ViewGroup {
      }
      """.trimIndent()

  @Before
  fun setUp() {
    fixture.copyFileToProject(FN_ANDROID_MANIFEST_XML, FN_ANDROID_MANIFEST_XML)
    fixture.addClass(constraintLayout)
  }

  private val KOTLIN_ACTIVITY =
    //language=kotlin
    """
        package p1.p2

        import android.app.Activity
        import android.os.Bundle

        class MyActivity : Activity() {
          override fun onCreate(state: Bundle?) {
            setContentView(R.layout.layout)
            R.drawable.thumbnail${caret}
          }
        }
      """.trimIndent()

  /**
   * Tests on resource representations that resolve in the editor.
   */
  @Test
  @RunsInEdt
  fun drawableKotlin() {
    fixture.copyFileToProject("quickDefinitions/drawable1_thumbnail.png", "res/drawable-mdpi/thumbnail.png")
    fixture.copyFileToProject("quickDefinitions/drawable1_thumbnail.png", "res/drawable-xhdpi/thumbnail.png")
    val activityFile = fixture.addFileToProject("src/p1/p2/MyActivity.kt", KOTLIN_ACTIVITY).virtualFile
    fixture.configureFromExistingVirtualFile(activityFile)
    val implementations = ShowImplementationsTestUtil.getImplementations()
    assertThat(implementations).hasLength(2)
    assertThat(implementations.map { it.toString() }).containsExactly("PsiBinaryFile:thumbnail.png", "PsiBinaryFile:thumbnail.png")
  }

  @Test
  @RunsInEdt
  fun drawableFromResourceReferenceXml() {
    fixture.copyFileToProject("quickDefinitions/drawable1_thumbnail.png", "res/drawable-mdpi/thumbnail.png")
    fixture.copyFileToProject("quickDefinitions/drawable1_thumbnail.png", "res/drawable-xhdpi/thumbnail.png")
    val layoutFile = fixture.addFileToProject("res/layout/activity_main.xml",
      //language=xml
                                              """
      <androidx.constraintlayout.widget.ConstraintLayout
        xmlns:android="http://schemas.android.com/apk/res/android"
        xmlns:app="http://schemas.android.com/apk/res-auto"
        android:layout_width="match_parent"
        android:layout_height="match_parent">

        <ImageView
          android:id="@+id/editText"
          app:layout_constraintStart_toStartOf="parent"
          app:layout_constraintTop_toBottomOf="parent"
          android:src="@drawable/thumbnail${caret}"/>
      </androidx.constraintlayout.widget.ConstraintLayout>
      """.trimIndent())
    fixture.configureFromExistingVirtualFile(layoutFile.virtualFile)
    val implementations = ShowImplementationsTestUtil.getImplementations()
    assertThat(implementations.map(PsiElement::toString)).containsExactly(
        "PsiElement(XML_ATTRIBUTE_VALUE): ResourceReference{namespace=apk/res-auto, type=drawable, name=thumbnail}",
        "PsiBinaryFile:thumbnail.png",
        "PsiBinaryFile:thumbnail.png")
  }

  @Test
  @RunsInEdt
  fun colorFromResourceReferenceXml() {
    fixture.addFileToProject(
      "res/values/colors.xml",
      //language=XML
      """
        <resources>
          <color name="colorPrimary">#008577</color>
        </resources>
      """.trimIndent())
    val layoutFile = fixture.addFileToProject("res/layout/activity_main.xml",
      //language=xml
                                              """
      <androidx.constraintlayout.widget.ConstraintLayout
        xmlns:android="http://schemas.android.com/apk/res/android"
        android:layout_width="match_parent"
        android:layout_height="match_parent">

        <TextView
          android:layout_width="match_parent"
          android:layout_height="match_parent"
          android:textColor="@color/colorPrimary${caret}"/>
      </androidx.constraintlayout.widget.ConstraintLayout>
      """.trimIndent())
    fixture.configureFromExistingVirtualFile(layoutFile.virtualFile)
    val implementations = ShowImplementationsTestUtil.getImplementations()
    assertThat(implementations).hasLength(2)
    assertThat(implementations[0].toString()).isEqualTo(
        "PsiElement(XML_ATTRIBUTE_VALUE): ResourceReference{namespace=apk/res-auto, type=color, name=colorPrimary}")
    assertThat(ImplementationViewComponent.getNewText(implementations[1])).isEqualTo("  <color name=\"colorPrimary\">#008577</color>")
  }

  /**
   * Tests on resource representations from code completion
   */
  @Test
  @RunsInEdt
  fun completionColorReferenceXml() {
    fixture.addFileToProject(
      "res/values/colors.xml",
      //language=XML
      """
        <resources>
          <color name="colorPrimary">#008577</color>
          <color name="colorAccent">#008577</color>
        </resources>
      """.trimIndent())
    val layoutFile = fixture.addFileToProject("res/layout/activity_main.xml",
      //language=xml
                                              """
      <androidx.constraintlayout.widget.ConstraintLayout
        xmlns:android="http://schemas.android.com/apk/res/android"
        android:layout_width="match_parent"
        android:layout_height="match_parent">

        <TextView
          android:layout_width="match_parent"
          android:layout_height="match_parent"
          android:textColor="@color/colo${caret}"/>
      </androidx.constraintlayout.widget.ConstraintLayout>
      """.trimIndent()).virtualFile
    fixture.configureFromExistingVirtualFile(layoutFile)
    val completionElements = fixture.completeBasic()
    val colorPrimaryElement = completionElements.stream().filter { it.lookupString == "@color/colorPrimary" }.findFirst().get()
    val implementationsForCompletionObject = getImplementationsForCompletionObject(colorPrimaryElement)
    assertThat(implementationsForCompletionObject).hasLength(1)
    assertThat(ImplementationViewComponent.getNewText((implementationsForCompletionObject[0] as PsiImplementationViewElement).getPsiElement()))
      .isEqualTo("  <color name=\"colorPrimary\">#008577</color>")
  }

  @Test
  @RunsInEdt
  fun completionDrawableReferenceKotlin() {
    fixture.copyFileToProject("quickDefinitions/drawable1_thumbnail.png", "res/drawable-mdpi/thumbnail.png")
    fixture.copyFileToProject("quickDefinitions/drawable1_thumbnail.png", "res/drawable-xhdpi/thumbnail.png")
    fixture.copyFileToProject("quickDefinitions/drawable1_thumbnail.png", "res/drawable-xhdpi/thumbNotNail.png")
    val activityFile = fixture.addFileToProject(
      "src/p1/p2/MyActivity.kt",
      //language=kotlin
      """
        package p1.p2

        import android.app.Activity
        import android.os.Bundle

        class MyActivity : Activity() {
          override fun onCreate(state: Bundle?) {
            setContentView(R.layout.layout)
            R.drawable.thumb${caret}
          }
        }
      """.trimIndent()).virtualFile
    fixture.configureFromExistingVirtualFile(activityFile)
    val completionElements = fixture.completeBasic()
    val colorPrimaryElement = completionElements.stream().filter { it.lookupString == "thumbnail" }.findFirst().get()
    val implementations = getImplementationsForCompletionObject(colorPrimaryElement)
    assertThat(implementations).hasLength(2)
    assertThat(implementations.map { (it as PsiImplementationViewElement).getPsiElement().toString() })
      .containsExactly("PsiBinaryFile:thumbnail.png", "PsiBinaryFile:thumbnail.png")
  }

  @Test
  @RunsInEdt
  fun nonTransitiveAarRClassReferenceKotlin() {
    addAarDependency(fixture, module, "aarLib", "com.example.aarLib") { resDir ->
      resDir.parentFile.resolve(FN_RESOURCE_TEXT).writeText(
        """int color colorPrimary 0x7f010001"""
      )
      resDir.resolve("values/colors.xml").writeText(
        // language=XML
        """
        <resources>
          <color name="colorPrimary">#008577</color>
        </resources>
        """.trimIndent()
      )
    }

    fixture.configureFromExistingVirtualFile(fixture.addFileToProject(
      "src/p1/p2/MyActivity.kt",
      //language=kotlin
      """
        package p1.p2

        class MyActivity {
          fun onCreate() {
            com.example.aarLib.R.color.colorPrimary${caret}
          }
        }
      """.trimIndent()).virtualFile)
    val implementations = ShowImplementationsTestUtil.getImplementations()
    assertThat(implementations).hasLength(1)
    assertThat(implementations.map { ImplementationViewComponent.getNewText(it) })
      .containsExactly("  <color name=\"colorPrimary\">#008577</color>")
  }

  private fun getImplementationsForCompletionObject(lookupElement: LookupElement): Array<ImplementationViewElement> {
    val activeLookup = LookupManager.getInstance(fixture.project).activeLookup
    activeLookup!!.currentItem = lookupElement
    val dataContext = DataManager.getInstance().getDataContext(fixture.editor.component)
    val sessionRef = Ref<ImplementationViewSession>()
    object : ShowImplementationsAction() {

      override fun showImplementations(session: ImplementationViewSession,
                                       invokedFromEditor: Boolean,
                                       invokedByShortcut: Boolean) {
        sessionRef.set(session)
      }
    }.performForContext(dataContext)
    val session = sessionRef.get()
    val targetElement = DocumentationManager.getInstance(fixture.project).getElementFromLookup(fixture.editor, fixture.file)
    val newSession = session.factory.createSessionForLookupElement(session.project, session.editor, session.file,
                                                                   targetElement,true, alwaysIncludeSelf = true)
    return newSession!!.implementationElements.toTypedArray()
  }
}