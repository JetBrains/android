/*
 * Copyright (C) 2026 The Android Open Source Project
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
package com.android.tools.idea.compose.gradle.preview

import com.android.testutils.junit4.OldAgpTest
import com.android.tools.idea.compose.ANDROID_KOTLIN_MULTIPLATFORM_MULTI_PREVIEW
import com.android.tools.idea.compose.preview.getPreviewNodes
import com.android.tools.idea.compose.preview.isMultiPreviewAnnotation
import com.android.tools.idea.compose.preview.isPreviewAnnotation
import com.android.tools.idea.preview.find.findAllAnnotationsInGraph
import com.android.tools.idea.testartifacts.TestConfigurationTestingUtil.Companion.getPsiElement
import com.android.tools.idea.testartifacts.TestConfigurationTestingUtil.Method
import com.android.tools.idea.testing.AgpVersionSoftwareEnvironmentDescriptor
import com.android.tools.idea.testing.AndroidGradleProjectRule
import com.android.tools.idea.testing.EdtAndroidGradleProjectRule
import com.android.tools.idea.testing.onEdt
import com.android.tools.preview.SingleComposePreviewElementInstance
import com.intellij.openapi.application.runReadAction
import com.intellij.psi.PsiModifierListOwner
import com.intellij.testFramework.RunsInEdt
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.flow.toSet
import kotlinx.coroutines.runBlocking
import org.jetbrains.kotlin.psi.KtAnnotationEntry
import org.jetbrains.uast.UAnnotation
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.toUElement
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@RunsInEdt
class KotlinMultiplatformLibraryMultiPreviewTest {

  @get:Rule
  val projectRule: EdtAndroidGradleProjectRule =
    AndroidGradleProjectRule(agpVersionSoftwareEnvironment = AgpVersionSoftwareEnvironmentDescriptor.AGP_8_13).onEdt()

  @Before
  fun setup() {
    projectRule.loadProject(ANDROID_KOTLIN_MULTIPLATFORM_MULTI_PREVIEW)
  }

  @Test
  @OldAgpTest(gradleVersions = ["8.13"], agpVersions = ["8.13.0"])
  fun `findAllAnnotationsInGraph can find all Preview annotations from library multi preview PreviewFontScale`() = runBlocking {
    val multiPreviewUMethod = projectRule.project.getPsiElement(MULTI_PREVIEW_METHOD).toUElement()
    assertNotNull(multiPreviewUMethod)

    val annotations = multiPreviewUMethod.findAllAnnotationsInGraph(filter = { it.isPreviewAnnotation() }).toList()
    assertNotNull(annotations)

    val elements = annotations.mapNotNull { it.element.sourcePsi }
    val previewAnnotationsCount = elements.count { (it as? KtAnnotationEntry)?.shortName?.asString() == "Preview" }
    assertTrue(
      previewAnnotationsCount > 1,
      "There should be multiple Preview annotation found, but was $previewAnnotationsCount. " + "Found elements are: $elements.",
    )
  }

  @Test
  fun `PreviewFontScale is properly detected as MultiPreview annotation`() = runBlocking {
    val multiPreviewMethod = projectRule.project.getPsiElement(MULTI_PREVIEW_METHOD)
    assertIs<PsiModifierListOwner>(multiPreviewMethod)

    val annotation = multiPreviewMethod.annotations.singleOrNull { it.qualifiedName == PREVIEW_FONT_SCALE_QNAME }
    assertNotNull(
      annotation,
      "Expected PreviewFontScale annotation to be present, but found ${multiPreviewMethod.annotations.map { it.qualifiedName }}.",
    )

    val uAnnotation = annotation.toUElement()
    assertIs<UAnnotation>(uAnnotation)

    assertTrue(runReadAction { uAnnotation.isMultiPreviewAnnotation() })
  }

  @Test
  fun `UastAnnotationAttributesProvider can provide proper annotation parameter values for getPreviewNodes`() = runBlocking {
    val multiPreviewUMethod = projectRule.project.getPsiElement(MULTI_PREVIEW_METHOD).toUElement()
    assertIs<UMethod>(multiPreviewUMethod)

    val nodes = getPreviewNodes(multiPreviewUMethod, includeAllNodes = true)

    val fontScales = nodes.mapNotNull { it as? SingleComposePreviewElementInstance<*> }.map { it.configuration.fontScale }.toSet()

    assertTrue(fontScales.size > 1, "Expected multiple font scales, but found $fontScales.")
  }
}

private val MULTI_PREVIEW_METHOD = Method("org.example.project.ClickMeTextKt", "ClickMeText")
private const val PREVIEW_FONT_SCALE_QNAME = "androidx.compose.ui.tooling.preview.PreviewFontScale"
