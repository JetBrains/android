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

import com.android.repository.api.RemotePackage
import com.android.tools.idea.welcome.isWritable
import com.android.tools.idea.welcome.wizard.deprecated.InstallSummaryStepForm
import com.android.tools.idea.wizard.model.ModelWizardStep
import com.android.utils.HtmlBuilder
import com.intellij.openapi.util.text.StringUtil
import com.intellij.util.ui.StartupUiUtil.labelFont
import com.intellij.util.ui.UIUtil
import java.io.File
import java.util.function.Supplier
import javax.swing.JComponent

/**
 * Provides an explanation of changes the wizard will perform.
 */
class InstallSummaryStep(
  private val model: FirstRunModel,
  private val packagesProvider: Supplier<out Collection<RemotePackage>?>
) : ModelWizardStep<FirstRunModel>(model, "Verify Settings") {

  companion object {
    @JvmStatic
    fun getSdkFolderSection(location: File): Section {
      val text = if (isWritable(location.toPath()))
        location.absolutePath
      else
        location.absolutePath + " (read-only)"

      return Section("SDK Folder", text)
    }

    @JvmStatic
    fun getSetupTypeSection(type: String): Section {
      return Section("Setup Type", type)
    }

    @JvmStatic
    fun getPackagesSection(remotePackages: Collection<RemotePackage>) =
      Section("SDK Components to Download", getPackagesTable(remotePackages).orEmpty())

    private fun getPackagesTable(remotePackages: Collection<RemotePackage>): String? {
      if (remotePackages.isEmpty()) {
        return null
      }
      val sortedPackagesList = sortedSetOf(PackageInfoComparator()).apply {
        addAll(remotePackages)
      }
      return HtmlBuilder().apply {
        beginTable()
        sortedPackagesList.forEach {
          beginTableRow()
          addTableRow(
            it.displayName,
            "&nbsp;&nbsp;", // Adds some whitespace between name and size columns
            getSizeLabel(it.archive!!.complete.size)
          )
          endTableRow()
        }
        endTable()
      }.html
    }

    @JvmStatic
    fun getDownloadSizeSection(remotePackages: Collection<RemotePackage>): Section {
      // TODO: calculate patches?
      val downloadSize = remotePackages.map { it.archive!!.complete.size }.sum()
      return Section("Total Download Size", if (downloadSize == 0L) "" else getSizeLabel(downloadSize))
    }

    @JvmStatic
    fun generateSummaryHtml(sections: List<Section>): String {
      val builder = java.lang.StringBuilder("<html><head>")
      builder.append(UIUtil.getCssFontDeclaration(labelFont, UIUtil.getLabelForeground(), null, null)).append("</head><body>")

      for (section in sections) {
        if (!section.isEmpty) {
          builder.append(section.html)
        }
      }
      builder.append("</body></html>")

      return builder.toString()
    }
  }

  private val form = InstallSummaryStepForm()

  override fun getComponent(): JComponent = form.root

  override fun getPreferredFocusComponent(): JComponent = form.summaryText

  override fun onEntering() {
    super.onEntering()
    generateSummary()
  }

  private fun generateSummary() {
    val packages = packagesProvider.get()
    if (packages == null) {
      form.summaryText.text = "An error occurred while trying to compute required packages."
      return
    }
    val sections = listOf(
      getSetupTypeSection(StringUtil.capitalize(model.installationType.get().name.lowercase())),
      getSdkFolderSection(model.sdkLocation),
      getDownloadSizeSection(packages),
      getPackagesSection(packages)
    )

    form.summaryText.text = generateSummaryHtml(sections)
    form.summaryText.setCaretPosition(0) // Otherwise the scroll view will already be scrolled to the bottom when the UI is first shown
  }
}

/**
 * Summary section, consists of a header and a body text.
 */
class Section(private val title: String, private val text: String) {
  val isEmpty: Boolean get() = text.isBlank()
  val html: String get() = "<p><strong>$title:</strong><br>$text</p>"
}

/**
 * Sorts package info in descending size order. Packages with the same size are sorted alphabetically.
 */
private class PackageInfoComparator : Comparator<RemotePackage> {
  override fun compare(o1: RemotePackage?, o2: RemotePackage?): Int = when {
    o1 === o2 -> 0
    o1 == null -> -1
    o2 == null -> 1
    else -> o1.displayName.compareTo(o2.displayName)
  }
}
