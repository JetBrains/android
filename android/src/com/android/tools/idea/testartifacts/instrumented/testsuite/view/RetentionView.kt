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
import com.android.io.CancellableFileIo
import com.android.prefs.AndroidLocationsSingleton
import com.android.repository.api.ProgressIndicator
import com.android.sdklib.repository.AndroidSdkHandler
import com.android.tools.analytics.UsageTracker
import com.android.tools.idea.concurrency.AndroidExecutors
import com.android.tools.idea.progress.StudioLoggerProgressIndicator
import com.android.tools.idea.sdk.IdeSdks
import com.android.tools.idea.testartifacts.instrumented.AVD_NAME_KEY
import com.android.tools.idea.testartifacts.instrumented.EMULATOR_SNAPSHOT_FILE_KEY
import com.android.tools.idea.testartifacts.instrumented.EMULATOR_SNAPSHOT_ID_KEY
import com.android.tools.idea.testartifacts.instrumented.EMULATOR_SNAPSHOT_LAUNCH_PARAMETERS
import com.android.tools.idea.testartifacts.instrumented.IS_MANAGED_DEVICE
import com.android.tools.idea.testartifacts.instrumented.LOAD_RETENTION_ACTION_ID
import com.android.tools.idea.testartifacts.instrumented.PACKAGE_NAME_KEY
import com.android.tools.idea.testartifacts.instrumented.RETENTION_AUTO_CONNECT_DEBUGGER_KEY
import com.android.tools.idea.testartifacts.instrumented.RETENTION_ON_FINISH_KEY
import com.android.tools.idea.testartifacts.instrumented.retention.findFailureReasonFromEmulatorOutput
import com.android.tools.idea.testartifacts.instrumented.testsuite.logging.UsageLogReporter
import com.android.tools.idea.testartifacts.instrumented.testsuite.model.AndroidDevice
import com.android.tools.idea.testartifacts.instrumented.testsuite.model.AndroidDeviceType
import com.android.tools.utp.plugins.host.icebox.proto.IceboxOutputProto.IceboxOutput
import com.google.common.annotations.VisibleForTesting
import com.google.wireless.android.sdk.stats.AndroidStudioEvent
import com.google.wireless.android.sdk.stats.AndroidTestRetentionEvent
import com.google.wireless.android.sdk.stats.AndroidTestRetentionEvent.SnapshotCompatibility.Result
import com.google.wireless.android.sdk.stats.EmulatorSnapshotFailureReason
import com.intellij.icons.AllIcons
import com.intellij.ide.DataManager
import com.intellij.ide.HelpTooltip
import com.intellij.ide.actions.RevealFileAction
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataProvider
import com.intellij.openapi.diagnostic.Logger
import com.intellij.ui.AnimatedIcon
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
import java.util.concurrent.Executor
import java.util.concurrent.locks.ReentrantLock
import java.util.regex.Pattern
import javax.imageio.ImageIO
import javax.swing.BorderFactory
import javax.swing.ImageIcon
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.SwingConstants
import javax.swing.event.HyperlinkEvent
import kotlin.concurrent.withLock

/**
 * Shows the Android Test Retention artifacts
 */
