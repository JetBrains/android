/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.idea.stats

import com.google.common.base.Predicates
import com.intellij.ide.gdpr.Consent
import com.intellij.ide.gdpr.ConsentOptions
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.util.text.StringUtil
import com.intellij.ui.AppUIUtil
import com.intellij.ui.scale.JBUIScale
import java.awt.Color
import java.awt.EventQueue
import java.lang.reflect.InvocationTargetException
import javax.swing.Action
import javax.swing.Box
import javax.swing.JCheckBox
import javax.swing.JComponent
import javax.swing.JTextArea

class ConsentDialog(private val consent: Consent) : DialogWrapper(null) {
  private val checkBox = JCheckBox()
  override fun createActions(): Array<Action> {
    return arrayOf(okAction)
  }

  // TODO: update visuals
  val content: JComponent = Box.createVerticalBox().apply {
    background = Color.pink
    foreground = Color.orange

    val textArea = JTextArea()
    textArea.text = consent.text;
    textArea.lineWrap = true;
    textArea.wrapStyleWord = true
    add(textArea)

    checkBox.text = "I agree to the conditions."
    add(checkBox)
  }

  init {
    isAutoAdjustable = true
    isResizable = true
    title = "Data Sharing"
    isModal = true
    checkBox.isSelected = true
    init()
  }

  override fun createCenterPanel(): JComponent = content

  override fun doOKAction() {
    val result = listOf<Consent>(consent.derive(checkBox.isSelected))
    AppUIUtil.saveConsents(result)
    super.doOKAction()
  }

  companion object {
    @JvmStatic
    fun showConsentDialogIfNeeded() {
      val options = ConsentOptions.getInstance()
      val consentsToShow = options.getConsents(Predicates.alwaysTrue())
      if (!consentsToShow.second) {
        return
      }

      val list = consentsToShow.first

      if (list.size != 1) {
        return
      }

      val dialog = ConsentDialog(list[0])
      dialog.isModal = true

      if (EventQueue.isDispatchThread()) {
        dialog.show()
      }
      else {
        try {
          EventQueue.invokeAndWait { dialog.show() }
        }
        catch (e: InterruptedException) {
        }
        catch (e: InvocationTargetException) {
        }
      }
    }
  }
}
