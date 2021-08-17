package com.android.tools.idea.tests.gui.framework.fixture

import com.android.tools.idea.tests.gui.framework.matcher.Matchers
import com.intellij.ide.ui.newItemPopup.NewItemSimplePopupPanel
import com.intellij.ui.components.fields.ExtendableTextField
import org.fest.swing.core.Robot
import java.awt.event.KeyEvent

class NewFilePopupFixture(private val robot: Robot, private val popup: NewItemSimplePopupPanel) {

  private val textField = robot.finder().find(popup, Matchers.byType(ExtendableTextField::class.java))

  fun setFilePath(path: String): NewFilePopupFixture {
    robot.focus(textField)
    robot.enterText(path)
    return this
  }

  fun pressEnter() {
    robot.focus(textField)
    robot.pressAndReleaseKey(KeyEvent.VK_ENTER)
  }

  companion object {
    fun find(ideFixture: IdeFrameFixture): NewFilePopupFixture {
      return NewFilePopupFixture(ideFixture.robot(),
                                 ideFixture.robot().finder().find(ideFixture.target(),
                                                                  Matchers.byType(NewItemSimplePopupPanel::class.java)))
    }
  }
}