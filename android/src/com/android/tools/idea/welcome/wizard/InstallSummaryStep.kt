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
import com.android.tools.idea.gradle.ui.SdkUiStrings.JDK_LOCATION_WARNING_URL
import com.android.tools.idea.sdk.IdeSdks.isSameAsJavaHomeJdk
import com.android.tools.idea.welcome.isWritable
import com.android.tools.idea.wizard.model.ModelWizardStep
import com.android.utils.HtmlBuilder
import com.intellij.ide.BrowserUtil
import com.intellij.ui.layout.panel
import com.intellij.uiDesigner.core.Spacer
import com.intellij.util.ui.HTMLEditorKitBuilder
import com.intellij.util.ui.StartupUiUtil
import com.intellij.util.ui.UIUtil
import java.io.File
import java.util.function.Supplier
import javax.swing.JComponent
import javax.swing.JTextPane
import javax.swing.event.HyperlinkEvent

/**
 * Provides an explanation of changes the wizard will perform.
 */
class InstallSummaryStep(
  private val model: FirstRunModel,
  private val packagesProvider: Supplier<out Collection<RemotePackage>?>
) : ModelWizardStep<FirstRunModel>(model, "Verify Settings") {
  private val summaryText = JTextPane().apply {
    isEditable = false
    editorKit = HTMLEditorKitBuilder.simple()

    // There is no need to add whitespace on the top
    addHyperlinkListener {
      if (it.eventType == HyperlinkEvent.EventType.ACTIVATED && it.url != null) {
        BrowserUtil.browse(it.url)
      }
    }
    // TODO(qumeric) set "label for"?
  }

  private val panel = panel {
    row {
      label("If you want to review or change any of your installation settings, click Previous.")
    }
    row {
      Spacer()()
    }
    row {
      label("Current Settings:")
    }
    row {
      summaryText()
    }
  }

  private val jdkFolderSection: Section
    get() {
      var jdkLocationText = model.jdkLocation.toAbsolutePath().toString()

      if (!isSameAsJavaHomeJdk(model.jdkLocation)) {
        jdkLocationText += " (<b>Note:</b> Gradle may be using JAVA_HOME when invoked from command line. " +
                           "<a href=\"$JDK_LOCATION_WARNING_URL\">More info...</a>)"
      }
      return Section("JDK Location", jdkLocationText)
    }
  private val sdkFolderSection: Section
    get() {
      val suffix = " (read-only)".takeUnless { (isWritable(sdkDirectory.toPath())) } ?: ""
      return Section("SDK Folder", sdkDirectory.absolutePath + suffix)
    }

  private val sdkDirectory: File
    get() = model.sdkLocation

  private val setupTypeSection: Section
    get() {
      val setupType = model.installationType.get().name
      return Section("Setup Type", setupType)
    }

  override fun getComponent(): JComponent = panel

  override fun getPreferredFocusComponent(): JComponent = summaryText

  override fun onEntering() {
    val packages = packagesProvider.get()
    if (packages == null) {
      summaryText.text = "An error occurred while trying to compute required packages."
      return
    }
    val sections = listOf(
      setupTypeSection, sdkFolderSection, jdkFolderSection, getDownloadSizeSection(packages), getPackagesSection(packages)
    )

    // TODO(qumeric): change to HtmlBuilder/similar.
    val builder = StringBuilder("<html><head>")
    builder.append(UIUtil.getCssFontDeclaration(StartupUiUtil.labelFont, UIUtil.getLabelForeground(), null, null))
      .append("</head><body>")
    sections.filterNot(Section::isEmpty).forEach {
      builder.append(it.html)
    }
    builder.append("</body></html>")
    summaryText.text = builder.toString()
    // TODO invokeUpdate<Any>(null)
  }
}

private fun getPackagesSection(remotePackages: Collection<RemotePackage>) =
  Section("SDK Components to Download", getPackagesTable(remotePackages).orEmpty())

// TODO(qumeric): make private
fun getPackagesTable(remotePackages: Collection<RemotePackage>): String? {
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

private fun getDownloadSizeSection(remotePackages: Collection<RemotePackage>): Section {
  // TODO: calculate patches?
  val downloadSize = remotePackages.map { it.archive!!.complete.size }.sum()
  return Section("Total Download Size", if (downloadSize == 0L) "" else getSizeLabel(downloadSize))
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