class RetentionView(private val androidSdkHandler: AndroidSdkHandler
                    = AndroidSdkHandler.getInstance(AndroidLocationsSingleton, IdeSdks.getInstance().androidSdkPath?.toPath()),
                    private val progressIndicator: ProgressIndicator
                    = StudioLoggerProgressIndicator(RetentionView::class.java),
                    private val runtime: Runtime
                    = Runtime.getRuntime(),
                    private val usageLogReporter: UsageLogReporter
                    = RetentionUsageLogReporterImpl,
                    private val executor: Executor
                    = AndroidExecutors.getInstance().diskIoThreadExecutor) {
  private inner class RetentionPanel : JPanel(), DataProvider {
    private val retentionArtifactRegex = ".*-(failure[0-9]+)(.tar(.gz)?)?"
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
        PACKAGE_NAME_KEY.name -> retentionInfo?.appPackage?:classPackageName
        RETENTION_AUTO_CONNECT_DEBUGGER_KEY.name -> true
        RETENTION_ON_FINISH_KEY.name -> Runnable { myRetentionDebugButton.isEnabled = true }
        AVD_NAME_KEY.name -> androidDevice!!.avdName
        IS_MANAGED_DEVICE.name -> androidDevice!!.deviceType == AndroidDeviceType.LOCAL_GRADLE_MANAGED_EMULATOR
        else -> null
      }
    }
  }

  private var classPackageName = ""
  private var retentionInfo: IceboxOutput? = null
  // TODO(b/179519137): fix the paddings.
  @VisibleForTesting
  val myRetentionDebugLoading = JLabel("Validating snapshot", AnimatedIcon.Default(), SwingConstants.LEFT)
  @VisibleForTesting
  val myRetentionDebugButton: JButton = JButton("Start retention debug", AllIcons.Actions.Execute).apply {
    addActionListener {
      isEnabled = false
      val dataContext = DataManager.getInstance().getDataContext(myRetentionPanel)
      ActionManager.getInstance().getAction(LOAD_RETENTION_ACTION_ID).actionPerformed(
        AnActionEvent.createFromDataContext("", null, dataContext))
    }
    border = BorderFactory.createEmptyBorder()
    isBorderPainted = false
  }
  @VisibleForTesting
  val myRetentionHelperLabel = JLabel(AllIcons.General.ContextHelp).apply {
    isVisible = false
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
      val uri = it.url.toURI()
      LOG.info("opening ${uri.path}")
      if (it.eventType == HyperlinkEvent.EventType.ACTIVATED) {
        if (RevealFileAction.isSupported()) {
          RevealFileAction.openDirectory(File(uri))
        } else {
          LOG.warn("file opening not supported")
        }
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
        myRetentionDebugLoading()
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
  private val snapshotThreadLock = ReentrantLock()

  /**
   * Returns the root panel.
   */
  val rootPanel: JPanel
    get() = myRetentionPanel

  private data class CachedData(val state: RetentionViewState, val image: Image?, val snapshotProto: SnapshotOuterClass.Snapshot?)
  // Please only access it in AppUIUtil.invokeOnEdt
  private val cachedDataMap = HashMap<String, CachedData>()

  // Get the name of the APP being tested.
  @VisibleForTesting
  val appName: String get() = myRetentionPanel.getData(PACKAGE_NAME_KEY.name) as String

  fun setPackageName(packageName: String) {
    this.classPackageName = packageName
  }

  val component: JComponent
    get() = myRetentionPanel

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

  @AnyThread
  fun setRetentionInfoFile(retentionInfoFile: File?) {
    if (retentionInfoFile == null) {
      retentionInfo = null
    } else {
      retentionInfo = IceboxOutput.parseFrom(retentionInfoFile.inputStream())
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
    myRetentionPanel.snapshotProto = null
    val snapshotCheck = object: Runnable {
      override fun run() {
        scanSnapshotFileContent(snapshotFile) {
          snapshotCheckLock.withLock {
            lastSnapshotCheck != this
          }
        }
      }
    }
    snapshotCheckLock.withLock {
      lastSnapshotCheck = snapshotCheck
    }
    executor.execute {
      snapshotThreadLock.withLock {
        snapshotCheck.run()
      }
    }
  }

  @UiThread
  private fun updateDebugButton(state: RetentionViewState) {
    myRetentionDebugLoading.isVisible = state.isValidating
    myRetentionDebugButton.isVisible = !state.isValidating
    myRetentionHelperLabel.isVisible = !state.isValidating
    myRetentionDebugButton.isEnabled = state.loadable
    myRetentionDebugButton.toolTipText = state.reason
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
          updateDebugButton(SNAPSHOT_FILE_NOT_FOUND)
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
        if (cachedDataMap.containsKey(snapshotFile.absolutePath)) {
          cachedDataMap[snapshotFile.absolutePath].also {
            updateDebugButton(it!!.state)
            myRetentionPanel.snapshotProto = it!!.snapshotProto
            it!!.image?.also { image ->
              this.image = image
              updateSnapshotImage(image, image.getWidth(observer),
                                  image.getHeight(observer), isCancelled)
            }
          }
          shouldReturn = true
          waitForCheck.countDown()
          return@invokeOnEdt
        }
        updateDebugButton(VALIDATING_SNAPSHOT)
        waitForCheck.countDown()
      }
      waitForCheck.await()
      if (shouldReturn) {
        return
      }

      var snapshotProto: SnapshotOuterClass.Snapshot? = null
      if (snapshotFile.isDirectory) {
        try {
          snapshotFile.resolve("snapshot.pb").inputStream().use {
            snapshotProto = SnapshotOuterClass.Snapshot.parseFrom(it)
          }
          snapshotFile.resolve("screenshot.png").inputStream().use {
            val imageStream = ImageIO.read(it)
            val image = ImageIcon(imageStream).image
            this.image = image
            updateSnapshotImage(image, image.getWidth(observer),
                                image.getHeight(observer), isCancelled)
          }
        } catch (e: IOException) {
          // No-op. Handle null snapshotProto later.
        }
      } else {
        FileInputStream(snapshotFile).use { inputStream ->
          var gzipInputStream: InputStream? = null
          if (FileNameUtils.getExtension(
              snapshotFile.name.lowercase(
                Locale.getDefault())) == "gz") {
            gzipInputStream = GzipCompressorInputStream(
              inputStream)
          }
          TarArchiveInputStream(
            gzipInputStream ?: inputStream).use { tarInputStream ->
            var entry: TarArchiveEntry?
            while (tarInputStream.nextTarEntry.also { entry = it } != null) {
              if (isCancelled()) {
                return
              }
              if (entry!!.name == "screenshot.png") {
                val imageStream = ImageIO.read(
                  tarInputStream)
                image = ImageIcon(imageStream).image
                updateSnapshotImage(image!!, image!!.getWidth(observer),
                                    image!!.getHeight(observer), isCancelled)
              }
              else if (entry!!.name == "snapshot.pb") {
                snapshotProto = SnapshotOuterClass.Snapshot.parseFrom(tarInputStream)
              }
            }
          }
          gzipInputStream?.close()
        }
      }
      if (isCancelled()) {
        return
      }
      myRetentionPanel.snapshotProto = snapshotProto
      if (snapshotProto == null) {
        AppUIUtil.invokeOnEdt {
          val state = SnapshotProtoFileNotFound(snapshotFile.name)
          cachedDataMap[snapshotFile.absolutePath] = CachedData(state, image, null)
          reportCompatible(state)
          if (isCancelled()) {
            return@invokeOnEdt
          }
          updateDebugButton(state)
        }
        return
      }
      val emulatorPackage = androidSdkHandler.getLocalPackage(SdkConstants.FD_EMULATOR, progressIndicator)
      val emulatorBinary = emulatorPackage?.location?.resolve(SdkConstants.FN_EMULATOR)
      if (emulatorBinary == null || !CancellableFileIo.exists(emulatorBinary)) {
        AppUIUtil.invokeOnEdt {
          val state = EMULATOR_EXEC_NOT_FOUND
          cachedDataMap[snapshotFile.absolutePath] = CachedData(state, image, snapshotProto)
          reportCompatible(state)
          if (isCancelled()) {
            return@invokeOnEdt
          }
          updateDebugButton(state)
        }
        return
      }
      if (snapshotProto!!.launchParametersCount != 0) {
        val args = snapshotProto!!
          .launchParametersList
          .toMutableList()
          .apply {
            this[0] = emulatorBinary.toString()
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
              val state = Unloadable(lines.joinToString(" "))
              cachedDataMap[snapshotFile.absolutePath] = CachedData(state, image, snapshotProto)
              reportCompatible(state)
              if (isCancelled()) {
                return@invokeOnEdt
              }
              updateDebugButton(state)
            }
            return
          }
        }
      }
      AppUIUtil.invokeOnEdt {
        val state = LOADABLE
        cachedDataMap[snapshotFile.absolutePath] = CachedData(state, image, snapshotProto)
        reportCompatible(state)
        if (isCancelled()) {
          return@invokeOnEdt
        }
        updateDebugButton(state)
      }
    } catch (e: IOException) {
      LOG.warn(
        "Failed to parse retention snapshot", e)
      AppUIUtil.invokeOnEdt {
        val state = UNKNOWN_FAILURE
        if (snapshotFile != null) {
          cachedDataMap[snapshotFile.absolutePath] = CachedData(state, image, null)
          reportCompatible(state)
        }
        if (isCancelled()) {
          return@invokeOnEdt
        }
        updateDebugButton(state)
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
    text += "<br><b>Note: </b> After loading the snapshot, the <b>Debugger</b> tool window might not automatically select your app’s " +
            "thread. Make sure to navigate to your app’s thread in the dropdown menu to view your app’s call stack.<br>"
    val snapshotFile = myRetentionPanel.getSnapshotFile()
    if (snapshotFile != null) {
      text += "<br><b>Test snapshot</b><br>"
      text += "${snapshotFile.name.escapeHtml()}<br>"
      text += "size: ${snapshotFile.length() / 1024 / 1024} MB<br>"
      if (snapshotFile.parent != null && RevealFileAction.isSupported()) {
        text += "<a href=\"file:///${snapshotFile.parent.replace(" ", "%20").escapeHtml()}\">View file</a><br>"
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

  private fun reportCompatible(state: RetentionViewState) {
    usageLogReporter.report(
      AndroidStudioEvent.newBuilder().apply {
        category = AndroidStudioEvent.EventCategory.TESTS
        kind = AndroidStudioEvent.EventKind.ANDROID_TEST_RETENTION_EVENT
        androidTestRetentionEvent = androidTestRetentionEventBuilder.apply {
          snapshotCompatibility = AndroidTestRetentionEvent.SnapshotCompatibility.newBuilder().apply {
            this.result = state.loadableResultProto
            this.emulatorCheckFailureReason = state.emulatorCheckFailureReasonProto
          }.build()
        }.build()
      },
      System.currentTimeMillis())
  }

  companion object {
    private val LOG = Logger.getInstance(
      RetentionView::class.java)
  }
}

private fun Long.formatTime() = DateFormat.getDateTimeInstance().format(Date(this))

private fun String.escapeHtml() = StringEscapeUtils.escapeHtml(this)

private sealed class RetentionViewState(val isValidating: Boolean,
                                        val loadable: Boolean,
                                        val reason: String?,
                                        val loadableResultProto: Result,
                                        val emulatorCheckFailureReasonProto: EmulatorSnapshotFailureReason = EmulatorSnapshotFailureReason.EMULATOR_SNAPSHOT_FAILURE_REASON_UNSPECIFIED)

private object VALIDATING_SNAPSHOT : RetentionViewState(true, false,
                                                        "Validating snapshot file, please wait...",
                                                        Result.UNKNOWN_FAILURE)

private object SNAPSHOT_FILE_NOT_FOUND : RetentionViewState(false, false, "No snapshot file found.",
                                                            Result.SNAPSHOT_FILE_NOT_FOUND)

private object EMULATOR_EXEC_NOT_FOUND : RetentionViewState(false,
                                                            false,
                                                            "Missing emulator executables. Please download the emulator from SDK manager.",
                                                            Result.EMULATOR_EXEC_NOT_FOUND)

private object UNKNOWN_FAILURE : RetentionViewState(false, false, "Failed to parse retention snapshot",
                                                    Result.UNKNOWN_FAILURE)

private object LOADABLE : RetentionViewState(false, true, null, Result.LOADABLE)
private class SnapshotProtoFileNotFound(snapshotFileName: String) : RetentionViewState(
  false,
  false,
  "Snapshot protobuf not found, expectedPath ${snapshotFileName}:snapshot.pb\"",
  Result.SNAPSHOT_PROTO_FILE_NOT_FOUND)

private class Unloadable(reason: String) : RetentionViewState(
  false,
  false,
  "Snapshot not loadable, reason: $reason",
  Result.EMULATOR_LOADABLE_CHECK_FAILURE,
  findFailureReasonFromEmulatorOutput(reason)
)

data class CompatibleResult(val compatible: Boolean, val reason: String? = null)

private object RetentionUsageLogReporterImpl : UsageLogReporter {
  override fun report(studioEvent: AndroidStudioEvent.Builder, eventTimeMs: Long?) {
    if (eventTimeMs == null) {
      UsageTracker.log(studioEvent)
    } else {
      UsageTracker.log(eventTimeMs, studioEvent)
    }
  }
}
