/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.refactoring.modularize

import com.android.ide.common.resources.configuration.FolderConfiguration
import com.android.testutils.MockitoKt.any
import com.android.testutils.MockitoKt.argThat
import com.android.testutils.MockitoKt.eq
import com.android.testutils.MockitoKt.getTypedArgument
import com.android.testutils.MockitoKt.mock
import com.android.testutils.MockitoKt.mockStatic
import com.android.tools.idea.res.getFolderConfiguration
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.application.Application
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.util.Computable
import com.intellij.openapi.util.Iconable
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.xml.XmlTag
import com.intellij.ui.ColoredTreeCellRenderer
import com.intellij.ui.SimpleTextAttributes
import com.intellij.usageView.UsageInfo
import org.junit.Test
import org.mockito.Mockito
import org.mockito.Mockito.never
import org.mockito.Mockito.spy
import org.mockito.Mockito.verify
import java.awt.Color
import javax.swing.Icon
import kotlin.random.Random
import kotlin.test.assertFailsWith
import org.mockito.Mockito.`when` as given

class UsageInfoTreeNodeTest {

  @Test
  fun `constructor populates PsiElement from UsageInfo`() {
    val usageInfo = mock<UsageInfo>()
    val element = mock<PsiElement>()
    given(usageInfo.element).thenReturn(element)

    val node = UsageInfoTreeNode(usageInfo, Random.nextInt())

    assertThat(node.psiElement).isEqualTo(element)
  }

  @Test
  fun `constructor can populate PsiElement with null missing form UsageInfo`() {
    val node = UsageInfoTreeNode(mock(), Random.nextInt())

    assertThat(node.psiElement).isNull()
  }

  @Test
  fun `render throws a NPE if the PsiElement is missing`() {
    val app = mock<Application>()
    given(app.runReadAction(any<Computable<Icon>>())).thenAnswer {
      it.getTypedArgument<Computable<Icon>>(0).compute()
    }

    val node = UsageInfoTreeNode(mock(), Random.nextInt())

    mockStatic<ApplicationManager>().use {
      given(ApplicationManager.getApplication()).thenReturn(app)

      assertFailsWith<NullPointerException> { node.render(mock()) }
    }
  }

  @Test
  fun `render sets the icon from the PsiElement`() {
    val icon = mock<Icon>()
    val element = mock<PsiElement>()
    given(element.getIcon(Iconable.ICON_FLAG_VISIBILITY or Iconable.ICON_FLAG_READ_STATUS)).thenReturn(icon)
    val usageInfo = mock<UsageInfo>()
    given(usageInfo.element).thenReturn(element)

    val app = mock<Application>()
    given(app.runReadAction(any<Computable<Icon>>())).thenAnswer {
      it.getTypedArgument<Computable<Icon>>(0).compute()
    }

    val renderer = mock<ColoredTreeCellRenderer>()

    val node = UsageInfoTreeNode(usageInfo, Random.nextInt())

    mockStatic<ApplicationManager>().use {
      given(ApplicationManager.getApplication()).thenReturn(app)

      assertThat(
        assertFailsWith<IllegalArgumentException> { node.render(renderer) })
        .hasMessageThat().isEqualTo("Unknown psiElement $element")
      verify(renderer).icon = icon
    }
  }

  @Test
  fun `render appends XmlTag's text with attributes`() {
    val text = "<some element text>"
    val xmlTag = mock<XmlTag>()
    given(xmlTag.text).thenReturn(text)
    val usageInfo = mock<UsageInfo>()
    given(usageInfo.element).thenReturn(xmlTag)

    val renderer = mock<ColoredTreeCellRenderer>()

    val node = spy(UsageInfoTreeNode(usageInfo, Random.nextInt()))
    val attributes = mock<SimpleTextAttributes>()
    Mockito.doReturn(attributes).`when`(node).textAttributes

    mockStatic<ApplicationManager>().use {
      given(ApplicationManager.getApplication()).thenReturn(mock())

      node.render(renderer)

      verify(renderer).append(eq(text), eq(attributes))
    }
  }

  @Test
  fun `render handles PsiClass with name`() {
    val name = "<a PsiClass name>"
    val psiClass = mock<PsiClass>()
    given(psiClass.name).thenReturn(name)
    val usageInfo = mock<UsageInfo>()
    given(usageInfo.element).thenReturn(psiClass)

    val renderer = mock<ColoredTreeCellRenderer>()

    val node = spy(UsageInfoTreeNode(usageInfo, Random.nextInt()))
    val attributes = mock<SimpleTextAttributes>()
    Mockito.doReturn(attributes).`when`(node).textAttributes

    mockStatic<ApplicationManager>().use {
      given(ApplicationManager.getApplication()).thenReturn(mock())

      node.render(renderer)

      verify(renderer).append(eq(name), eq(attributes))
      verify(node).renderReferenceCount(eq(renderer), eq(attributes))
    }
  }

