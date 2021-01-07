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
import com.android.annotations.concurrency.UiThread
import com.android.emulator.snapshot.SnapshotOuterClass
import com.android.repository.api.ProgressIndicator
import com.android.sdklib.repository.AndroidSdkHandler
import com.android.tools.idea.concurrency.AndroidExecutors
import com.android.tools.idea.sdk.IdeSdks
import com.android.tools.idea.sdk.progress.StudioLoggerProgressIndicator
import com.android.tools.idea.testartifacts.instrumented.DEVICE_NAME_KEY
import com.android.tools.idea.testartifacts.instrumented.EMULATOR_SNAPSHOT_FILE_KEY
import com.android.tools.idea.testartifacts.instrumented.EMULATOR_SNAPSHOT_ID_KEY
import com.android.tools.idea.testartifacts.instrumented.EMULATOR_SNAPSHOT_LAUNCH_PARAMETERS
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
import com.intellij.ui.AppUIUtil
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
import java.awt.Desktop
import java.awt.Image
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import java.awt.image.ImageObserver
import java.io.BufferedReader
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.nio.file.Files
import java.nio.file.attribute.BasicFileAttributes
import java.text.DateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.locks.ReentrantLock
import java.util.regex.Pattern
import javax.imageio.ImageIO
import javax.swing.BorderFactory
import javax.swing.ImageIcon
import javax.swing.JButton
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.event.HyperlinkEvent

// TODO(yahan@) rework this view when we have the UI mock
/**
 * Shows the Android Test Retention artifacts
 */
