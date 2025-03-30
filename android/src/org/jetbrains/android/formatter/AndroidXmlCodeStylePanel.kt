// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.android.formatter

import com.intellij.application.options.CodeStyleAbstractPanel
import com.intellij.ide.highlighter.XmlFileType
import com.intellij.lang.Language
import com.intellij.openapi.application.ApplicationBundle
import com.intellij.openapi.editor.colors.EditorColorsScheme
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.util.NlsSafe
import com.intellij.psi.codeStyle.CodeStyleSettings
import com.intellij.ui.SimpleListCellRenderer
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.dsl.builder.AlignY
import com.intellij.ui.dsl.builder.Cell
import com.intellij.ui.dsl.builder.Panel
import com.intellij.ui.dsl.builder.bindItem
import com.intellij.ui.dsl.builder.bindSelected
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.dsl.builder.selected
import com.intellij.ui.dsl.builder.toNullableProperty
import com.intellij.util.ui.UIUtil
import org.jetbrains.android.util.AndroidBundle.message
import javax.swing.BorderFactory

@NlsSafe
private const val ANDROID_MANIFEST = "AndroidManifest.xml"

class AndroidXmlCodeStylePanel(language: Language, originalSettings: CodeStyleSettings)
  : CodeStyleAbstractPanel(language, null, originalSettings) {

  private val mySettings = settings.getCustomSettings(AndroidXmlCodeStyleSettings::class.java)

  override fun getRightMargin() = 0

  override fun createHighlighter(scheme: EditorColorsScheme) = null

  override fun getFileType(): XmlFileType = XmlFileType.INSTANCE

  override fun getPreviewText() = null

  override fun apply(settings: CodeStyleSettings) {
    // Apply is called on each panel change with this.settings, skip in this case
    if (settings === this.settings) return

    myPanel.apply()
    settings.copyFrom(this.settings)
  }

  override fun isModified(settings: CodeStyleSettings?) = myPanel.isModified()

  override fun resetImpl(settings: CodeStyleSettings) {
    myPanel.reset()
  }

  override fun getPanel(): DialogPanel = myPanel

  private val myPanel = panel {
    lateinit var myUseCustomSettings: Cell<JBCheckBox>
    row {
      myUseCustomSettings = checkBox(message("checkbox.use.custom.formatting.settings.for.android.xml.files"))
          .bindSelected(mySettings::USE_CUSTOM_SETTINGS)
    }

    row {
      panel {
        group(ANDROID_MANIFEST) {
          androidXmlWrapStyleComboBox(mySettings.MANIFEST_SETTINGS)
          androidXmlStyleCommonCheckBoxes(mySettings.MANIFEST_SETTINGS)
          row {
            checkBox(message("checkbox.group.tags.with.the.same.name"))
              .bindSelected(mySettings.MANIFEST_SETTINGS::GROUP_TAGS_WITH_SAME_NAME)
          }
        }

        group(message("group.value.resource.files.selectors.title")) {
          androidXmlWrapStyleComboBox(mySettings.VALUE_RESOURCE_FILE_SETTINGS)
          row {
            checkBox(message("checkbox.insert.line.breaks.around.style.declaration"))
              .bindSelected(mySettings.VALUE_RESOURCE_FILE_SETTINGS::INSERT_LINE_BREAKS_AROUND_STYLE)
          }
        }
      }.align(AlignY.TOP)

      panel {
        group(message("group.layout.files.title")) {
          androidXmlWrapStyleComboBox(mySettings.LAYOUT_SETTINGS)
          androidXmlStyleCommonCheckBoxes(mySettings.LAYOUT_SETTINGS)
          row {
            checkBox(message("checkbox.insert.blank.line.before.tag"))
              .bindSelected(mySettings.LAYOUT_SETTINGS::INSERT_BLANK_LINE_BEFORE_TAG)
          }
        }

        group(message("group.other.xml.resource.files.title")) {
          androidXmlWrapStyleComboBox(mySettings.OTHER_SETTINGS)
          androidXmlStyleCommonCheckBoxes(mySettings.OTHER_SETTINGS)
        }
      }.align(AlignY.TOP)
    }.enabledIf(myUseCustomSettings.selected)
  }.apply {
    border = BorderFactory.createEmptyBorder(UIUtil.DEFAULT_VGAP, UIUtil.DEFAULT_HGAP, UIUtil.DEFAULT_VGAP, UIUtil.DEFAULT_HGAP)
  }

  private fun Panel.androidXmlWrapStyleComboBox(androidSettings: AndroidXmlCodeStyleSettings.MySettings) {
    row(ApplicationBundle.message("label.wrap.attributes")) {
      comboBox(
        items = CodeStyleSettings.WrapStyle.values().map { it.id },
        renderer = SimpleListCellRenderer.create(String()) { CodeStyleSettings.WrapStyle.forWrapping(it).presentableText }
        //TODO replace after IDEA-324576
        //renderer = simpleListCellRenderer { it?.let(CodeStyleSettings.WrapStyle::forWrapping)?.presentableText }
      )
        .bindItem(androidSettings::WRAP_ATTRIBUTES.toNullableProperty(CodeStyleSettings.WrapStyle.DO_NOT_WRAP.id))
    }
  }

  private fun Panel.androidXmlStyleCommonCheckBoxes(androidSettings: AndroidXmlCodeStyleSettings.MySettings) {
    lateinit var myInsertLineBreakBeforeFirstAttributeCheckBox: Cell<JBCheckBox>
    row {
      myInsertLineBreakBeforeFirstAttributeCheckBox = checkBox(message("checkbox.insert.line.break.before.first.attribute"))
        .bindSelected(androidSettings::INSERT_LINE_BREAK_BEFORE_FIRST_ATTRIBUTE)
    }
    indent {
      row {
        checkBox(message("checkbox.include.namespace.declarations"))
          .enabledIf(myInsertLineBreakBeforeFirstAttributeCheckBox.selected)
          .bindSelected(androidSettings::INSERT_LINE_BREAK_BEFORE_NAMESPACE_DECLARATION)
      }
    }
    row {
      checkBox(message("checkbox.insert.line.break.after.last.attribute"))
        .bindSelected(androidSettings::INSERT_LINE_BREAK_AFTER_LAST_ATTRIBUTE)
    }
  }

}