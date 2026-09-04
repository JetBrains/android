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
package com.android.tools.idea.gradle.dsl.parser.settings

import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslAnchor
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslElement
import com.android.tools.idea.gradle.dsl.parser.elements.GradleNameElement
import com.android.tools.idea.gradle.dsl.parser.elements.GradlePropertiesDslElement
import com.google.common.truth.Truth.assertThat
import com.intellij.psi.PsiElement
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`

class ProjectPropertiesDslElementTest {

  @Test
  fun testRequestAnchorDelegatesToParentWhenParentIsPropertiesElement() {
    val parent = mock(GradlePropertiesDslElement::class.java)
    val child = mock(GradleDslElement::class.java)
    val anchor = GradleDslAnchor.Start(parent)
    `when`(parent.requestAnchor(child)).thenReturn(anchor)

    val element = ProjectPropertiesDslElement(parent, GradleNameElement.fake("project(':lib')"))
    assertThat(element.requestAnchor(child)).isSameAs(anchor)
  }

  @Test
  fun testRequestAnchorFallsBackToSuperWhenParentIsNotPropertiesElement() {
    // When parent is a GradleDslElement that is NOT an instance of GradlePropertiesDslElement,
    // requestAnchor should fall back to super.requestAnchor(element).
    val parent = mock(GradleDslElement::class.java)
    val element = ProjectPropertiesDslElement(parent, GradleNameElement.fake("project(':lib')"))
    val child = mock(GradleDslElement::class.java)
    val anchor = element.requestAnchor(child)
    assertThat(anchor).isInstanceOf(GradleDslAnchor.Start::class.java)
  }

  @Test
  fun testGetStandardProjectKey() {
    assertThat(ProjectPropertiesDslElement.getStandardProjectKey("project(':app')")).isEqualTo("project(':app')")
    assertThat(ProjectPropertiesDslElement.getStandardProjectKey("project(\":app\")")).isEqualTo("project(':app')")
    assertThat(ProjectPropertiesDslElement.getStandardProjectKey("project( ':lib' )")).isEqualTo("project(':lib')")
    assertThat(ProjectPropertiesDslElement.getStandardProjectKey("invalidProject")).isNull()
    assertThat(ProjectPropertiesDslElement.getStandardProjectKey("")).isNull()
  }

  @Test
  fun testProjectDirReturnsNullWhenNotSet() {
    val parent = mock(GradleDslElement::class.java)
    val element = ProjectPropertiesDslElement(parent, GradleNameElement.fake("project(':lib')"))
    assertThat(element.projectDir()).isNull()
  }

  @Test
  fun testCreateDelegatesToParent() {
    val parent = mock(GradlePropertiesDslElement::class.java)
    val psiElement = mock(PsiElement::class.java)
    `when`(parent.create()).thenReturn(psiElement)
    val element = ProjectPropertiesDslElement(parent, GradleNameElement.fake("project(':lib')"))
    assertThat(element.create()).isSameAs(psiElement)
  }
}
