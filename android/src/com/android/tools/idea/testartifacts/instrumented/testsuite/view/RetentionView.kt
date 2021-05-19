/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.testartifacts.instrumented.testsuite.view

import com.android.SdkConstants
import com.android.annotations.concurrency.AnyThread
import com.android.emulator.snapshot.SnapshotOuterClass
import com.android.repository.api.ProgressIndicator
import com.android.sdklib.repository.AndroidSdkHandler
import com.android.tools.idea.concurrency.AndroidExecutors.Companion.getInstance
import com.android.tools.idea.sdk.IdeSdks
import com.android.tools.idea.sdk.progress.StudioLoggerProgressIndicator
import com.android.tools.idea.testartifacts.instrumented.DEVICE_NAME_KEY
import com.android.tools.idea.testartifacts.instrumented.EMULATOR_SNAPSHOT_FILE_KEY
import com.android.tools.idea.testartifacts.instrumented.EMULATOR_SNAPSHOT_ID_KEY
import com.android.tools.idea.testartifacts.instrumented.LOAD_RETENTION_ACTION_ID
import com.android.tools.idea.testartifacts.instrumented.PACKAGE_NAME_KEY
import com.android.tools.idea.testartifacts.instrumented.RETENTION_AUTO_CONNECT_DEBUGGER_KEY
import com.android.tools.idea.testartifacts.instrumented.RETENTION_ON_FINISH_KEY
import com.android.tools.idea.testartifacts.instrumented.testsuite.model.AndroidDevice
import com.google.common.annotations.VisibleForTesting
import com.intellij.icons.AllIcons
import com.intellij.ide.DataManager
import com.intellij.ide.HelpTooltip
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataProvider
import com.intellij.openapi.diagnostic.Logger
import com.intellij.ui.layout.panel
import com.intellij.uiDesigner.core.GridConstraints
import com.intellij.uiDesigner.core.GridLayoutManager
import com.intellij.util.ui.SwingHelper
import com.intellij.util.ui.UIUtil
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream
import org.apache.commons.compress.utils.FileNameUtils
import org.apache.commons.lang.StringEscapeUtils
import org.ini4j.Config
import org.ini4j.Ini
import java.awt.Desktop
import java.awt.Image
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import java.awt.image.ImageObserver
import java.io.File
import java.io.FileInputStream
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import java.nio.charset.Charset
import java.nio.file.Files
import java.nio.file.attribute.BasicFileAttributes
import java.text.DateFormat
import java.util.Date
import java.util.Locale
import java.util.regex.Pattern
import javax.imageio.ImageIO
import javax.swing.BorderFactory
import javax.swing.ImageIcon
import javax.swing.JButton
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.event.HyperlinkEvent

private const val INI_GLOBAL_SECTION_NAME = "global"
private const val BUILD_PROP_FILE_NAME = "build.prop"

// TODO(yahan@) rework this view when we have the UI mock
/**
 * Shows the Android Test Retention artifacts
 */
