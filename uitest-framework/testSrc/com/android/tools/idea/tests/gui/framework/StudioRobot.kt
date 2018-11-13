/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.tests.gui.framework

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.ide.CopyPasteManager
import org.fest.swing.annotation.RunsInEDT
import org.fest.swing.core.Robot
import org.fest.swing.edt.GuiQuery
import org.fest.swing.exception.WaitTimedOutError
import org.fest.swing.timing.Wait
import java.awt.Toolkit
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.StringSelection
import java.awt.event.KeyEvent

/**
 * Wrapper class around a [Robot]. This class implements some logic
 * specific to Android Studio.
 */
class StudioRobot(val robot: Robot) : Robot by robot {

  companion object {
    private const val MAX_CHARS_TO_TYPE = 8
  }

  @Volatile
  private var copyPasteManager: CopyPasteManager? = null

  @RunsInEDT
  override fun enterText(text: String) {
    val cpm = copyPasteManager
    if (cpm == null) {
      robot.enterText(text)
      return
    }

    // cpm != null
    if (text.length <= MAX_CHARS_TO_TYPE) {
      typeText(text)
    } else {
      // Override paste implementation...
      Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(text), null)
      try {
        Wait.seconds(1)
          .expecting("Clipboard to contain contents: $text")
          .until {
            GuiQuery.getNonNull {
              cpm.areDataFlavorsAvailable(DataFlavor.stringFlavor)
              && cpm.getContents<String>(DataFlavor.stringFlavor) == text
            }
          }

        pressAndReleaseKey(KeyEvent.VK_V, Toolkit.getDefaultToolkit().menuShortcutKeyMask)
      }
      catch (dataNotAvailableYet: WaitTimedOutError) {
        // Can't rely on clipboard holding data. Enter text manually:
        robot.typeText(text)
      }
    }

  }

  /**
   * On Linux systems, retrieving a value from the system clipboard
   * shortly after a value was stored into the clipboard does not yield
   * the same value that was stored into the clipboard. The value stored
   * into the clipboard will appear asynchronously, so we need to
   * wait for the clipboard to contain the value we want it to have.
   */
  fun enableXwinClipboardWorkaround(copyPasteManager: CopyPasteManager) {
    this.copyPasteManager = copyPasteManager
  }

  fun disableXwinClipboardWorkaround() {
    this.copyPasteManager = null
  }
}