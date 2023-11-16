/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.build.attribution.ui.view.details

import com.android.build.attribution.ui.MockUiData
import com.android.build.attribution.ui.controllers.WindowsDefenderPageHandlerImpl
import com.android.build.attribution.ui.model.WarningsDataPageModelImpl
import com.android.build.attribution.ui.model.WarningsPageId
import com.android.build.attribution.ui.model.WarningsTreeNode
import com.android.build.attribution.ui.model.WindowsDefenderWarningNodeDescriptor
import com.android.build.attribution.ui.view.ViewActionHandlers
import com.android.build.diagnostic.WindowsDefenderCheckService
import com.android.build.diagnostic.WindowsDefenderCheckerWrapper
import com.android.testutils.MockitoKt
import com.android.tools.adtui.TreeWalker
import com.android.tools.adtui.swing.FakeUi
import com.android.tools.idea.testing.AndroidProjectRule
import com.google.common.truth.Truth.assertThat
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.RunsInEdt
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito
import java.awt.Dimension
import java.nio.file.Path

@RunsInEdt
class WindowsDefenderWarningUITest {

  @get:Rule
  val projectRule = AndroidProjectRule.inMemory()

  @get:Rule
  val edtRule = EdtRule()

  private val listOfPaths = listOf(
    Path.of("C:\\Users\\username\\.gradle"),
    Path.of("C:\\Users\\username\\AndroidStudioProjects\\MyApp"),
    Path.of("C:\\Users\\username\\AppData\\Local\\Google\\Android")
  )

  private val checkerWrapperMock = Mockito.mock(WindowsDefenderCheckerWrapper::class.java).apply {
    Mockito.`when`(this.isStatusCheckIgnored(MockitoKt.any())).thenReturn(false)
    Mockito.`when`(this.isRealTimeProtectionEnabled).thenReturn(true)
    Mockito.`when`(this.canRunScript()).thenReturn(true)
    Mockito.`when`(this.getImportantPaths(MockitoKt.any())).thenReturn(listOfPaths)
  }

  @Test
  fun testWarningTreeNodeCreatedByTheModelWhenShouldBeShown() {
    val mockUiData = MockUiData().apply {
      windowsDefenderWarningData =  WindowsDefenderCheckService.WindowsDefenderWarningData(true, true, emptyList())
    }

    val model = WarningsDataPageModelImpl(mockUiData)
    assertThat(
      model.treeRoot.preorderEnumeration().asSequence()
        .filter { (it as? WarningsTreeNode)?.descriptor is WindowsDefenderWarningNodeDescriptor }
        .toList()
    ).hasSize(1)
  }

  @Test
  fun testWarningTreeNodeNotCreatedByTheModelWhenShouldNotBeShown() {
    val mockUiData = MockUiData().apply {
      windowsDefenderWarningData =  WindowsDefenderCheckService.NO_WARNING
    }

    val model = WarningsDataPageModelImpl(mockUiData)
    assertThat(
      model.treeRoot.preorderEnumeration().asSequence()
        .filter { (it as? WarningsTreeNode)?.descriptor is WindowsDefenderWarningNodeDescriptor }
        .toList()
    ).hasSize(0)
  }

  @Test
  fun testUICreationWhenCanRunScript() {
    val page = createPage(checkerWrapperMock)
    page.size = Dimension(600, 400)
    val ui = FakeUi(page)
    ui.layoutAndDispatchEvents()

    assertThat(page.autoExclusionLine.isVisible).isTrue()
    assertThat(page.autoExcludeStatus.isVisible).isFalse()
    assertThat(page.suppressLine.isVisible).isTrue()
    assertThat(page.warningSuppressedMessage.isVisible).isFalse()
    listOfPaths.forEach { assertThat(page.contentHtml).contains(it.toString()) }
  }

  @Test
  fun testUICreationWhenCanNotRunScript() {
    Mockito.`when`(checkerWrapperMock.canRunScript()).thenReturn(false)

    val page = createPage(checkerWrapperMock)
    page.size = Dimension(600, 400)
    val ui = FakeUi(page)
    ui.layoutAndDispatchEvents()

    assertThat(page.autoExclusionLine.isVisible).isFalse()
    assertThat(page.autoExcludeStatus.isVisible).isFalse()
    assertThat(page.suppressLine.isVisible).isTrue()
    assertThat(page.warningSuppressedMessage.isVisible).isFalse()
  }

