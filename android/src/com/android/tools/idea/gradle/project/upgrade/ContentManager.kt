package com.android.tools.idea.gradle.project.upgrade

import com.android.ide.common.repository.GradleVersion
import com.android.tools.idea.gradle.plugin.AndroidPluginInfo
import com.android.tools.idea.gradle.plugin.LatestKnownPluginVersionProvider
import com.android.tools.idea.gradle.project.upgrade.AgpUpgradeComponentNecessity.MANDATORY_CODEPENDENT
import com.android.tools.idea.gradle.project.upgrade.AgpUpgradeComponentNecessity.MANDATORY_INDEPENDENT
import com.android.tools.idea.gradle.project.upgrade.AgpUpgradeComponentNecessity.OPTIONAL_CODEPENDENT
import com.android.tools.idea.gradle.project.upgrade.AgpUpgradeComponentNecessity.OPTIONAL_INDEPENDENT
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.RegisterToolWindowTask
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanel
import com.intellij.ui.components.JBTextArea
import com.intellij.ui.components.JBTextField
import com.intellij.ui.content.ContentFactory
import com.intellij.util.ui.components.JBComponent
import java.awt.BorderLayout
import java.awt.Component.LEFT_ALIGNMENT
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JTextField
import javax.swing.text.JTextComponent

// "Model" here loosely in the sense of Model-View-Controller
internal class ToolWindowModel(var processor: AgpUpgradeRefactoringProcessor) {
}

class ContentManager(val project: Project) {
  init {
    ToolWindowManager.getInstance(project).registerToolWindow(RegisterToolWindowTask.closable("Upgrade Assistant", icons.GradleIcons.GradleFile))
  }

  fun showContent() {
    val current = AndroidPluginInfo.find(project)?.pluginVersion ?: return
    val new = GradleVersion.parse(LatestKnownPluginVersionProvider.INSTANCE.get())
    val toolWindow = ToolWindowManager.getInstance(project).getToolWindow("Upgrade Assistant")!!
    toolWindow.contentManager.removeAllContents(true)
    val processor = AgpUpgradeRefactoringProcessor(project, current, new)
    val model = ToolWindowModel(processor)
    //val component = JBLabel("Hello, Upgrade")
    val component = JBPanel<JBPanel<*>>().apply {
      layout = BorderLayout()
      val label = makeCenterComponent(model) as JTextComponent
      val textField = makeTopComponent(model, label)
      add(textField, BorderLayout.NORTH)
      add(label, BorderLayout.CENTER)
    }
    val content = ContentFactory.SERVICE.getInstance().createContent(component, "Hello, Upgrade!", true)
    content.isPinned = true
    toolWindow.contentManager.addContent(content)
    toolWindow.show()
  }

  internal fun makeTopComponent(model: ToolWindowModel, label: JTextComponent): JComponent {
    val panel = JBPanel<JBPanel<*>>()
    panel.alignmentX = LEFT_ALIGNMENT // obviously this doesn't work to left-align the panel itself, *sigh*
    panel.add(JBLabel("Upgrading from ${model.processor.current} to"))
    val textField = JBTextField("${model.processor.new}")
    textField.addActionListener  { _ ->
      val text = textField.text
      model.processor = AgpUpgradeRefactoringProcessor(project, model.processor.current, GradleVersion.parse(text))
      refreshLabel(model, label)
    }
    panel.add(textField)
    return panel
  }

  internal fun makeCenterComponent(model: ToolWindowModel): JComponent {
    val label = JBTextArea()
    label.isEnabled = false
    refreshLabel(model, label)
    return label
  }

  private fun refreshLabel(model: ToolWindowModel, label: JTextComponent) {
    fun AgpUpgradeRefactoringProcessor.components(necessity: AgpUpgradeComponentNecessity): List<AgpUpgradeComponentRefactoringProcessor> {
      return (this.componentRefactoringProcessors + this.classpathRefactoringProcessor)
        .filter { it.isEnabled }
        .filter { it.necessity() == necessity }
        .filter { !it.isAlwaysNoOpForProject }
    }

    val sb = StringBuilder()
    sb.append("Upgrade details:\n")
    val processor = model.processor
    processor.ensureParsedModels()
    sb.append("\nPre-upgrade steps:\n")
    processor.components(MANDATORY_INDEPENDENT).forEach { sb.append("- ${it.commandName}\n") }
    sb.append("\nUpgrade steps:\n")
    processor.components(MANDATORY_CODEPENDENT).forEach { sb.append("- ${it.commandName}\n") }
    sb.append("\nPost-upgrade steps:\n")
    processor.components(OPTIONAL_CODEPENDENT).forEach { sb.append("- ${it.commandName}\n") }
    sb.append("\nOptional steps:\n")
    processor.components(OPTIONAL_INDEPENDENT).forEach { sb.append("- ${ it.commandName}") }
    label.text = sb.toString()
  }

}