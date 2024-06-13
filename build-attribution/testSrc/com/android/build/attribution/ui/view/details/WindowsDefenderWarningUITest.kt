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
import com.android.build.attribution.ui.model.WarningsDataPageModelImpl
import com.android.build.attribution.ui.model.WarningsPageId
import com.android.build.attribution.ui.model.WarningsTreeNode
import com.android.build.attribution.ui.model.WindowsDefenderWarningNodeDescriptor
import com.android.build.attribution.ui.view.ViewActionHandlers
import com.android.testutils.MockitoKt
import com.android.tools.adtui.TreeWalker
import com.android.tools.adtui.swing.FakeUi
import com.android.tools.idea.testing.AndroidProjectRule
import com.google.common.truth.Truth.assertThat
import com.android.build.diagnostic.WindowsDefenderCheckService
import com.android.build.attribution.ui.controllers.WindowsDefenderPageHandlerImpl
import com.intellij.diagnostic.WindowsDefenderChecker
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

  private val checkerMock = Mockito.mock(WindowsDefenderChecker::class.java).apply {
    Mockito.`when`(this.isStatusCheckIgnored(MockitoKt.any())).thenReturn(false)
    Mockito.`when`(this.isRealTimeProtectionEnabled).thenReturn(true)
    Mockito.`when`(this.getPathsToExclude(MockitoKt.any())).thenReturn(listOfPaths)
  }

  @Test
  fun testWarningTreeNodeCreatedByTheModelWhenShouldBeShown() {
    val mockUiData = MockUiData().apply {
      windowsDefenderWarningData =  WindowsDefenderCheckService.WindowsDefenderWarningData(true, emptyList())
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
    val page = createPage(checkerMock)
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
  fun testUIResponseOnSuppressAction() {
    val page = createPage(checkerMock)
    page.size = Dimension(600, 400)
    val ui = FakeUi(page)
    ui.layoutAndDispatchEvents()

    ui.clickOn(page.suppressWarningLink)
    ui.layoutAndDispatchEvents()

    assertThat(page.autoExclusionLine.isVisible).isTrue()
    assertThat(page.autoExcludeStatus.isVisible).isFalse()
    assertThat(page.suppressLine.isVisible).isTrue()
    assertThat(page.warningSuppressedMessage.isVisible).isTrue()
    Mockito.verify(checkerMock, Mockito.times(1)).ignoreStatusCheck(MockitoKt.any(), MockitoKt.eq(true))
  }

  @Test
  fun testUIResponseOnExcludeActionSuccess() {
    val page = createPage(checkerMock)
    page.size = Dimension(600, 400)
    val ui = FakeUi(page)
    ui.layoutAndDispatchEvents()

    // Exclusion script runs in Backgroundable task that in tests runs synchronously
    Mockito.`when`(checkerMock.excludeProjectPaths(MockitoKt.any(), MockitoKt.eq(listOfPaths))).then {
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
    Mockito.verify(checkerMock, Mockito.times(1)).ignoreStatusCheck(MockitoKt.any(), MockitoKt.eq(true))
  }

  @Test
  fun testUIResponseOnExcludeActionFailure() {
    val page = createPage(checkerMock)
    page.size = Dimension(600, 400)
    val ui = FakeUi(page)
    ui.layoutAndDispatchEvents()

    // Exclusion script runs in Backgroundable task that in tests runs synchronously
    Mockito.`when`(checkerMock.excludeProjectPaths(MockitoKt.any(), MockitoKt.eq(listOfPaths))).then {
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
    Mockito.verify(checkerMock, Mockito.never()).ignoreStatusCheck(MockitoKt.any(), MockitoKt.eq(true))
  }

  private fun createPage(checkerMock: WindowsDefenderChecker): WindowsDefenderWarningPage {
    val service = WindowsDefenderCheckService(projectRule.project) { checkerMock }
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


