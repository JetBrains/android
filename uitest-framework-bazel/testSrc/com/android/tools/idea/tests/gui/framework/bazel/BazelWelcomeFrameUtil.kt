package com.android.tools.idea.tests.gui.framework.bazel

import com.android.tools.idea.tests.gui.framework.fixture.ActionLinkFixture
import com.android.tools.idea.tests.gui.framework.fixture.IdeSettingsDialogFixture
import com.android.tools.idea.tests.gui.framework.fixture.WelcomeFrameFixture
import com.intellij.openapi.util.Ref
import com.intellij.ui.popup.PopupFactoryImpl
import org.fest.swing.core.GenericTypeMatcher
import org.fest.swing.exception.ComponentLookupException
import org.fest.swing.fixture.JListFixture
import org.fest.swing.timing.Wait
import javax.swing.JLabel
import javax.swing.JList

fun WelcomeFrameFixture.importBazelProject(): WelcomeFrameFixture {
  // The actionId is from //tools/vendor/google3/blaze/third_party/intellij/bazel/plugin/java/src/META-INF/java-contents.xml
  ActionLinkFixture.findByActionId("Blaze.ImportProject2", robot(), target()).click()
  return this
}

/**
 * Open "Default Settings" dialog by clicking on Configure -> Settings on welcome frame.
 */
fun WelcomeFrameFixture.openSettings(): IdeSettingsDialogFixture {
  // The "Configure" button is actually a JLabel that has an ActionLink, however the ActionId of the link doesn't seem
  // to be registered correctly and the FEST bot cannot detect it.  We have to match by finding a JLabel with text "Configure".
  Wait.seconds(1).expecting("Configure Label in welcome frame to be visible")
    .until {
      try {
        val found = robot().finder().find(
            object : GenericTypeMatcher<JLabel>(JLabel::class.java) {
              override fun isMatching(label: JLabel): Boolean {
                return (label.text == "Configure")
              }
            }
        )
        robot().click(found)
        true
      } catch (e: ComponentLookupException) {
        false
      }
    }

  // The menu that pops up after clicking configure is a JList of ActionItems. The robot cannot look into the JList, so we need to
  // find the JList and then make the selection through JListFixture.
  // This method assumes that the "Configure" popup is the only JList in the WelcomeFrame that has a "Settings" item.
  val configurePopupListRef = Ref<JList<PopupFactoryImpl.ActionItem>>()
  Wait.seconds(1).expecting("Configure popup list to be visible")
    .until {
      try {
        val found = robot().finder().find(
            object : GenericTypeMatcher<JList<PopupFactoryImpl.ActionItem>>(JList<PopupFactoryImpl.ActionItem>().javaClass) {
              override fun isMatching(list: JList<PopupFactoryImpl.ActionItem>): Boolean {
                for (i in 0 until list.model.size) {
                  if (list.model.getElementAt(i).text == "Settings") {
                    return true
                  }
                }
                return false
              }
            }
        )
        configurePopupListRef.set(found)
        true
      } catch (e: ComponentLookupException) {
        false
      }
    }
  val listFixture = JListFixture(robot(), configurePopupListRef.get())
  listFixture.clickItem("Settings")

  return IdeSettingsDialogFixture.find(robot())
}
