/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.welcome.wizard

import com.android.tools.idea.observable.core.ObservableBool
import com.android.tools.idea.ui.wizard.StudioWizardStepPanel
import com.android.tools.idea.wizard.model.ModelWizardStep
import com.android.utils.HtmlBuilder
import com.intellij.openapi.util.SystemInfo
import com.intellij.ui.BrowserHyperlinkListener
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.uiDesigner.core.GridConstraints
import com.intellij.uiDesigner.core.GridLayoutManager
import com.intellij.util.ui.SwingHelper
import com.intellij.util.ui.UIUtil
import java.awt.Dimension
import javax.swing.JComponent
import javax.swing.JEditorPane
import javax.swing.JPanel

private const val KVM_DOCUMENTATION_URL = "http://developer.android.com/r/studio-ui/emulator-kvm-setup.html"

/**
 * Provides guidance for setting up Intel® HAXM on Linux platform.
 */
class LinuxHaxmInfoStep : ModelWizardStep.WithoutModel("Emulator Settings") {
  private var urlPane = SwingHelper.createHtmlViewer(true, null, null, null).apply {
    addHyperlinkListener(BrowserHyperlinkListener.INSTANCE)
    text = HtmlBuilder().apply {
      beginParagraph()
      addHtml("We have detected that your system can run the Android emulator in an accelerated performance mode.")
      endParagraph()
      beginParagraph()
      addHtml("Linux-based systems support virtual machine acceleration through the KVM (Kernel-based Virtual Machine) software package.")
      endParagraph()
      beginParagraph()
      addHtml("<p>Search for install instructions for your particular Linux configuration (")
      addLink("Android KVM Linux Installation", KVM_DOCUMENTATION_URL)
      addHtml(") that KVM is enabled for faster Android emulator performance.</p>")
      endParagraph()
    }.html
    SwingHelper.setHtml(this, text, UIUtil.getLabelForeground())
    background = UIUtil.getLabelBackground()
  }

  override fun getComponent(): JComponent = urlPane

  override fun canGoForward(): ObservableBool = ObservableBool.TRUE
}