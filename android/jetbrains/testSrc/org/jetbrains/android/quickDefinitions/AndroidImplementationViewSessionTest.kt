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

import com.android.SdkConstants
import com.android.tools.idea.res.addAarDependency
import com.android.tools.idea.testing.caret
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
import com.intellij.util.containers.stream
import org.intellij.lang.annotations.Language
import org.jetbrains.android.AndroidTestCase

/**
 * Tests for [AndroidImplementationViewSession] and [ShowImplementationsAction].
 */
class AndroidImplementationViewSessionTest : AndroidTestCase() {

  @Language("JAVA")
  private val constraintLayout =
    """
      package androidx.constraintlayout.widget;

      public class ConstraintLayout extends android.view.ViewGroup {
      }
      """.trimIndent()

  override fun setUp() {
    super.setUp()
    myFixture.addClass(constraintLayout)
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

  fun testDrawableKotlin() {
    myFixture.copyFileToProject("quickDefinitions/drawable1_thumbnail.png", "res/drawable-mdpi/thumbnail.png")
    myFixture.copyFileToProject("quickDefinitions/drawable1_thumbnail.png", "res/drawable-xhdpi/thumbnail.png")
    val activityFile = myFixture.addFileToProject("src/p1/p2/MyActivity.kt", KOTLIN_ACTIVITY).virtualFile
    myFixture.configureFromExistingVirtualFile(activityFile)
    val implementations = ShowImplementationsTestUtil.getImplementations()
    assertThat(implementations).hasLength(2)
    assertThat(implementations.map { it.toString() }).containsExactly("PsiBinaryFile:thumbnail.png", "PsiBinaryFile:thumbnail.png")
  }

  fun testDrawableFromResourceReferenceXml() {
    myFixture.copyFileToProject("quickDefinitions/drawable1_thumbnail.png", "res/drawable-mdpi/thumbnail.png")
    myFixture.copyFileToProject("quickDefinitions/drawable1_thumbnail.png", "res/drawable-xhdpi/thumbnail.png")
    val layoutFile = myFixture.addFileToProject("res/layout/activity_main.xml",
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
    myFixture.configureFromExistingVirtualFile(layoutFile.virtualFile)
    val implementations = ShowImplementationsTestUtil.getImplementations()
    assertThat(implementations.map(PsiElement::toString)).containsExactly(
        "PsiElement(XML_ATTRIBUTE_VALUE): ResourceReference{namespace=apk/res-auto, type=drawable, name=thumbnail}",
        "PsiBinaryFile:thumbnail.png",
        "PsiBinaryFile:thumbnail.png")
  }

  fun testColorFromResourceReferenceXml() {
    myFixture.addFileToProject(
      "res/values/colors.xml",
      //language=XML
      """
        <resources>
          <color name="colorPrimary">#008577</color>
        </resources>
      """.trimIndent())
    val layoutFile = myFixture.addFileToProject("res/layout/activity_main.xml",
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
    myFixture.configureFromExistingVirtualFile(layoutFile.virtualFile)
    val implementations = ShowImplementationsTestUtil.getImplementations()
    assertThat(implementations).hasLength(2)
    assertThat(implementations[0].toString()).isEqualTo(
        "PsiElement(XML_ATTRIBUTE_VALUE): ResourceReference{namespace=apk/res-auto, type=color, name=colorPrimary}")
    assertThat(ImplementationViewComponent.getNewText(implementations[1])).isEqualTo("  <color name=\"colorPrimary\">#008577</color>")
  }

  /**
   * Tests on resource representations from code completion
   */

  fun testCompletionColorReferenceXml() {
    myFixture.addFileToProject(
      "res/values/colors.xml",
      //language=XML
      """
        <resources>
          <color name="colorPrimary">#008577</color>
          <color name="colorAccent">#008577</color>
        </resources>
      """.trimIndent())
    val layoutFile = myFixture.addFileToProject("res/layout/activity_main.xml",
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
    myFixture.configureFromExistingVirtualFile(layoutFile)
    val completionElements = myFixture.completeBasic()
    val colorPrimaryElement = completionElements.stream().filter { it.lookupString == "@color/colorPrimary" }.findFirst().get()
    val implementationsForCompletionObject = getImplementationsForCompletionObject(colorPrimaryElement)
    assertThat(implementationsForCompletionObject).hasLength(1)
    assertThat(ImplementationViewComponent.getNewText((implementationsForCompletionObject[0] as PsiImplementationViewElement).getPsiElement()))
      .isEqualTo("  <color name=\"colorPrimary\">#008577</color>")
  }

  fun testCompletionDrawableReferenceKotlin() {
    myFixture.copyFileToProject("quickDefinitions/drawable1_thumbnail.png", "res/drawable-mdpi/thumbnail.png")
    myFixture.copyFileToProject("quickDefinitions/drawable1_thumbnail.png", "res/drawable-xhdpi/thumbnail.png")
    myFixture.copyFileToProject("quickDefinitions/drawable1_thumbnail.png", "res/drawable-xhdpi/thumbNotNail.png")
    val activityFile = myFixture.addFileToProject(
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
    myFixture.configureFromExistingVirtualFile(activityFile)
    val completionElements = myFixture.completeBasic()
    val colorPrimaryElement = completionElements.stream().filter { it.lookupString == "thumbnail" }.findFirst().get()
    val implementations = getImplementationsForCompletionObject(colorPrimaryElement)
    assertThat(implementations).hasLength(2)
    assertThat(implementations.map { (it as PsiImplementationViewElement).getPsiElement().toString() })
      .containsExactly("PsiBinaryFile:thumbnail.png", "PsiBinaryFile:thumbnail.png")
  }

  fun testNonTransitiveAarRClassReferenceKotlin() {
    addAarDependency(myFixture, myModule, "aarLib", "com.example.aarLib") { resDir ->
      resDir.parentFile.resolve(SdkConstants.FN_RESOURCE_TEXT).writeText(
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

    myFixture.configureFromExistingVirtualFile(myFixture.addFileToProject(
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
    val activeLookup = LookupManager.getInstance(myFixture.project).activeLookup
    activeLookup!!.currentItem = lookupElement
    val dataContext = DataManager.getInstance().getDataContext(myFixture.editor.component)
    val sessionRef = Ref<ImplementationViewSession>()
    object : ShowImplementationsAction() {

      override fun showImplementations(session: ImplementationViewSession,
                                       invokedFromEditor: Boolean,
                                       invokedByShortcut: Boolean) {
        sessionRef.set(session)
      }
    }.performForContext(dataContext)
    val session = sessionRef.get()
    val targetElement = DocumentationManager.getInstance(myFixture.project).getElementFromLookup(myFixture.editor, myFixture.file)
    val newSession = session.factory.createSessionForLookupElement(session.project, session.editor, session.file,
                                                                   targetElement,true, alwaysIncludeSelf = true)
    return newSession!!.implementationElements.toTypedArray()
  }
}