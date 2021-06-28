/*
 * Copyright (C) 2013 The Android Open Source Project
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
package com.android.tools.idea.ddms.actions

import com.android.SdkConstants
import com.android.ddmlib.IDevice
import com.android.io.writeImage
import com.android.resources.ScreenOrientation
import com.android.tools.adtui.ImageUtils
import com.android.tools.adtui.device.DeviceArtDescriptor
import com.android.tools.idea.ddms.DeviceContext
import com.android.tools.idea.ddms.screenshot.DeviceArtFramingOption
import com.android.tools.idea.ddms.screenshot.DeviceArtScreenshotPostprocessor
import com.android.tools.idea.ddms.screenshot.DeviceScreenshotSupplier
import com.android.tools.idea.ddms.screenshot.FramingOption
import com.android.tools.idea.ddms.screenshot.ScreenshotImage
import com.android.tools.idea.ddms.screenshot.ScreenshotPostprocessor
import com.android.tools.idea.ddms.screenshot.ScreenshotSupplier
import com.android.tools.idea.ddms.screenshot.ScreenshotTask
import com.android.tools.idea.ddms.screenshot.ScreenshotViewer
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.LocalFileSystem
import icons.StudioIcons
import org.jetbrains.android.util.AndroidBundle
import java.util.EnumSet
import kotlin.math.min

/**
 * Captures a screenshot of the device display.
 */
class DeviceScreenshotAction(private val myProject: Project,
                             context: DeviceContext
) : AbstractDeviceAction(context, AndroidBundle.message("android.ddms.actions.screenshot"),
                         AndroidBundle.message("android.ddms.actions.screenshot.description"), StudioIcons.Logcat.SNAPSHOT) {

  override fun performAction(event: AnActionEvent, device: IDevice) {
    val project = myProject
    object : ScreenshotTask(project, DeviceScreenshotSupplier(device)) {

      override fun onSuccess() {
        error?.let { msg ->
          Messages.showErrorDialog(project, msg, AndroidBundle.message("android.ddms.actions.screenshot.title"))
          return
        }

        try {
          val backingFile = FileUtil.createTempFile("screenshot", SdkConstants.DOT_PNG).toPath()
          val screenshotImage = screenshot!!
          screenshotImage.image.writeImage(SdkConstants.EXT_PNG, backingFile)
          val screenshotSupplier: ScreenshotSupplier = DeviceScreenshotSupplier(device)
          val screenshotPostprocessor: ScreenshotPostprocessor = DeviceArtScreenshotPostprocessor()
          val framingOptions = getFramingOptions(screenshotImage)
          val defaultFrame = getDefaultFrame(framingOptions, screenshotImage, device.getProperty(IDevice.PROP_DEVICE_MODEL))
          val viewer: ScreenshotViewer = object : ScreenshotViewer(project, screenshotImage, backingFile, screenshotSupplier,
                                                                   screenshotPostprocessor, framingOptions, defaultFrame,
                                                                   EnumSet.of(Option.ALLOW_IMAGE_ROTATION)) {
            override fun doOKAction() {
              super.doOKAction()
              screenshot?.let {
                LocalFileSystem.getInstance().refreshAndFindFileByNioFile(it)?.let { virtualFile ->
                  virtualFile.refresh(false, false)
                  FileEditorManager.getInstance(project).openFile(virtualFile, true)
                }
              }
            }
          }
          viewer.show()
        }
        catch (e: Exception) {
          thisLogger().warn("Error while displaying screenshot viewer: ", e)
          Messages.showErrorDialog(project, AndroidBundle.message("android.ddms.screenshot.generic.error", e),
                                   AndroidBundle.message("android.ddms.actions.screenshot.title"))
        }
      }
    }.queue()
  }

  /** Returns the list of available frames for the given image.  */
  private fun getFramingOptions(screenshotImage: ScreenshotImage): List<DeviceArtFramingOption> {
    val imgAspectRatio = screenshotImage.width.toDouble() / screenshotImage.height
    val orientation = if (imgAspectRatio >= 1 - ImageUtils.EPSILON) ScreenOrientation.LANDSCAPE else ScreenOrientation.PORTRAIT
    val allDescriptors = DeviceArtDescriptor.getDescriptors(null)
    return allDescriptors.filter { it.canFrameImage(screenshotImage.image, orientation) }.map { DeviceArtFramingOption(it) }
  }

  private fun getDefaultFrame(frames: List<FramingOption>, screenshotImage: ScreenshotImage, deviceModel: String?): Int {
    if (deviceModel != null) {
      val index = findFrameIndexForDeviceModel(frames, deviceModel)
      if (index >= 0) {
        return index
      }
    }
    // Assume that if the min resolution is > 1280, then we are on a tablet.
    val defaultDevice = if (min(screenshotImage.width, screenshotImage.height) > 1280) "Generic Tablet" else "Generic Phone"
    // If we can't find anything (which shouldn't happen since we should get the Generic Phone/Tablet),
    // default to the first one.
    return findFrameIndexForDeviceModel(frames, defaultDevice).coerceAtLeast(0)
  }

  private fun findFrameIndexForDeviceModel(frames: List<FramingOption>, deviceModel: String): Int {
    return frames.indexOfFirst { it.displayName.equals(deviceModel, ignoreCase = true) }
  }
}