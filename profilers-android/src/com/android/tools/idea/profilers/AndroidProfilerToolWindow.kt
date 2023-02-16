/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.profilers

import com.android.ddmlib.IDevice
import com.android.tools.idea.transport.TransportService
import com.android.tools.idea.transport.TransportServiceProxy.Companion.getDeviceManufacturer
import com.android.tools.idea.transport.TransportServiceProxy.Companion.getDeviceModel
import com.android.tools.nativeSymbolizer.ProjectSymbolSource
import com.android.tools.nativeSymbolizer.SymbolFilesLocator
import com.android.tools.nativeSymbolizer.SymbolSource
import com.android.tools.profiler.proto.Common
import com.android.tools.profilers.Notification
import com.android.tools.profilers.ProfilerColors
import com.android.tools.profilers.ProfilerFonts
import com.android.tools.profilers.StudioProfilers
import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.VirtualFile
import icons.StudioIllustrations
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Dimension
import java.io.File
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.SwingConstants

class AndroidProfilerToolWindow(private val window: ToolWindowWrapper, private val project: Project) : Disposable {
  private val panel: JPanel
  private var profilersWrapper: StudioProfilersWrapper? = null
  private val ideProfilerServices: IntellijProfilerServices

  init {
    val symbolSource: SymbolSource = ProjectSymbolSource(project)
    val symbolLocator = SymbolFilesLocator(symbolSource)
    ideProfilerServices = IntellijProfilerServices(project, symbolLocator)
    Disposer.register(this, ideProfilerServices)

    panel = JPanel(BorderLayout())
    if (!tryInitializeProfilers()) {
      ideProfilerServices.featureTracker.trackProfilerInitializationFailed()
      panel.add(buildInitializationFailedUi())
    }
  }

  /**
   * Attempt to create the [StudioProfilers] and its facilities. Note that the StudioProfilers will not be re-created if one already
   * exists, or if the profilers is already running in a separate project.
   *
   * @return true if the StudioProfilers already exists or is successfully created. False otherwise.
   */
  private fun tryInitializeProfilers(): Boolean {
    if (profilersWrapper != null) {
      return true
    }

    TransportService.getInstance() ?: return false
    profilersWrapper = StudioProfilersWrapper(project, window, ideProfilerServices)
    Disposer.register(this, profilersWrapper!!)
    panel.removeAll()
    panel.add(profilersWrapper!!.profilersView.component)
    panel.revalidate()
    panel.repaint()
    return true
  }

  private fun buildInitializationFailedUi(): JComponent {
    val panel = JPanel()
    val layout = BoxLayout(panel, BoxLayout.Y_AXIS)
    panel.layout = layout
    panel.add(Box.createVerticalGlue())
    panel.background = ProfilerColors.DEFAULT_BACKGROUND

    val icon = JLabel(StudioIllustrations.Common.DISCONNECT_PROFILER)
    icon.horizontalAlignment = SwingConstants.CENTER
    icon.alignmentX = Component.CENTER_ALIGNMENT
    panel.add(icon)

    val title = JLabel(NO_CLIENT_TITLE)
    title.horizontalAlignment = SwingConstants.CENTER
    title.alignmentX = Component.CENTER_ALIGNMENT
    title.font = ProfilerFonts.H1_FONT
    title.foreground = ProfilerColors.MESSAGE_COLOR
    panel.add(title)
    panel.add(Box.createRigidArea(Dimension(1, 15)))

    val message = JLabel(NO_CLIENT_MESSAGE)
    message.horizontalAlignment = SwingConstants.CENTER
    message.alignmentX = Component.CENTER_ALIGNMENT
    message.font = ProfilerFonts.STANDARD_FONT
    message.foreground = ProfilerColors.MESSAGE_COLOR
    panel.add(message)
    panel.add(Box.createVerticalGlue())

    return panel
  }

  val profilers: StudioProfilers?
    /**
     * @return The [StudioProfilers] instance. Null if the profilers cannot be initialized, such as if it is already opened in another
     * project.
     */
    get() = profilersWrapper?.profilers

  /** Sets the profiler's auto-profiling process in case it has been unset.  */
  fun profile(processInfo: PreferredProcessInfo) {
    if (tryInitializeProfilers()) {
      val profilers: StudioProfilers = profilersWrapper!!.profilers
      profilers.setPreferredProcess(processInfo.deviceName,
                                    processInfo.processName) { p: Common.Process? -> processInfo.processFilter.invoke(p!!) }
    }
  }

  /**
   * Disables auto device+process selection.
   * See: [StudioProfilers.setAutoProfilingEnabled]
   */
  fun disableAutoProfiling() {
    if (tryInitializeProfilers()) {
      val profilers: StudioProfilers = profilersWrapper!!.profilers
      profilers.autoProfilingEnabled = false
    }
  }

  /**
   * Tries to import a file into an imported session of the profilers and shows an error balloon if it fails to do so.
   */
  fun openFile(file: VirtualFile) {
    if (tryInitializeProfilers()) {
      val profilers = profilersWrapper!!.profilers
      if (!profilers.sessionsManager.importSessionFromFile(File(file.path))) {
        profilers.ideServices.showNotification(OPEN_FILE_FAILURE_NOTIFICATION)
      }
    }
  }

  override fun dispose() {}

  val component: JComponent
    get() = panel

  companion object {
    /**
     * Key for storing the last app that was run when the profiler window was not opened. This allows the Profilers to start auto-profiling
     * that app when the user opens the window at a later time.
     */
    @JvmField
    val LAST_RUN_APP_INFO = Key.create<PreferredProcessInfo>("Profiler.Last.Run.App")
    private val OPEN_FILE_FAILURE_NOTIFICATION = Notification(
      Notification.Severity.ERROR,
      "Failed to open file",
      "The profiler was unable to open the selected file. Please try opening it " +
      "again or select a different file.",
      null)
    private const val NO_CLIENT_TITLE = "Initialization failed"
    private const val NO_CLIENT_MESSAGE = "To start the profiler, close all other Android Studio projects."

    /**
     * Analogous to [StudioProfilers.buildDeviceName] but works with an [IDevice] instead.
     *
     * @return A string of the format: {Manufacturer Model}.
     */
    @JvmStatic
    fun getDeviceDisplayName(device: IDevice): String {
      val manufacturer = getDeviceManufacturer(device)
      val model = getDeviceModel(device)
      val serial = device.serialNumber
      return getDeviceDisplayName(manufacturer, model, serial)
    }

    /**
     * Gets the display name of a device with the given manufacturer, model, and serial string.
     */
    fun getDeviceDisplayName(manufacturer: String, model: String, serial: String): String {
      var model = model
      val deviceNameBuilder = StringBuilder()
      val suffix = String.format("-%s", serial)
      if (model.endsWith(suffix)) {
        model = model.substring(0, model.length - suffix.length)
      }
      if (!StringUtil.isEmpty(manufacturer) && !model.uppercase().startsWith(manufacturer.uppercase())) {
        deviceNameBuilder.append(manufacturer)
        deviceNameBuilder.append(" ")
      }
      deviceNameBuilder.append(model)

      return deviceNameBuilder.toString()
    }
  }
}