class RetentionView(private val androidSdkHandler: AndroidSdkHandler
                    = AndroidSdkHandler.getInstance(IdeSdks.getInstance().androidSdkPath?.toPath()),
                    private val progressIndicator: ProgressIndicator
                    = StudioLoggerProgressIndicator(RetentionView::class.java),
                    private val runtime: Runtime
                    = Runtime.getRuntime()) {
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
      return when (dataId) {
        EMULATOR_SNAPSHOT_ID_KEY.name -> snapshotId
        EMULATOR_SNAPSHOT_FILE_KEY.name -> snapshotFile
        EMULATOR_SNAPSHOT_LAUNCH_PARAMETERS.name -> snapshotProto?.launchParametersList
        PACKAGE_NAME_KEY.name -> packageName
        RETENTION_AUTO_CONNECT_DEBUGGER_KEY.name -> true
        RETENTION_ON_FINISH_KEY.name -> Runnable { myRetentionDebugButton.isEnabled = true }
        DEVICE_NAME_KEY.name -> androidDevice!!.deviceName
        else -> null
      }
    }
  }

  private var packageName = ""
  @VisibleForTesting
  val myRetentionDebugButton: JButton = JButton("Start retention debug", AllIcons.Actions.Execute).apply {
    addActionListener {
      isEnabled = false
      val dataContext = DataManager.getInstance().getDataContext(myRetentionPanel)
      ActionManager.getInstance().getAction(LOAD_RETENTION_ACTION_ID).actionPerformed(
        AnActionEvent.createFromDataContext("", null, dataContext))
    }
    isBorderPainted = false
  }
  @VisibleForTesting
  val myRetentionHelperLabel = JLabel(AllIcons.General.ContextHelp).apply {
    HelpTooltip().setDescription(
      "Debug from the retention state will load the snapshot at the point of failure, and attach to the debugger."
    ).installOn(this)
  }
  @VisibleForTesting
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
  @VisibleForTesting
  val myImageLabel = JLabel()
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
  private val observer = ImageObserver { _, infoflags, _, _, width, height ->
    if (infoflags and ImageObserver.WIDTH == 0 || infoflags and ImageObserver.HEIGHT == 0) {
      return@ImageObserver true
    }
    updateSnapshotImage(image!!, width, height)
    false
  }

  private var testStartTime: Long? = null
  private var lastSnapshotCheck: Runnable? = null
  private val snapshotCheckLock = ReentrantLock()

  /**
   * Returns the root panel.
   */
  val rootPanel: JPanel
    get() = myRetentionPanel

  private data class SnapshotLoadability(val loadable: Boolean, val reason: String?)
  // Please only access it in AppUIUtil.invokeOnEdt
  private val cachedSnapshotChecks = HashMap<String, SnapshotLoadability>()

  fun setPackageName(packageName: String) {
    this.packageName = packageName
  }

  @AnyThread
  @VisibleForTesting
  fun updateSnapshotImage(image: Image, imageWidth: Int, imageHeight: Int,
                          isCancelled: (() -> Boolean)? = null) {
    val rootWidth = rootPanel.width
    if (rootWidth == 0 || imageHeight <= 0 || imageWidth <= 0) {
      return
    }
    val targetWidth = rootPanel.width / 4
    val targetHeight = targetWidth * imageHeight / imageWidth
    val newImage = image.getScaledInstance(targetWidth, targetHeight, Image.SCALE_DEFAULT)
    AppUIUtil.invokeOnEdt {
      if (isCancelled == null || !isCancelled()) {
        myImageLabel.icon = ImageIcon(newImage)
        myInnerPanel.revalidate()
      }
    }
  }

  /*
   * Sets the snapshot file and updates UI.
   *
   * @param snapshotFile a snapshot file
   */
  @UiThread
  fun setSnapshotFile(snapshotFile: File?) {
    if (myRetentionPanel.getSnapshotFile()?.canonicalPath
      == snapshotFile?.canonicalPath) {
      return
    }
    myRetentionPanel.setSnapshotFile(
      snapshotFile)
    updateInfoText()
    myImageLabel.icon = null
    image = null
    val snapshotCheck = object: Runnable {
      override fun run() {
        scanSnapshotFileContent(snapshotFile) {
          synchronized(snapshotCheckLock) {
            lastSnapshotCheck != this
          }
        }
      }
    }
    synchronized(snapshotCheckLock) {
      lastSnapshotCheck = snapshotCheck
    }
    AndroidExecutors.getInstance().ioThreadExecutor.execute {
      snapshotCheck.run()
    }
  }

  @VisibleForTesting
  fun scanSnapshotFileContent(snapshotFile: File?,
                              isCancelled: () -> Boolean) {
    try {
      if (isCancelled()) {
        return
      }
      if (snapshotFile == null) {
        AppUIUtil.invokeOnEdt {
          if (isCancelled()) {
            return@invokeOnEdt
          }
          myRetentionDebugButton.isEnabled = false
          myRetentionDebugButton.toolTipText = "No snapshot file found."
        }
        return
      }
      var shouldReturn = false
      var waitForCheck = CountDownLatch(1)
      AppUIUtil.invokeOnEdt {
        if (isCancelled()) {
          shouldReturn = true
          waitForCheck.countDown()
          return@invokeOnEdt
        }
        if (cachedSnapshotChecks.containsKey(snapshotFile.absolutePath)) {
          cachedSnapshotChecks[snapshotFile.absolutePath].also {
            myRetentionDebugButton.isEnabled = it!!.loadable
            myRetentionDebugButton.toolTipText = it!!.reason
          }
          shouldReturn = true
          waitForCheck.countDown()
          return@invokeOnEdt
        }
        myRetentionDebugButton.isEnabled = false
        myRetentionDebugButton.toolTipText = "Validating snapshot file, please wait..."
        waitForCheck.countDown()
      }
      waitForCheck.await()
      if (shouldReturn) {
        return
      }
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
      while (tarInputStream.nextTarEntry.also { entry = it } != null) {
        if (entry!!.name == "screenshot.png") {
          val imageStream = ImageIO.read(
            tarInputStream)
          image = ImageIcon(imageStream).image
          updateSnapshotImage(image!!, image!!.getWidth(observer),
                              image!!.getHeight(observer), isCancelled)
        } else if (entry!!.name == "snapshot.pb") {
          snapshotProto = SnapshotOuterClass.Snapshot.parseFrom(tarInputStream)
        }
      }
      myRetentionPanel.snapshotProto = snapshotProto
      if (snapshotProto == null) {
        AppUIUtil.invokeOnEdt {
          val tooltip = "Snapshot protobuf not found, expected path: ${snapshotFile.name}:snapshot.pb"
          cachedSnapshotChecks[snapshotFile.absolutePath] = SnapshotLoadability(false, tooltip)
          if (isCancelled()) {
            return@invokeOnEdt
          }
          myRetentionDebugButton.toolTipText = tooltip
        }
        return
      }
      val emulatorPackage = androidSdkHandler.getLocalPackage(SdkConstants.FD_EMULATOR, progressIndicator)
      val emulatorBinary = emulatorPackage?.location?.resolve(SdkConstants.FN_EMULATOR)?.let { androidSdkHandler.fileOp.toFile(it) }
      if (emulatorBinary == null || !androidSdkHandler.fileOp.exists(emulatorBinary)) {
        AppUIUtil.invokeOnEdt {
          val tooltip = "Missing emulator executables. Please download the emulator from SDK manager."
          cachedSnapshotChecks[snapshotFile.absolutePath] = SnapshotLoadability(false, tooltip)
          if (isCancelled()) {
            return@invokeOnEdt
          }
          myRetentionDebugButton.toolTipText = tooltip
        }
        return
      }
      if (snapshotProto.launchParametersCount != 0) {
        val args = snapshotProto
          .launchParametersList
          .toMutableList()
          .apply {
            this[0] = emulatorBinary.path
            add("-check-snapshot-loadable")
            add(snapshotFile.absolutePath)
          }
          .toTypedArray()
        val p = runtime.exec(args.joinToString(" "))
        p.waitFor()
        val lines = BufferedReader(InputStreamReader(p.inputStream)).readLines()
        lines.any {
          LOG.info(it)
          it.contains("Not loadable")
        }.let {
          if (it) {
            AppUIUtil.invokeOnEdt {
              val tooltip = "Snapshot not loadable, reason: ${lines.joinToString(" ")}"
              cachedSnapshotChecks[snapshotFile.absolutePath] = SnapshotLoadability(false, tooltip)
              if (isCancelled()) {
                return@invokeOnEdt
              }
              myRetentionDebugButton.toolTipText = tooltip
            }
            return
          }
        }
      }
      AppUIUtil.invokeOnEdt {
        cachedSnapshotChecks[snapshotFile.absolutePath] = SnapshotLoadability(true, null)
        if (isCancelled()) {
          return@invokeOnEdt
        }
        myRetentionDebugButton.toolTipText = null
        myRetentionDebugButton.isEnabled = true
      }
    } catch (e: IOException) {
      LOG.warn(
        "Failed to parse retention snapshot", e)
      AppUIUtil.invokeOnEdt {
        val tooltip = "Failed to parse retention snapshot"
        if (snapshotFile != null) {
          cachedSnapshotChecks[snapshotFile.absolutePath] = SnapshotLoadability(false, tooltip)
        }
        if (isCancelled()) {
          return@invokeOnEdt
        }
        myRetentionDebugButton.toolTipText = tooltip
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
      }
      catch (ex: Exception) {
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