class RetentionView(private val androidSdkHandler: AndroidSdkHandler
                    = AndroidSdkHandler.getInstance(IdeSdks.getInstance().androidSdkPath),
                    private val progressIndicator: ProgressIndicator
                    = StudioLoggerProgressIndicator(RetentionView::class.java)) {
  private inner class RetentionPanel : JPanel(), DataProvider {
    private val retentionArtifactRegex = ".*-(failure[0-9]+).tar(.gz)?"
    private val retentionArtifactPattern = Pattern.compile(retentionArtifactRegex)
    private var snapshotFile: File? = null
    private var snapshotId = ""
    var androidDevice: AndroidDevice? = null
    var snapshotProto: SnapshotOuterClass.Snapshot? = null

    fun setSnapshotFile(snapshotFile: File?) {
      this.snapshotFile = snapshotFile
      snapshotId = ""
      if (snapshotFile == null) {
        return
      }
      val matcher = retentionArtifactPattern.matcher(snapshotFile.name)
      if (matcher.find()) {
        snapshotId = matcher.group(1)
      }
    }

    fun getSnapshotFile(): File? {
      return snapshotFile
    }

    override fun getData(dataId: String): Any? {
      when {
        dataId === EMULATOR_SNAPSHOT_ID_KEY.name -> {
          return snapshotId
        }
        dataId === EMULATOR_SNAPSHOT_FILE_KEY.name -> {
          return snapshotFile
        }
        dataId === PACKAGE_NAME_KEY.name -> {
          return packageName
        }
        dataId === RETENTION_AUTO_CONNECT_DEBUGGER_KEY.name -> {
          return true
        }
        dataId === RETENTION_ON_FINISH_KEY.name -> {
          return Runnable { myRetentionDebugButton.isEnabled = true }
        }
        dataId === DEVICE_NAME_KEY.name -> {
          return androidDevice!!.deviceName
        }
        else -> return null
      }
    }
  }

  private var packageName = ""
  val myRetentionDebugButton: JButton = JButton("Start retention debug", AllIcons.Actions.Execute).apply {
    addActionListener {
      isEnabled = false
      val dataContext = DataManager.getInstance().getDataContext(myRetentionPanel)
      ActionManager.getInstance().getAction(LOAD_RETENTION_ACTION_ID).actionPerformed(
        AnActionEvent.createFromDataContext("", null, dataContext))
    }
    isBorderPainted = false
  }
  val myRetentionHelperLabel = JLabel(AllIcons.General.ContextHelp).apply {
    HelpTooltip().setDescription(
      "Debug from the retentio state will load the snapshot at the point of failure, and attach to the debugger."
    ).installOn(this)
  }
  val myInfoText = SwingHelper.createHtmlViewer(true, null, null, null).apply {
    alignmentX = 0.0f
    alignmentY = 0.0f
    isEditable = false
    addHyperlinkListener {
      LOG.warn("opening ${it.url.path}")
      if (it.eventType == HyperlinkEvent.EventType.ACTIVATED) {
        Desktop.getDesktop().browse(it.url.toURI())
      }
    }
  }
  private val myImageLabel = JLabel()
  private val myInnerPanel = panel {
    row {
      myImageLabel()
      myInfoText(pushX, growY)
    }
  }.apply {
    background = UIUtil.getTableBackground()
    border =
      BorderFactory.createCompoundBorder(
        BorderFactory.createLineBorder(UIUtil.CONTRAST_BORDER_COLOR),
        BorderFactory.createEmptyBorder(10, 20, 10, 20)
      )
  }
  private val myLayoutPanel = panel {
    row {
      cell {
        myRetentionDebugButton()
        myRetentionHelperLabel()
      }
    }
    row {
      scrollPane(myInnerPanel)
    }
  }
  private val myRetentionPanel = RetentionPanel().apply {
    addComponentListener(object : ComponentAdapter() {
      override fun componentResized(e: ComponentEvent) {
        if (image != null) {
          updateSnapshotImage(image!!, image!!.getWidth(observer), image!!.getHeight(observer))
        }
      }
    })
    layout = GridLayoutManager(1, 1)
    add(myLayoutPanel, GridConstraints().apply {
      fill = GridConstraints.FILL_HORIZONTAL
    })
  }

  @VisibleForTesting
  var image: Image? = null
  private val observer = ImageObserver { img, infoflags, x, y, width, height ->
    if (infoflags and ImageObserver.WIDTH == 0 || infoflags and ImageObserver.HEIGHT == 0) {
      return@ImageObserver true
    }
    updateSnapshotImage(image!!, width, height)
    false
  }

  private var testStartTime: Long? = null
  /**
   * Returns the root panel.
   */
  val rootPanel: JPanel
    get() = myRetentionPanel

  fun setPackageName(packageName: String) {
    this.packageName = packageName
  }

  @AnyThread
  private fun updateSnapshotImage(image: Image, imageWidth: Int, imageHeight: Int) {
    val rootWidth = rootPanel.width
    if (rootWidth == 0 || imageHeight <= 0 || imageWidth <= 0) {
      return
    }
    val targetWidth = rootPanel.width / 4
    val targetHeight = targetWidth * imageHeight / imageWidth
    val newImage = image.getScaledInstance(targetWidth, targetHeight, Image.SCALE_DEFAULT)
    myImageLabel.icon = ImageIcon(newImage)
    myInnerPanel.revalidate()
  }

  fun setSnapshotFile(snapshotFile: File?) {
    myRetentionPanel.setSnapshotFile(
      snapshotFile)
    myRetentionDebugButton.isEnabled = false
    myRetentionDebugButton.toolTipText = null
    updateInfoText()
    myImageLabel.icon = null
    image = null
    if (snapshotFile != null) {
      getInstance().ioThreadExecutor.execute {
        try {
          var inputStream: InputStream = FileInputStream(
            snapshotFile)
          if (FileNameUtils.getExtension(
              snapshotFile.name.toLowerCase(
                Locale.getDefault())) == "gz") {
            inputStream = GzipCompressorInputStream(
              inputStream)
          }
          val tarInputStream = TarArchiveInputStream(
            inputStream)
          var entry: TarArchiveEntry?
          var snapshotProto: SnapshotOuterClass.Snapshot? = null
          var hardwareIni: Ini? = null
          while (tarInputStream.nextTarEntry.also { entry = it } != null) {
            if (entry!!.name == "screenshot.png") {
              val imageStream = ImageIO.read(
                tarInputStream)
              image = ImageIcon(imageStream).image
              updateSnapshotImage(image!!, image!!.getWidth(observer),
                                  image!!.getHeight(observer))
            } else if (entry!!.name == "snapshot.pb") {
              snapshotProto = SnapshotOuterClass.Snapshot.parseFrom(tarInputStream)
            } else if (entry!!.name == "hardware.ini") {
              hardwareIni = Ini().apply {
                config = Config.getGlobal().apply {
                  isGlobalSection = true
                  globalSectionName = INI_GLOBAL_SECTION_NAME
                  fileEncoding = Charset.defaultCharset()
                }
                load(object : FilterInputStream(tarInputStream) {
                  override fun close() {
                    // Ini4J will close the stream, which breaks our tar stream reader.
                    // So we override the stream with a no-op close.
                  }
                })
              }
            }
          }
          myRetentionPanel.snapshotProto = snapshotProto
          if (snapshotProto == null) {
            myRetentionDebugButton.toolTipText = "Snapshot protobuf broken, expected path: ${snapshotFile.name}:snapshot.pb"
            return@execute
          }
          val emulatorPackage = androidSdkHandler.getLocalPackage(SdkConstants.FD_EMULATOR, progressIndicator)
          if (emulatorPackage == null) {
            myRetentionDebugButton.toolTipText = "Missing emulator executables. Please download the emulator from SDK manager."
            return@execute
          }
          // TODO(b/166826352): validate snapshot version
          if (hardwareIni == null) {
            myRetentionDebugButton.toolTipText = "Missing snapshot hardware.ini, expected path: ${snapshotFile.name}:hardware.ini"
            return@execute
          }
          isSystemImageCompatible(hardwareIni, snapshotProto.systemImageBuildId, androidSdkHandler).also {
            if (!it.compatible) {
              myRetentionDebugButton.toolTipText = "Snapshot system image incompatible, reason: ${it.reason}"
              return@execute
            }
          }
          myRetentionDebugButton.isEnabled = true
        }
        catch (e: IOException) {
          LOG.warn(
            "Failed to parse retention snapshot", e)
        }
      }
    }
  }

  private fun updateInfoText() {
    var text = ""
    text += "<html>"
    text += "<b>Test details</b><br>"
    if (testStartTime != null && testStartTime != 0L) {
      text += "Test failed: ${testStartTime?.formatTime()?.escapeHtml()}<br>"
    }
    val snapshotFile = myRetentionPanel.getSnapshotFile()
    if (snapshotFile != null) {
      text += "<br><b>Test snapshot</b><br>"
      text += "${snapshotFile.name.escapeHtml()}<br>"
      text += "size: ${snapshotFile.length() / 1024 / 1024} MB<br>"
      if (snapshotFile.parent != null) {
        text += "<a href=\"file:///${snapshotFile.parent.escapeHtml()}\">View file</a><br>"
      }
      try {
        val attribs = Files.readAttributes(snapshotFile.toPath(), BasicFileAttributes::class.java)
        val formatted: String = attribs.creationTime().toMillis().formatTime()
        text += "Created: ${formatted.escapeHtml()}<br>"
      } catch (ex: Exception) {
        // No-op
      }
    }
    text += "</html>"
    myInfoText.text = text
  }

  fun setAndroidDevice(device: AndroidDevice?) {
    myRetentionPanel.androidDevice = device
  }

  fun setStartTime(time: Long?) {
    testStartTime = time
    updateInfoText()
  }

  companion object {
    private val LOG = Logger.getInstance(
      RetentionView::class.java)
  }
}

