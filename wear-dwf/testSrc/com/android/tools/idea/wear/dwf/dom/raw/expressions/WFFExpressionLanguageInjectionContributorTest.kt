/*
 * Copyright (C) 2025 The Android Open Source Project
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
package com.android.tools.idea.wear.dwf.dom.raw.expressions

import com.android.SdkConstants.FN_ANDROID_MANIFEST_XML
import com.android.testutils.TestUtils.resolveWorkspacePath
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.caret
import com.android.tools.idea.testing.createAndroidProjectBuilderForDefaultTestProjectStructure
import com.android.tools.idea.testing.flags.overrideForTest
import com.google.common.truth.Truth.assertThat
import com.intellij.lang.injection.general.LanguageInjectionContributor
import com.intellij.openapi.application.runReadAction
import com.intellij.psi.PsiFile
import com.intellij.psi.util.parentOfType
import com.intellij.psi.xml.XmlAttribute
import com.intellij.psi.xml.XmlAttributeValue
import com.intellij.psi.xml.XmlText
import com.intellij.testFramework.fixtures.InjectionTestFixture
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class WFFExpressionLanguageInjectionContributorTest {

  @get:Rule
  val projectRule =
    AndroidProjectRule.withAndroidModel(
      createAndroidProjectBuilderForDefaultTestProjectStructure().withMinSdk({ 33 })
    )

  val fixture
    get() = projectRule.fixture

  private val injectionFixture: InjectionTestFixture
    get() = InjectionTestFixture(fixture)

  private val injectionContributor = WFFExpressionLanguageInjectionContributor()

  @Before
  fun setup() {
    projectRule.fixture.testDataPath =
      resolveWorkspacePath("tools/adt/idea/wear-dwf/testData/").toString()

    // add a manifest file for the `res/` folder to be considered a resource folder
    fixture.addFileToProject(FN_ANDROID_MANIFEST_XML, "")

    LanguageInjectionContributor.INJECTOR_EXTENSION.addExplicitExtension(
      WFFExpressionLanguage,
      injectionContributor,
    )
  }

  @After
  fun tearDown() {
    LanguageInjectionContributor.INJECTOR_EXTENSION.removeExplicitExtension(
      WFFExpressionLanguage,
      injectionContributor,
    )
  }

  @Test
  fun `the WFF expression language is injected in attributes`() {
    val watchFaceFile = fixture.addFileToProject(
          "res/raw/watch_face.xml",
          // language=XML
          """
        <WatchFace>
          <Transform value="transform expression" someOtherAttribute="this shouldn't be injected" />
          <Variant value="variant expression" />
          <Gyro
            x="gyro x expression"
            y="gyro y expression"
            scaleX="gyro scaleX expression"
            scaleY="gyro scaleY expression"
            angle="gyro angle expression"
            alpha="gyro alpha expression"
            anotherAttribute="no expression here" />
          <Parameter expression="parameter expression" />
          <SomeOtherTagWithoutExpression expression="shoudln't be injected" value="me neither" />
          <Expression><![CDATA[([SECONDS_IN_DAY] >= 43200 && [SECONDS_IN_DAY] < 86400)]]></Expression>
        </WatchFace>
      """
            .trimIndent(),
    )

    fixture.configureFromExistingVirtualFile(watchFaceFile.virtualFile)

    runReadAction {
      val injections = injectionFixture.getAllInjections()
      val injectedLanguages = injections.map { it.second.language }.toSet()
      assertThat(injectedLanguages).containsExactly(WFFExpressionLanguage)
      val injectedAttributesByTag = injections
        .mapNotNull { (injectedPsiElement, _) -> injectedPsiElement as? XmlAttributeValue }
        .map {
          val attribute = it.parentOfType<XmlAttribute>(true)
          attribute?.parent?.name to attribute?.name
        }
      assertThat(injectedAttributesByTag).containsExactly(
        "Transform" to "value",
        "Variant" to "value",
        "Gyro" to "x",
        "Gyro" to "y",
        "Gyro" to "scaleX",
        "Gyro" to "scaleY",
        "Gyro" to "angle",
        "Gyro" to "alpha",
        "Parameter" to "expression",
      )
    }
  }

  @Test
  fun `the WFF expression language is not injected when the flag is disabled`() {
    StudioFlags.WEAR_DECLARATIVE_WATCH_FACE_XML_EDITOR_SUPPORT.overrideForTest(
      false,
      projectRule.testRootDisposable,
    )

    val watchFaceFile =
      fixture.addFileToProject(
        "res/raw/watch_face_transform.xml",
        // language=XML
        """
        <WatchFace>
          <Transform value='$caret' />
        </WatchFace>
      """
          .trimIndent(),
      )

    fixture.configureFromExistingVirtualFile(watchFaceFile.virtualFile)

    runReadAction { injectionFixture.assertInjectedLangAtCaret(null) }
  }

  @Test
  fun `the WFF expression is not injected in non-declarative watch face files`() {
    val notAWatchFaceFile =
      fixture.addFileToProject(
        "res/xml/not_a_watch_face_file.xml",
        // language=XML
        """
        <NotAWatchFace>
          <Transform value='$caret' />
        </NotAWatchFace>
      """
          .trimIndent(),
      )

    fixture.configureFromExistingVirtualFile(notAWatchFaceFile.virtualFile)

    runReadAction { injectionFixture.assertInjectedLangAtCaret(null) }
  }

  @Test
  fun `the WFF expression language is injected in expression tag text`() {
    val watchFaceFile =
      fixture.addFileToProject(
        "res/raw/watch_face.xml",
        // language=XML
        """
        <WatchFace>
          <Expression><![CDATA[([SECONDS_IN_DAY] >= 43200 && [SECONDS_IN_DAY] < 86400)]]></Expression>
          <SomeOtherTag><![CDATA[this shouldn't be injected]]></SomeOtherTag>
        </WatchFace>
      """
          .trimIndent(),
      )

    fixture.configureFromExistingVirtualFile(watchFaceFile.virtualFile)

    runReadAction {
      val injections = injectionFixture.getAllInjections()
      assertThat(injections).hasSize(1)

      val (psiElement, languageFile) = injections.single()
      assertThat(psiElement).isInstanceOf(XmlText::class.java)
      assertThat((psiElement as XmlText).value).isEqualTo("([SECONDS_IN_DAY] >= 43200 && [SECONDS_IN_DAY] < 86400)")
      assertThat(languageFile).isInstanceOf(PsiFile::class.java)
      assertThat(languageFile.language).isEqualTo(WFFExpressionLanguage)
    }
  }
}
