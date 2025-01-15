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

import com.android.SdkConstants
import com.android.sdklib.deviceprovisioner.DeviceType
import com.android.tools.analytics.UsageTracker.log
import com.android.tools.idea.ui.AndroidAdbUiBundle.message
import com.android.tools.pixelprobe.color.Colors
import com.google.wireless.android.sdk.stats.AndroidStudioEvent
import com.google.wireless.android.sdk.stats.DeviceScreenshotEvent
import com.google.wireless.android.sdk.stats.DeviceScreenshotEvent.DecorationOption
import com.intellij.icons.AllIcons
import com.intellij.ide.util.PropertiesComponent
import com.intellij.notification.NotificationGroup
import com.intellij.notification.NotificationType
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.DataProvider
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.StoragePathMacros.NON_ROAMABLE_FILE
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.fileChooser.FileChooserFactory
import com.intellij.openapi.fileChooser.FileSaverDescriptor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorProvider
import com.intellij.openapi.fileEditor.ex.FileEditorProviderManager
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.IdeFocusManager
import com.intellij.ui.dsl.builder.Align
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.panel
import com.intellij.util.ui.components.BorderLayoutPanel
import com.intellij.util.xmlb.XmlSerializerUtil
import org.intellij.images.editor.ImageFileEditor
import org.jetbrains.android.util.runOnDisposalOfAnyOf
import org.jetbrains.annotations.NonNls
import java.awt.Dimension
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
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
import kotlin.math.max

