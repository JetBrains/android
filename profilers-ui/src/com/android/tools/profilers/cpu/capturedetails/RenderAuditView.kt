package com.android.tools.profilers.cpu.capturedetails

import com.android.tools.profilers.cpu.CpuProfilerStageView
import com.intellij.ui.components.JBScrollPane
import java.awt.GridLayout
import javax.swing.JComponent
import javax.swing.JPanel

class RenderAuditView(myStageView: CpuProfilerStageView, details: CaptureDetails.RenderAuditCaptureDetails) : CaptureDetailsView() {

  private var view: JComponent
  private val renderAuditModel = details.renderAuditModel

  init {
    val view = JPanel()
    view.layout = GridLayout(1, 3)
    this.view = JBScrollPane(view)
  }

  override fun getComponent(): JComponent {
    return view
  }

}