  @Test
  fun testUIResponceOnSuppressAction() {
    val page = createPage(checkerWrapperMock)
    page.size = Dimension(600, 400)
    val ui = FakeUi(page)
    ui.layoutAndDispatchEvents()

    ui.clickOn(page.suppressWarningLink)
    ui.layoutAndDispatchEvents()

    assertThat(page.autoExclusionLine.isVisible).isTrue()
    assertThat(page.autoExcludeStatus.isVisible).isFalse()
    assertThat(page.suppressLine.isVisible).isTrue()
    assertThat(page.warningSuppressedMessage.isVisible).isTrue()
    Mockito.verify(checkerWrapperMock, Mockito.times(1)).ignoreStatusCheck(MockitoKt.any(), MockitoKt.eq(true))
  }

  @Test
  fun testUIResponceOnExcludeActionSuccess() {
    val page = createPage(checkerWrapperMock)
    page.size = Dimension(600, 400)
    val ui = FakeUi(page)
    ui.layoutAndDispatchEvents()

    // Exclusion script runs in Backgroundable task that in tests runs synchronously
    Mockito.`when`(checkerWrapperMock.excludeProjectPaths(MockitoKt.eq(listOfPaths))).then {
      assertThat(page.autoExcludeStatus.text).isEqualTo("Running...")
      return@then true
    }

    ui.clickOn(page.autoExcludeLink)
    ui.layoutAndDispatchEvents()

    assertThat(page.autoExclusionLine.isVisible).isTrue()
    assertThat(page.autoExcludeStatus.isVisible).isTrue()
    assertThat(page.autoExcludeStatus.text).isEqualTo("Project paths were successfully added to the Microsoft Defender exclusion list")
    assertThat(page.suppressLine.isVisible).isTrue()
    assertThat(page.warningSuppressedMessage.isVisible).isFalse()
    Mockito.verify(checkerWrapperMock, Mockito.times(1)).ignoreStatusCheck(MockitoKt.any(), MockitoKt.eq(true))
  }

  @Test
  fun testUIResponceOnExcludeActionFailure() {
    val page = createPage(checkerWrapperMock)
    page.size = Dimension(600, 400)
    val ui = FakeUi(page)
    ui.layoutAndDispatchEvents()

    // Exclusion script runs in Backgroundable task that in tests runs synchronously
    Mockito.`when`(checkerWrapperMock.excludeProjectPaths(MockitoKt.eq(listOfPaths))).then {
      assertThat(page.autoExcludeStatus.text).isEqualTo("Running...")
      return@then false
    }

    ui.clickOn(page.autoExcludeLink)
    ui.layoutAndDispatchEvents()

    assertThat(page.autoExclusionLine.isVisible).isTrue()
    assertThat(page.autoExcludeStatus.isVisible).isTrue()
    assertThat(page.autoExcludeStatus.text).isEqualTo("Microsoft Defender configuration script failed. Please look for \"WindowsDefenderChecker\" records in the log.")
    assertThat(page.suppressLine.isVisible).isTrue()
    assertThat(page.warningSuppressedMessage.isVisible).isFalse()
    Mockito.verify(checkerWrapperMock, Mockito.never()).ignoreStatusCheck(MockitoKt.any(), MockitoKt.eq(true))
  }

  private fun createPage(checkerWrapperMock: WindowsDefenderCheckerWrapper): WindowsDefenderWarningPage {
    val service = WindowsDefenderCheckService(projectRule.project) { checkerWrapperMock }
    service.checkRealTimeProtectionStatus()
    val mockUiData = MockUiData().apply {
      windowsDefenderWarningData =  service.warningData
    }
    val model = WarningsDataPageModelImpl(mockUiData)
    val mockHandlers = Mockito.mock(ViewActionHandlers::class.java)

    val pagesFactory = WarningsViewDetailPagesFactory(model, mockHandlers, projectRule.testRootDisposable)

    Mockito.`when`(mockHandlers.windowsDefenderPageHandler()).thenReturn(WindowsDefenderPageHandlerImpl(service))

    val page = pagesFactory.createDetailsPage(WarningsPageId.windowsDefenderWarning)
    return TreeWalker(page).descendants().filterIsInstance<WindowsDefenderWarningPage>().single()
  }
}


