/*
 * Copyright (C) 2025 The Android Open Source Project
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
package com.android.tools.idea.ui.screenshot

import com.android.SdkConstants.EXT_PNG
import com.android.SdkConstants.PRIMARY_DISPLAY_ID
import com.android.sdklib.deviceprovisioner.DeviceType
import com.android.tools.analytics.UsageTracker.log
import com.android.tools.idea.concurrency.createCoroutineScope
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.ui.AndroidAdbUiBundle.message
import com.android.tools.idea.ui.save.PostSaveAction
import com.android.tools.idea.ui.save.SaveConfigurationResolver
import com.android.tools.pixelprobe.color.Colors
import com.google.wireless.android.sdk.stats.AndroidStudioEvent
import com.google.wireless.android.sdk.stats.DeviceScreenshotEvent
import com.google.wireless.android.sdk.stats.DeviceScreenshotEvent.DecorationOption
import com.intellij.icons.AllIcons
import com.intellij.ide.actions.RevealFileAction
import com.intellij.ide.util.PropertiesComponent
import com.intellij.notification.NotificationGroup
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.DataProvider
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.fileEditor.FileEditorProvider
import com.intellij.openapi.fileEditor.ex.FileEditorProviderManager
import com.intellij.openapi.fileTypes.NativeFileType.openAssociatedApplication
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.Messages.showErrorDialog
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.io.FileUtilRt.getExtension
import com.intellij.openapi.util.io.FileUtilRt.getNameWithoutExtension
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.IdeFocusManager
import com.intellij.platform.ide.progress.withModalProgress
import com.intellij.ui.dsl.builder.Align
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.panel
import com.intellij.util.ExceptionUtil.getMessage
import com.intellij.util.ui.components.BorderLayoutPanel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.intellij.images.editor.ImageFileEditor
import org.jetbrains.android.util.runOnDisposalOfAnyOf
import org.jetbrains.annotations.NonNls
import java.awt.Dimension
import java.awt.Point
import java.awt.color.ICC_ColorSpace
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.Transferable
import java.awt.datatransfer.UnsupportedFlavorException
import java.awt.event.ActionListener
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.OutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.text.SimpleDateFormat
import java.time.Instant
import java.util.Date
import java.util.Locale.ROOT
import java.util.concurrent.atomic.AtomicReference
import java.util.zip.Deflater
import javax.imageio.IIOImage
import javax.imageio.ImageIO
import javax.imageio.ImageTypeSpecifier
import javax.imageio.ImageWriter
import javax.imageio.metadata.IIOMetadataNode
import javax.swing.Action
import javax.swing.DefaultComboBoxModel
import javax.swing.JComponent
import javax.swing.JEditorPane
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * A dialog that shows a captured screenshot.
 *
 * @param project defines the context for the viewer
 * @param screenshotImage the screenshot to display
 * @param backingFile the temporary file containing the screenshot, which is deleted when the viewer
 *     is closed
 * @param screenshotProvider an optional provider of additional screenshots. The *Recapture*
 *     button is hidden if not provided
 * @param screenshotDecorator an optional postprocessor used for framing and clipping.
 *     The framing options are hidden if not provided
 * @param framingOptions available choices of frames. Ignored if [screenshotDecorator]
 *     is null. The pull-down list of framing options is shown only when [screenshotDecorator] is
 *     not null and there are two or more framing options.
 * @param defaultFramingOption the index of the default framing option in the [framingOptions] list
 * @param allowImageRotation determines whether the rotation buttons are available or not
 * @param dialogLocationArbiter allows multiple dialogs to avoid being displayed on top of each other
*/
class ScreenshotViewer(
  private val project: Project,
  screenshotImage: ScreenshotImage,
  processedImage: BufferedImage,
  private val backingFile: VirtualFile,
  private val screenshotProvider: ScreenshotProvider,
  private val screenshotDecorator: ScreenshotDecorator,
  framingOptions: List<FramingOption>,
  defaultFramingOption: Int,
  private val allowImageRotation: Boolean,
  private val dialogLocationArbiter: DialogLocationArbiter? = null,
) : DialogWrapper(project), DataProvider {

  private val editorProvider: FileEditorProvider = getImageFileEditorProvider()
  private val imageFileEditor = editorProvider.createEditor(project, backingFile) as ImageFileEditor

  private val saveConfigResolver = project.service<SaveConfigurationResolver>()
  private val settings = DeviceScreenshotSettings.getInstance()
  private val saveConfig
    get() = settings.saveConfig
  private val saveLocation: String
    get() = saveConfigResolver.expandSaveLocation (saveConfig.saveLocation)
  private var scale = settings.scale
  private var frameScreenshot = settings.frameScreenshot
  private lateinit var dialogPanel: DialogPanel
  private lateinit var saveLocationText: JEditorPane

  private var decorationComboBox = ComboBox<ScreenshotDecorationOption>()

  /**
   * Number of quadrants by which the screenshot from the device has been rotated. One of 0, 1, 2 or 3.
   * Used only if rotation buttons are enabled.
   */
  private var rotationQuadrants: Int

  /**
   * Reference to the screenshot obtained from the device and then rotated by [rotationQuadrants].
   * Accessed from both EDT and background threads.
   */
  private val sourceImageRef = AtomicReference<ScreenshotImage>()

  /**
   * Reference to the framed screenshot displayed on screen. Accessed from both EDT and background threads.
   */
  private val displayedImageRef = AtomicReference<TimestampedImage>()

  /**
   * The user specified destination where the screenshot was saved, or null of the screenshot was not saved.
   */
  private var screenshotFile: Path? = null

  private val defaultFileName: String
    get() {
      val timestampSuffix = SimpleDateFormat("yyyyMMdd_HHmmss", ROOT).format(Date())
      return "Screenshot_$timestampSuffix"
    }

  init {
    require(framingOptions.isEmpty() || 0 <= defaultFramingOption && defaultFramingOption < framingOptions.size)
        { "Invalid defaultFramingOption:$defaultFramingOption framingOptions:$framingOptions" }

    isModal = false
    title = when (screenshotImage.displayId) {
      PRIMARY_DISPLAY_ID -> message("screenshot.dialog.title.primary.display", screenshotImage.deviceName)
      else -> message("screenshot.dialog.title.secondary.display", screenshotImage.deviceName, screenshotImage.displayId)
    }
    sourceImageRef.set(screenshotImage)
    rotationQuadrants = screenshotImage.screenshotOrientationQuadrants
    displayedImageRef.set(TimestampedImage(processedImage))

    val decorationOptions = DefaultComboBoxModel<ScreenshotDecorationOption>()
    decorationOptions.addElement(ScreenshotDecorationOption.RECTANGULAR)
    // Clipping is available when the postprocessor supports it and for round devices.
    val canClipDeviceMask = screenshotDecorator.canClipToDisplayShape || screenshotImage.isRoundDisplay
    if (canClipDeviceMask) {
      decorationOptions.addElement(ScreenshotDecorationOption.DISPLAY_SHAPE_CLIP)
    }
    // DAC specifies a 384x384 minimum size requirement but that requirement is actually not enforced.
    // The 1:1 image aspect ratio is enforced, however.
    val isPlayCompatibleWearScreenshot = screenshotImage.isWear && screenshotImage.width == screenshotImage.height
    if (isPlayCompatibleWearScreenshot) {
      decorationOptions.addElement(ScreenshotDecorationOption.PLAY_COMPATIBLE)
    }
    val frameOptionStartIndex = decorationOptions.size
    for (framingOption in framingOptions) {
      decorationOptions.addElement(ScreenshotDecorationOption(framingOption))
    }
    decorationComboBox.setModel(decorationOptions)

    when {
      frameScreenshot && decorationComboBox.itemCount > defaultFramingOption + frameOptionStartIndex ->
          decorationComboBox.setSelectedIndex(defaultFramingOption + frameOptionStartIndex) // Select the default framing option.
      isPlayCompatibleWearScreenshot -> decorationComboBox.setSelectedItem(ScreenshotDecorationOption.PLAY_COMPATIBLE)
      canClipDeviceMask -> decorationComboBox.setSelectedItem(ScreenshotDecorationOption.DISPLAY_SHAPE_CLIP)
      else -> decorationComboBox.setSelectedItem(ScreenshotDecorationOption.RECTANGULAR)
    }

    val decorationListener = ActionListener {
      frameScreenshot = (decorationOptions.selectedItem as ScreenshotDecorationOption).framingOption != null
      processScreenshot()
    }
    decorationComboBox.addActionListener(decorationListener)

    init()

    updateEditorImage()
  }

  override fun createCenterPanel(): JComponent {
    dialogPanel = panel {
      row {
        button(message("screenshot.dialog.recapture.button.text")) { doRefreshScreenshot() }
          .applyToComponent {
            icon = AllIcons.Actions.Refresh
            runOnDisposalOfAnyOf(screenshotProvider, disposable, runnable = { setEnabled(false) })
          }

        if (allowImageRotation) {
          button(message("screenshot.dialog.rotate.left.button.text")) { updateImageRotation(1) }
          button(message("screenshot.dialog.rotate.right.button.text")) { updateImageRotation(3) }
        }

        button(message("screenshot.dialog.copy.button.text")) { copyImageToClipboard() }

        cell(decorationComboBox).align(AlignX.RIGHT)
      }
      row {
        cell(imageFileEditor.component).align(Align.FILL)
      }.resizableRow()
      row(message("screenshot.options.resolution")) {
        comboBox(listOf(100, 50, 25))
          .onChanged { updateScale(it.item / 100.0) }
          .applyToComponent { item = (scale * 100).roundToInt() }
      }
      row {
        text(message("screenrecord.options.save.directory"))
        text(saveLocation)
          .applyToComponent { saveLocationText = this }
        link(message("configure.save.link.text")) { configureSave() }
          .align(AlignX.RIGHT)
      }
    }

    // The following panel prevents the minimum size of the dialog from growing when it is resized.
    val sizingPanel = object : BorderLayoutPanel() {
      private var initialMinSize: Dimension? = null

      override fun getMinimumSize(): Dimension {
        return initialMinSize ?: super.getMinimumSize().apply {
          // To prevent the dialog from shrinking too much, constrain the height to be not less than the minimum width.
          height = max(width, height)
          initialMinSize = this
        }
      }
    }
    return sizingPanel.addToCenter(dialogPanel)
  }

  /**
   * This override is needed because [createCenterPanel] doesn't return [DialogPanel] and because
   * [DeviceScreenshotSettings.scale] and [DeviceScreenshotSettings.frameScreenshot] have to be updated
   * only after a successful screenshot saving.
   */
  override fun applyFields() {
    dialogPanel.apply()
    settings.scale = scale
    settings.frameScreenshot = frameScreenshot
  }

  override fun getHelpId(): String =
      "org.jetbrains.android.r/studio-ui/am-screenshot.html"

  override fun createDefaultActions() {
    super.createDefaultActions()
    okAction.putValue(Action.NAME, message("screenshot.dialog.ok.button.text"))
  }

  override fun doOKAction() {
    val image = displayedImageRef.get() ?: return
    val expandedFilename = saveConfigResolver.expandFilenamePattern(
      saveConfig.saveLocation, saveConfig.filenameTemplate, EXT_PNG, image.timestamp, settings.screenshotCount + 1)
    val file = adjustToAvoidExistingFiles(Paths.get(expandedFilename))
    try {
      Files.createDirectories(file.parent)
      writePng(image.image, file)
      settings.screenshotCount++
      screenshotFile = file
      logScreenshotUsage()
    }
    catch (e: IOException) {
      val error = e.message ?: e.javaClass.name
      showErrorDialog(project, message("screenshot.dialog.error", error), message("screenshot.action.title"))
      return
    }

    when (saveConfig.postSaveAction) {
      PostSaveAction.NONE -> {}
      PostSaveAction.SHOW_IN_FOLDER -> RevealFileAction.openFile(file)
      PostSaveAction.OPEN -> LocalFileSystem.getInstance().refreshAndFindFileByNioFile(file)?.let { openAssociatedApplication(it) }
    }

    super.doOKAction()
  }

  private fun adjustToAvoidExistingFiles(file: Path): Path {
    if (!Files.exists(file)) {
      return file
    }
    val filename = file.fileName.toString()
    val name = getNameWithoutExtension(filename)
    val extension = getExtension(name)
    val dir = file.parent
    var i = 0
    while (true) {
      val adjusted = dir.resolve("$name (${++i}).$extension")
      if (!Files.exists(file) || i > 100) {
        return adjusted
      }
    }
  }

  /**
   * Makes the screenshot viewer's focus on the image itself when opened, to allow keyboard shortcut copying.
   */
  override fun getPreferredFocusedComponent(): JComponent =
      imageFileEditor.component

  override fun getDimensionServiceKey(): @NonNls String {
    val displayId = sourceImageRef.get().displayId
    return when {
      !StudioFlags.MULTI_DISPLAY_SCREENSHOTS.get() || displayId == PRIMARY_DISPLAY_ID -> SCREENSHOT_VIEWER_DIMENSIONS_KEY
      else -> "$SCREENSHOT_VIEWER_DIMENSIONS_KEY.$displayId"
    }
  }

  override fun getInitialLocation(): Point? =
      dialogLocationArbiter?.suggestLocation(this)

  override fun beforeShowCallback() {
    dialogLocationArbiter?.dialogShown(this)
  }

  override fun getData(dataId: @NonNls String): Any? {
    // This is required since the Image Editor's actions are dependent on the context
    // being a ImageFileEditor.
    return if (PlatformCoreDataKeys.FILE_EDITOR.`is`(dataId)) imageFileEditor else null
  }

  override fun dispose() {
    editorProvider.disposeEditor(imageFileEditor)
    try {
      ApplicationManager.getApplication().runWriteAction {
        try {
          backingFile.delete(this)
        }
        catch (e: IOException) {
          thisLogger().error(e)
        }
      }
    }
    finally {
      super.dispose()
    }
  }

  private fun getImageFileEditorProvider(): FileEditorProvider {
    val providers = FileEditorProviderManager.getInstance().getProviderList(project, backingFile)
    assert(providers.isNotEmpty())

    // Note: In case there are multiple providers for image files, we'd prefer to get the bundled
    // image editor, but we don't have access to any of its implementation details, so we rely
    // on the editor type id being "images" as defined by ImageFileEditorProvider.EDITOR_TYPE_ID.
    for (provider in providers) {
      if (provider.editorTypeId == "images") {
        return provider
      }
    }

    return providers[0]
  }

  private fun doRefreshScreenshot() {
    screenshotProvider.createCoroutineScope().launch {
      withModalProgress(project, message("screenshot.task.step.obtain")) {
        try {
          val screenshotImage = screenshotProvider.captureScreenshot()
          sourceImageRef.set(screenshotImage)
          ApplicationManager.getApplication().invokeLater {
            processScreenshot(if (allowImageRotation) rotationQuadrants else 0)
          }
        }
        catch (e: CancellationException) {
          throw e
        }
        catch (e: Throwable) {
          val cause = getMessage(e) ?: e::javaClass.name
          val message = message("screenshot.error.generic", cause)
          thisLogger().error(message, e)
          ApplicationManager.getApplication().invokeLater {
            showErrorDialog(project, message, message("screenshot.action.title"))
          }
        }
      }
    }
  }

  private fun updateImageRotation(numQuadrants: Int) {
    rotationQuadrants = (rotationQuadrants + numQuadrants) and 0x3
    processScreenshot(numQuadrants)
  }

  private fun updateScale(scale: Double) {
    this.scale = scale
    processScreenshot()
  }

  private fun copyImageToClipboard() {
    val currentImage = imageFileEditor.imageEditor.document.value
    CopyPasteManager.getInstance().setContents(BufferedImageTransferable(currentImage))
    NotificationGroup.findRegisteredGroup("Screen Capture")
      ?.createNotification(message("screenshot.notification.copied.to.clipboard"), NotificationType.INFORMATION)
      ?.notify(project)
    logScreenshotUsage()
  }

  private fun configureSave() {
    ShowSettingsUtil.getInstance().showSettingsDialog(project, DeviceScreenshotSettingsPage::class.java)
    saveLocationText.text = saveLocation
    pack()
  }

  private fun processScreenshot(rotationQuadrants: Int = 0) {
    val screenshotImage: ScreenshotImage = sourceImageRef.get()
    val rotatedImage = screenshotImage.rotatedAndScaled(rotationQuadrants = rotationQuadrants)
    val processedImage = processImage(rotatedImage)

    // Update the backing file, this is necessary for operations that read the backing file from the editor,
    // such as: Right click image -> Open in external editor
    ApplicationManager.getApplication().runWriteAction {
      try {
        backingFile.getOutputStream(this).use { stream ->
          writePng(processedImage, stream)
        }
      }
      catch (e: IOException) {
        thisLogger().error("Unexpected error while writing to ${backingFile.toNioPath()}", e)
      }
    }
    sourceImageRef.set(rotatedImage)
    displayedImageRef.set(TimestampedImage(processedImage))
    updateEditorImage()
  }

  private fun processImage(sourceImage: ScreenshotImage): BufferedImage {
    val decoration = decorationComboBox.selectedItem as ScreenshotDecorationOption
    return screenshotDecorator.decorate(sourceImage.rotatedAndScaled(scale = scale), decoration)
  }

  private fun updateEditorImage() {
    imageFileEditor.imageEditor.document.value = displayedImageRef.get().image
    pack()

    // After image has updated, set the focus to image to allow keyboard shortcut copying.
    IdeFocusManager.getInstance(project).requestFocusInProject(preferredFocusedComponent, project)
  }

  private fun adjustedFileName(fileName: String): String {
    // Add extension to filename on Mac only see: b/38447816.
    return if (SystemInfo.isMac) "$fileName.png" else fileName
  }

  private fun loadScreenshotPath(): VirtualFile? {
    val properties = PropertiesComponent.getInstance(project)
    val lastPath = properties.getValue(SCREENSHOT_SAVE_PATH_KEY) ?: return project.guessProjectDir()
    return LocalFileSystem.getInstance().findFileByPath(lastPath)
  }

  private fun logScreenshotUsage() {
    val usageDeviceType = when (sourceImageRef.get()?.deviceType) {
      DeviceType.WEAR -> DeviceScreenshotEvent.DeviceType.WEAR
      DeviceType.HANDHELD -> DeviceScreenshotEvent.DeviceType.PHONE
      DeviceType.TV -> DeviceScreenshotEvent.DeviceType.TV
      else -> DeviceScreenshotEvent.DeviceType.UNKNOWN_DEVICE_TYPE
    }

    val usageDecorationOption = when (decorationComboBox.selectedItem) {
      ScreenshotDecorationOption.RECTANGULAR -> DecorationOption.RECTANGULAR
      ScreenshotDecorationOption.DISPLAY_SHAPE_CLIP -> DecorationOption.DISPLAY_SHAPE_CLIP
      ScreenshotDecorationOption.PLAY_COMPATIBLE -> DecorationOption.PLAY_COMPATIBLE
      else -> DecorationOption.FRAMED
    }

    val event = DeviceScreenshotEvent.newBuilder().setDeviceType(usageDeviceType).setDecorationOption(usageDecorationOption)
    log(AndroidStudioEvent.newBuilder().setKind(AndroidStudioEvent.EventKind.DEVICE_SCREENSHOT_EVENT).setDeviceScreenshotEvent(event))
  }

  private class BufferedImageTransferable(private val image: BufferedImage) : Transferable {
    override fun getTransferDataFlavors(): Array<DataFlavor> {
      return arrayOf(DataFlavor.imageFlavor)
    }

    override fun isDataFlavorSupported(dataFlavor: DataFlavor): Boolean {
      return DataFlavor.imageFlavor.equals(dataFlavor)
    }

    @Throws(UnsupportedFlavorException::class)
    override fun getTransferData(dataFlavor: DataFlavor): BufferedImage {
      if (!DataFlavor.imageFlavor.equals(dataFlavor)) {
        throw UnsupportedFlavorException(dataFlavor)
      }
      return image
    }
  }

  private class TimestampedImage(val image: BufferedImage) {
    val timestamp: Instant = Instant.now()
  }

  companion object {
    private const val SCREENSHOT_VIEWER_DIMENSIONS_KEY: @NonNls String = "ScreenshotViewer"
    private const val SCREENSHOT_SAVE_PATH_KEY: @NonNls String = "ScreenshotViewer.SavePath"

    fun getDefaultDecoration(screenshotImage: ScreenshotImage, screenshotDecorator: ScreenshotDecorator,
                             defaultFramingOption: FramingOption?): ScreenshotDecorationOption {
      val frameScreenshot = service<DeviceScreenshotSettings>().frameScreenshot
      // Clipping is available when either the postprocessor supports it or for round devices.
      val canClipDeviceMask = screenshotDecorator.canClipToDisplayShape || screenshotImage.isRoundDisplay
      // DAC specifies a 384x384 minimum size requirement but that requirement is actually not enforced.
      // The 1:1 image aspect ratio is enforced, however.
      val isPlayCompatibleWearScreenshot = screenshotImage.isWear && screenshotImage.width == screenshotImage.height

      return when {
        frameScreenshot && defaultFramingOption != null -> ScreenshotDecorationOption(defaultFramingOption)
        canClipDeviceMask -> ScreenshotDecorationOption.DISPLAY_SHAPE_CLIP
        isPlayCompatibleWearScreenshot -> ScreenshotDecorationOption.PLAY_COMPATIBLE
        else -> ScreenshotDecorationOption.RECTANGULAR
      }
    }

    @Throws(IOException::class)
    private fun writePng(image: BufferedImage, outFile: Path) {
      try {
        Files.newOutputStream(outFile).use { stream ->
          writePng(image, stream)
        }
      }
      catch (e: IOException) {
        Files.deleteIfExists(outFile)
        throw e
      }
    }

    @Throws(IOException::class)
    private fun writePng(image: BufferedImage, outputStream: OutputStream) {
      val imageType = ImageTypeSpecifier.createFromRenderedImage(image)
      val iterator = ImageIO.getImageWriters(imageType, EXT_PNG)
      if (!iterator.hasNext()) {
        throw IOException("Failed to find PNG writer")
      }
      val pngWriter: ImageWriter = iterator.next()

      ImageIO.createImageOutputStream(outputStream).use { stream ->
        pngWriter.setOutput(stream)
        try {
          val colorSpace = image.colorModel.colorSpace
          if (colorSpace is ICC_ColorSpace) {
            val type = ImageTypeSpecifier.createFromRenderedImage(image)
            val writeParams = pngWriter.defaultWriteParam
            val metadata = pngWriter.getDefaultImageMetadata(type, writeParams)
            val node = metadata.getAsTree("javax_imageio_png_1.0")
            val metadataNode = IIOMetadataNode("iCCP")
            metadataNode.userObject = deflate(colorSpace.profile.data)
            metadataNode.setAttribute("profileName", Colors.getIccProfileDescription(colorSpace.profile))
            metadataNode.setAttribute("compressionMethod", "deflate")
            node.appendChild(metadataNode)
            metadata.setFromTree("javax_imageio_png_1.0", node)

            pngWriter.write(IIOImage(image, null, metadata))
          }
          else {
            pngWriter.write(image)
          }
        }
        finally {
          pngWriter.dispose()
        }
      }
    }

    private fun deflate(data: ByteArray): ByteArray {
      val out = ByteArrayOutputStream(data.size)

      val deflater = Deflater()
      deflater.setInput(data)
      deflater.finish()

      val buffer = ByteArray(4096)
      while (!deflater.finished()) {
        val count = deflater.deflate(buffer)
        out.write(buffer, 0, count)
      }
      return out.toByteArray()
    }
  }
}