private fun Long.formatTime() = DateFormat.getDateTimeInstance().format(Date(this))

private fun String.escapeHtml() = StringEscapeUtils.escapeHtml(this)

data class CompatibleResult(val compatible: Boolean, val reason: String? = null)

fun isSystemImageCompatible(snapshotHardwareIni: Ini,
                            snapshotSystemImageBuildId: String,
                            androidSdkHandler: AndroidSdkHandler): CompatibleResult {
  val snapshotSystemImagePath = snapshotHardwareIni[INI_GLOBAL_SECTION_NAME]?.get("disk.systemPartition.initPath")
  if (snapshotSystemImagePath.isNullOrEmpty()) {
    return CompatibleResult(false, "Cannot find snapshot system image path.")
  }
  val snapshotSdkRoot = snapshotHardwareIni[INI_GLOBAL_SECTION_NAME]?.get("android.sdk.root")
  val newSystemImagePath = if (snapshotSdkRoot == null || androidSdkHandler.location?.path.isNullOrEmpty()) {
    snapshotSystemImagePath
  } else {
    snapshotSystemImagePath.replace(snapshotSdkRoot,
                                    androidSdkHandler.location?.path ?: "")
  }
  val systemImageBuildPropertyPath = File(File(newSystemImagePath).parent).resolve(BUILD_PROP_FILE_NAME)
  if (!systemImageBuildPropertyPath.isFile) {
    return CompatibleResult(false,
                            "Failed to find system image build property, expected path: ${systemImageBuildPropertyPath}")
  }
  if (!snapshotSystemImageBuildId.isNullOrEmpty()) {
    Ini().also {
      it.config = Config.getGlobal().apply {
        isGlobalSection = true
        globalSectionName = INI_GLOBAL_SECTION_NAME
        fileEncoding = Charset.defaultCharset()
      }
      it.load(systemImageBuildPropertyPath)
      if (snapshotSystemImageBuildId != it[INI_GLOBAL_SECTION_NAME]?.get("ro.build.id")
          && snapshotSystemImageBuildId != it[INI_GLOBAL_SECTION_NAME]?.get("ro.build.display.id")) {
        return CompatibleResult(false,
                                "System image version mismatch. Please manually download the system image with build ID"
                                + "${snapshotSystemImageBuildId} to folder ${File(newSystemImagePath).parent}")
      }
    }
  }
  return CompatibleResult(true)
}