  @Test
  fun `render handles PsiClass without name`() {
    val psiClass = mock<PsiClass>()
    given(psiClass.name).thenReturn(null)
    val usageInfo = mock<UsageInfo>()
    given(usageInfo.element).thenReturn(psiClass)

    val renderer = mock<ColoredTreeCellRenderer>()

    val node = spy(UsageInfoTreeNode(usageInfo, Random.nextInt()))
    val attributes = mock<SimpleTextAttributes>()
    Mockito.doReturn(attributes).`when`(node).textAttributes

    mockStatic<ApplicationManager>().use {
      given(ApplicationManager.getApplication()).thenReturn(mock())

      node.render(renderer)

      verify(renderer).append(eq("<unknown>"), eq(attributes))
      verify(node).renderReferenceCount(eq(renderer), eq(attributes))
    }
  }

  @Test
  fun `render handles PsiFile with nontrivial folder config`() {
    val name = "<a PsiFile name>"
    val psiFile = mock<PsiFile>()
    given(psiFile.name).thenReturn(name)
    val usageInfo = mock<UsageInfo>()
    given(usageInfo.element).thenReturn(psiFile)

    val renderer = mock<ColoredTreeCellRenderer>()

    val node = spy(UsageInfoTreeNode(usageInfo, Random.nextInt()))
    val attributes = mock<SimpleTextAttributes>()
    val color = mock<Color>()
    given(attributes.fgColor).thenReturn(color)
    Mockito.doReturn(attributes).`when`(node).textAttributes

    val qualifierString = "<a qualifier string>" // a nontrivial config
    val folderConfiguration = mock<FolderConfiguration>()
    given(folderConfiguration.qualifierString).thenReturn(qualifierString)

    mockStatic<ApplicationManager>().use {
      given(ApplicationManager.getApplication()).thenReturn(mock())

      val `JVM class containing getFolderConfiguration` = Class.forName("com.android.tools.idea.res.IdeResourcesUtil")
      Mockito.mockStatic(`JVM class containing getFolderConfiguration`).use {
        given(getFolderConfiguration(psiFile)).thenReturn(folderConfiguration)

        node.render(renderer)

        verify(renderer).append(eq(name), eq(attributes))
        verify(renderer).append(eq(" ($qualifierString)"), argThat {
          it.style == attributes.style or SimpleTextAttributes.STYLE_SMALLER &&
          it.fgColor == attributes.fgColor
        })
        verify(node).renderReferenceCount(eq(renderer), eq(attributes))
      }
    }
  }

  @Test
  fun `render handles PsiFile with trivial folder config`() {
    val name = "<a PsiFile name>"
    val psiFile = mock<PsiFile>()
    given(psiFile.name).thenReturn(name)
    val usageInfo = mock<UsageInfo>()
    given(usageInfo.element).thenReturn(psiFile)

    val renderer = mock<ColoredTreeCellRenderer>()

    val node = spy(UsageInfoTreeNode(usageInfo, Random.nextInt()))
    val attributes = mock<SimpleTextAttributes>()
    val color = mock<Color>()
    given(attributes.fgColor).thenReturn(color)
    Mockito.doReturn(attributes).`when`(node).textAttributes

    val qualifierString = "" // a trivial config
    val folderConfiguration = mock<FolderConfiguration>()
    given(folderConfiguration.qualifierString).thenReturn(qualifierString)

    mockStatic<ApplicationManager>().use {
      given(ApplicationManager.getApplication()).thenReturn(mock())

      val `JVM class containing getFolderConfiguration` = Class.forName("com.android.tools.idea.res.IdeResourcesUtil")
      Mockito.mockStatic(`JVM class containing getFolderConfiguration`).use {
        given(getFolderConfiguration(psiFile)).thenReturn(folderConfiguration)

        node.render(renderer)

        verify(renderer).append(eq(name), eq(attributes))
        verify(renderer, never()).append(eq(" ($qualifierString)"), argThat {
          it.style == attributes.style or SimpleTextAttributes.STYLE_SMALLER &&
          it.fgColor == attributes.fgColor
        })
        verify(node).renderReferenceCount(eq(renderer), eq(attributes))
      }
    }
  }

  @Test
  fun `render raises NPE on PsiFile without folder config`() {
    val name = "<a PsiFile name>"
    val psiFile = mock<PsiFile>()
    given(psiFile.name).thenReturn(name)
    val usageInfo = mock<UsageInfo>()
    given(usageInfo.element).thenReturn(psiFile)

    val renderer = mock<ColoredTreeCellRenderer>()

    val node = spy(UsageInfoTreeNode(usageInfo, Random.nextInt()))
    val attributes = mock<SimpleTextAttributes>()
    val color = mock<Color>()
    given(attributes.fgColor).thenReturn(color)
    Mockito.doReturn(attributes).`when`(node).textAttributes

    mockStatic<ApplicationManager>().use {
      given(ApplicationManager.getApplication()).thenReturn(mock())

      val `JVM class containing getFolderConfiguration` = Class.forName("com.android.tools.idea.res.IdeResourcesUtil")
      Mockito.mockStatic(`JVM class containing getFolderConfiguration`).use {
        given(getFolderConfiguration(psiFile)).thenReturn(null)

        assertFailsWith<NullPointerException> { node.render(renderer) }

        verify(renderer).append(eq(name), eq(attributes))
        verify(node, never()).renderReferenceCount(eq(renderer), eq(attributes))
      }
    }
  }
}