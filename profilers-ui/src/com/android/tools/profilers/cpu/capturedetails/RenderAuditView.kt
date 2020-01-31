package com.android.tools.profilers.cpu.capturedetails

import com.android.tools.adtui.model.Range
import com.android.tools.profilers.cpu.CpuProfilerStage
import com.android.tools.profilers.cpu.CpuProfilerStageView
import com.android.tools.profilers.cpu.audits.RenderAuditModel
import com.intellij.ui.components.JBScrollPane
import java.awt.GridLayout
import javax.swing.JComponent
import javax.swing.JPanel

class RenderAuditView(stageView: CpuProfilerStageView,
                      details: CaptureDetails.RenderAuditCaptureDetails) : CaptureDetailsView() {

  private var view: JComponent = createRenderAuditComponent()
  private val renderAuditModel = details.renderAuditModel
  private val stage: CpuProfilerStage = stageView.stage

  private fun createRenderAuditComponent(): JComponent {
    val view = JPanel()
    view.layout = GridLayout(1, 3)
    return JBScrollPane(view)
  }

  /**
   * Callback for the button which the user clicks to inspect a frame's thread. The inspection takes the
   * user to the TopDown tab.
   */
  private fun inspectFrameThreadInCaptureDetails(thread: RenderAuditModel.FrameThread, frameRange: Range) {

  }

  override fun getComponent(): JComponent {
    return view
  }

}