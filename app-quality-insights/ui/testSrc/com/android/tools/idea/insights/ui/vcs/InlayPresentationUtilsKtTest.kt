package com.android.tools.idea.insights.ui.vcs

import com.android.testutils.MockitoKt.mock
import com.android.testutils.MockitoKt.whenever
import com.android.testutils.waitForCondition
import com.android.tools.adtui.swing.popup.FakeComponentPopup
import com.android.tools.adtui.swing.popup.FakeJBPopup
import com.android.tools.adtui.swing.popup.JBPopupRule
import com.android.tools.idea.testing.disposable
import com.android.tools.idea.testing.ui.flatten
import com.google.common.truth.Truth.assertThat
import com.intellij.codeInsight.hints.presentation.InlayPresentation
import com.intellij.codeInsight.hints.presentation.PresentationFactory
import com.intellij.execution.filters.TextConsoleBuilderFactory
import com.intellij.execution.impl.ConsoleViewImpl
import com.intellij.ide.HelpTooltip
import com.intellij.openapi.editor.colors.CodeInsightColors
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.ProjectRule
import com.intellij.testFramework.RunsInEdt
import java.awt.event.MouseEvent
import java.util.concurrent.TimeUnit
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain

@RunsInEdt
class InlayPresentationUtilsKtTest {

  private val popupRule = JBPopupRule()
  private val projectRule = ProjectRule()
  @get:Rule
  val rules: RuleChain = RuleChain.outerRule(projectRule).around(popupRule).around(EdtRule())

  private lateinit var console: ConsoleViewImpl
  private lateinit var inlay: InlayPresentation

  @Before
  fun setUp() {
    console = setUpConsole()
    inlay = setUpInlayWithTooltip("test tooltip")
  }

  private val editor
    get() = console.editor

  @Test
  fun `check tooltip`() {
    val point = editor.offsetToXY(0)

    val mockEvent: MouseEvent =
      mock<MouseEvent>().apply {
        whenever(this.component).thenReturn(console)
        whenever(this.point).thenReturn(point)
      }

    assertThat(getPopupPanel()).isNull()

    // Test a popup can show.
    inlay.mouseMoved(mockEvent, point)
    waitForCondition(2, TimeUnit.SECONDS) { getPopupPanel() != null }
    val popupPanel = getPopupPanel()
    assertThat(popupPanel).isNotNull()
    assertThat(
        popupPanel!!
          .contentPanel
          .flatten()
          .map { it.toString() }
          .filter { it.contains("text=<html><div>test tooltip</div></html>") }
      )
      .isNotEmpty()

    // Test this popup can hide.
    inlay.mouseExited()
    waitForCondition(2, TimeUnit.SECONDS) { !(popupPanel as FakeJBPopup<*>).isVisible }
  }

  private fun setUpConsole(): ConsoleViewImpl {
    return (TextConsoleBuilderFactory.getInstance().createBuilder(projectRule.project).console
        as ConsoleViewImpl)
      .apply {
        Disposer.register(projectRule.disposable, this)
        component
      }
  }

  private fun setUpInlayWithTooltip(text: String): InlayPresentation {
    val tooltip = HelpTooltip().apply { setDescription(text) }

    return InsightsTextInlayPresentation(
        text = "show diff",
        textAttributesKey = CodeInsightColors.HYPERLINK_ATTRIBUTES,
        isUnderline = true,
        editor
      )
      .withTooltip(tooltip, factory = PresentationFactory(editor as EditorImpl))
  }

  private fun getPopupPanel(): FakeComponentPopup? {
    if (popupRule.fakePopupFactory.popupCount == 0) return null

    val popup = popupRule.fakePopupFactory.getPopup<Any>(0)
    return (popup as FakeComponentPopup)
  }
}