/**
 * A dialog that shows a captured screenshot.
 *
 * @param project defines the context for the viewer
 * @param screenshotImage the screenshot to display
 * @param backingFile the temporary file containing the screenshot, which is deleted when the viewer
 *     is closed
 * @param screenshotSupplier an optional supplier of additional screenshots. The *Recapture*
 *     button is hidden if not provided
 * @param screenshotDecorator an optional postprocessor used for framing and clipping.
 *     The *Frame screenshot* checkbox and the framing options are hidden if not provided
 * @param framingOptions available choices of frames. Ignored if [screenshotDecorator]
 *     is null. The pull-down list of framing options is shown only when [screenshotDecorator] is
 *     not null and there are two or more framing options.
 * @param defaultFramingOption the index of the default framing option in the [framingOptions] list
 * @param screenshotViewerOptions determine whether the rotation buttons are available or not
*/
class ScreenshotViewer(
  private val project: Project,
  screenshotImage: ScreenshotImage,
  private val backingFile: VirtualFile,
  private val screenshotSupplier: ScreenshotSupplier,
  private val screenshotDecorator: ScreenshotDecorator,
  framingOptions: List<FramingOption>,
  defaultFramingOption: Int,
  screenshotViewerOptions: Set<Option>
) : DialogWrapper(project, true), DataProvider {

  private val timestampFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.ROOT)

  private var allowRotation = screenshotViewerOptions.contains(Option.ALLOW_IMAGE_ROTATION)
  private val editorProvider: FileEditorProvider = getImageFileEditorProvider()
  private val imageFileEditor = editorProvider.createEditor(project, backingFile) as ImageFileEditor

  private val persistentStorage = project.service<PersistentState>()

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
  private val displayedImageRef = AtomicReference<BufferedImage>()

  /**
   * The user specified destination where the screenshot was saved, or null of the screenshot was not saved.
   */
  var screenshotFile: Path? = null
    private set

  private val defaultFileName: String
    get() {
      val timestamp = Date()
      val timestampSuffix = timestampFormat.format(timestamp)
      return "Screenshot_$timestampSuffix"
    }

  init {
    require(framingOptions.isEmpty() || 0 <= defaultFramingOption && defaultFramingOption < framingOptions.size)
        { "Invalid defaultFramingOption:$defaultFramingOption framingOptions:$framingOptions" }

    isModal = false
    title = message("screenshot.action.title")

    sourceImageRef.set(screenshotImage)
    rotationQuadrants = screenshotImage.screenshotRotationQuadrants

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
      persistentStorage.frameScreenshot && decorationComboBox.itemCount > defaultFramingOption + frameOptionStartIndex ->
          decorationComboBox.setSelectedIndex(defaultFramingOption + frameOptionStartIndex) // Select the default framing option.
      isPlayCompatibleWearScreenshot -> decorationComboBox.setSelectedItem(ScreenshotDecorationOption.PLAY_COMPATIBLE)
      canClipDeviceMask -> decorationComboBox.setSelectedItem(ScreenshotDecorationOption.DISPLAY_SHAPE_CLIP)
      else -> decorationComboBox.setSelectedItem(ScreenshotDecorationOption.RECTANGULAR)
    }

    val decorationListener = ActionListener {
      persistentStorage.frameScreenshot = (decorationOptions.selectedItem as ScreenshotDecorationOption).framingOption != null
      updateImageFrame()
    }
    decorationComboBox.addActionListener(decorationListener)

    init()

    updateImageFrame()
  }

  override fun createCenterPanel(): JComponent {
    val panel = panel {
      row {
        button(message("screenshot.dialog.recapture.button.text")) { doRefreshScreenshot() }
          .applyToComponent {
            icon = AllIcons.Actions.Refresh
            runOnDisposalOfAnyOf(screenshotSupplier, disposable, runnable = Runnable { setEnabled(false) })
          }

        if (allowRotation) {
          button(message("screenshot.dialog.rotate.left.button.text")) { updateImageRotation(1) }
          button(message("screenshot.dialog.rotate.right.button.text")) { updateImageRotation(3) }
        }

        button(message("screenshot.dialog.copy.button.text")) { copyImageToClipboard() }

        cell(decorationComboBox).align(AlignX.RIGHT)
      }
      row {
        cell(imageFileEditor.component).align(Align.FILL)
      }.resizableRow()
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
    return sizingPanel.addToCenter(panel)
  }

  override fun getHelpId(): String =
      "org.jetbrains.android.r/studio-ui/am-screenshot.html"

  override fun createDefaultActions() {
    super.createDefaultActions()
    okAction.putValue(Action.NAME, message("screenshot.dialog.ok.button.text"))
  }

  override fun doOKAction() {
    val descriptor = FileSaverDescriptor(message("screenshot.dialog.title"), "", SdkConstants.EXT_PNG)
    val saveFileDialog = FileChooserFactory.getInstance().createSaveFileDialog(descriptor, project)
    val baseDir = loadScreenshotPath()
    val fileWrapper = saveFileDialog.save(baseDir, adjustedFileName(defaultFileName))
    if (fileWrapper == null) {
      return
    }

    val file = fileWrapper.file.toPath()
    try {
      val image = displayedImageRef.get()!!
      writePng(image, file)
      screenshotFile = file
      logScreenshotUsage()
    }
    catch (e: IOException) {
      Messages.showErrorDialog(project, message("screenshot.dialog.error", e), message("screenshot.action.title"))
      return
    }

    val virtualFile = fileWrapper.virtualFile
    if (virtualFile != null) {
      val properties = PropertiesComponent.getInstance(project)
      properties.setValue(SCREENSHOT_SAVE_PATH_KEY, virtualFile.parent.path)

      FileEditorManager.getInstance(project).openFile(virtualFile, true)
    }

    super.doOKAction()
  }

  /**
   * Makes the screenshot viewer's focus on the image itself when opened, to allow keyboard shortcut copying.
   */
  override fun getPreferredFocusedComponent(): JComponent =
      imageFileEditor.component

  override fun getDimensionServiceKey(): @NonNls String =
      SCREENSHOT_VIEWER_DIMENSIONS_KEY

  override fun getData(dataId: @NonNls String): Any? {
    // This is required since the Image Editor's actions are dependent on the context
    // being a ImageFileEditor.
    return if (PlatformCoreDataKeys.FILE_EDITOR.`is`(dataId)) imageFileEditor else null
  }

  override fun dispose() {
    editorProvider.disposeEditor(imageFileEditor)
    try {
      ApplicationManager.getApplication().runWriteAction(Runnable {
        try {
          backingFile.delete(this)
        }
        catch (e: IOException) {
          thisLogger().error(e)
        }
      })
    }
    finally {
      super.dispose()
    }
  }

  private fun getImageFileEditorProvider(): FileEditorProvider {
    val providers = FileEditorProviderManager.getInstance().getProviderList(project, backingFile)
    assert(!providers.isEmpty())

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
    requireNotNull(screenshotSupplier)
    object : ScreenshotTask(project, screenshotSupplier) {

      override fun run(indicator: ProgressIndicator) {
        Disposer.register(disposable, Disposable { indicator.cancel() })
        super.run(indicator)
      }

      override fun onSuccess() {
        val msg = error
        if (msg != null) {
          Messages.showErrorDialog(myProject, msg, message("screenshot.action.title"))
          return
        }

        val screenshotImage = screenshot
        sourceImageRef.set(screenshotImage)
        processScreenshot(if (allowRotation) rotationQuadrants else 0)
      }
    }.queue()
  }

  private fun updateImageRotation(numQuadrants: Int) {
    rotationQuadrants = (rotationQuadrants + numQuadrants) and 0x3
    processScreenshot(numQuadrants)
  }

  private fun updateImageFrame() {
    processScreenshot(0)
  }

  private fun copyImageToClipboard() {
    val currentImage = imageFileEditor.imageEditor.document.value
    CopyPasteManager.getInstance().setContents(BufferedImageTransferable(currentImage))
    NotificationGroup.findRegisteredGroup("Screen Capture")
      ?.createNotification(message("screenshot.notification.copied.to.clipboard"), NotificationType.INFORMATION)
      ?.notify(project)
    logScreenshotUsage()
  }

  private fun processScreenshot(rotationQuadrants: Int) {
    val rotatedImage = sourceImageRef.get().rotated(rotationQuadrants)
    val processedImage = processImage(rotatedImage)

    // Update the backing file, this is necessary for operations that read the backing file from the editor,
    // such as: Right click image -> Open in external editor
    ApplicationManager.getApplication().runWriteAction(Runnable {
      try {
        backingFile.getOutputStream(this).use { stream ->
          writePng(processedImage, stream)
        }
      }
      catch (e: IOException) {
        thisLogger().error("Unexpected error while writing to " + VfsUtilCore.virtualToIoFile(backingFile).toPath(), e)
      }
    })
    sourceImageRef.set(rotatedImage)
    displayedImageRef.set(processedImage)
    updateEditorImage()
  }

  private fun processImage(sourceImage: ScreenshotImage): BufferedImage {
    val decoration = decorationComboBox.selectedItem as ScreenshotDecorationOption
    return screenshotDecorator.decorate(sourceImage, decoration)
  }

  private fun updateEditorImage() {
    imageFileEditor.imageEditor.document.value = displayedImageRef.get()
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
      return arrayOf<DataFlavor>(DataFlavor.imageFlavor)
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

  enum class Option {
    ALLOW_IMAGE_ROTATION // Enables the image rotation buttons.
  }

  @Service(Service.Level.PROJECT)
  @State(name = "ScreenshotViewer", storages = [Storage(NON_ROAMABLE_FILE)])
  internal class PersistentState : PersistentStateComponent<PersistentState> {
    var frameScreenshot: Boolean = false

    override fun getState(): PersistentState {
      return this
    }

    override fun loadState(state: PersistentState) {
      XmlSerializerUtil.copyBean<PersistentState>(state, this)
    }
  }

  companion object {
    private const val SCREENSHOT_VIEWER_DIMENSIONS_KEY: @NonNls String = "ScreenshotViewer.Dimensions"
    private const val SCREENSHOT_SAVE_PATH_KEY: @NonNls String = "ScreenshotViewer.SavePath"

    fun getDefaultDecoration(screenshotImage: ScreenshotImage, screenshotDecorator: ScreenshotDecorator,
                             defaultFramingOption: FramingOption?, project: Project): ScreenshotDecorationOption {
      val frameScreenshot = project.service<PersistentState>().frameScreenshot
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
      val iterator = ImageIO.getImageWriters(imageType, SdkConstants.EXT_PNG)
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
