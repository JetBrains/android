/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.ui.screenshot;

import static com.android.SdkConstants.EXT_PNG;
import static com.google.wireless.android.sdk.stats.AndroidStudioEvent.EventKind.DEVICE_SCREENSHOT_EVENT;
import static com.intellij.openapi.components.StoragePathMacros.NON_ROAMABLE_FILE;
import static org.jetbrains.android.util.DisposableUtils.runOnDisposalOfAnyOf;

import com.android.tools.analytics.UsageTracker;
import com.android.tools.idea.ui.AndroidAdbUiBundle;
import com.android.tools.pixelprobe.color.Colors;
import com.google.common.base.Preconditions;
import com.google.wireless.android.sdk.stats.AndroidStudioEvent;
import com.google.wireless.android.sdk.stats.DeviceScreenshotEvent;
import com.intellij.icons.AllIcons;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.notification.NotificationGroup;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileChooser.FileChooserFactory;
import com.intellij.openapi.fileChooser.FileSaverDescriptor;
import com.intellij.openapi.fileChooser.FileSaverDialog;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.FileEditorProvider;
import com.intellij.openapi.fileEditor.ex.FileEditorProviderManager;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectUtil;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileWrapper;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.util.xmlb.XmlSerializerUtil;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.color.ICC_ColorSpace;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.event.ActionListener;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.zip.Deflater;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageTypeSpecifier;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.metadata.IIOMetadataNode;
import javax.imageio.stream.ImageOutputStream;
import javax.swing.Action;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JPanel;
import org.intellij.images.editor.ImageFileEditor;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.VisibleForTesting;
import org.w3c.dom.Node;

/**
 * A dialog that shows a captured screenshot.
 */
public class ScreenshotViewer extends DialogWrapper implements DataProvider {
  @NonNls private static final String SCREENSHOT_VIEWER_DIMENSIONS_KEY = "ScreenshotViewer.Dimensions";
  @NonNls private static final String SCREENSHOT_SAVE_PATH_KEY = "ScreenshotViewer.SavePath";

  private static final String HELP_PREFIX = "org.jetbrains.android.";

  private final @NotNull SimpleDateFormat myTimestampFormat = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.ROOT);

  private final @NotNull Project myProject;
  private final @Nullable ScreenshotSupplier myScreenshotSupplier;
  private final @NotNull ScreenshotDecorator myScreenshotDecorator;

  private final @NotNull VirtualFile myBackingFile;
  private final @NotNull ImageFileEditor myImageFileEditor;
  private final @NotNull FileEditorProvider myEditorProvider;
  private final @NotNull PersistentState myPersistentStorage;
  private final boolean myRotateOnRefresh;

  private @NotNull JPanel myPanel;
  private @NotNull JButton myRefreshButton;
  private @NotNull JButton myRotateRightButton;
  private @NotNull JButton myRotateLeftButton;
  private @NotNull JPanel myContentPane;
  private @NotNull JComboBox<ScreenshotDecorationOption> myDecorationComboBox;
  private @NotNull JButton myCopyButton;

  /**
   * Number of quadrants by which the screenshot from the device has been rotated. One of 0, 1, 2 or 3.
   * Used only if rotation buttons are enabled.
   */
  private int myRotationQuadrants;

  /**
   * Reference to the screenshot obtained from the device and then rotated by {@link #myRotationQuadrants}.
   * Accessed from both EDT and background threads.
   */
  private final AtomicReference<ScreenshotImage> mySourceImageRef = new AtomicReference<>();

  /**
   * Reference to the framed screenshot displayed on screen. Accessed from both EDT and background threads.
   */
  private final AtomicReference<BufferedImage> myDisplayedImageRef = new AtomicReference<>();

  /**
   * User specified destination where the screenshot is saved.
   */
  private @Nullable Path myScreenshotFile;

  /**
   * @param project                 defines the context for the viewer
   * @param screenshotImage         the screenshot to display
   * @param backingFile             the temporary file containing the screenshot, which is deleted when
   *                                the viewer is closed
   * @param screenshotSupplier      an optional supplier of additional screenshots. The <i>Recapture</i>
   *                                button is hidden if not provided
   * @param screenshotDecorator an optional postprocessor used for framing and clipping.
   *                                The <i>Frame screenshot</i> checkbox and the framing options are hidden if not provided
   * @param framingOptions          available choices of frames.  Ignored if {@code screenshotPostprocessor}
   *                                is null. The pull-down list of framing options is shown only when
   *                                {@code screenshotPostprocessor} is not null and there are two or more framing options.
   * @param defaultFramingOption    the index of the default framing option in
   *                                the {@code framingOptions} list
   * @param screenshotViewerOptions determine whether the rotation buttons are available or not
   */
  public ScreenshotViewer(@NotNull Project project,
                          @NotNull ScreenshotImage screenshotImage,
                          @NotNull VirtualFile backingFile,
                          @Nullable ScreenshotSupplier screenshotSupplier,
                          @NotNull ScreenshotDecorator screenshotDecorator,
                          @NotNull List<? extends FramingOption> framingOptions,
                          int defaultFramingOption,
                          @NotNull Set<Option> screenshotViewerOptions) {
    super(project, true);
    Preconditions.checkArgument(framingOptions.isEmpty() || defaultFramingOption >= 0 && defaultFramingOption < framingOptions.size(),
                                "framingOptions:%s defaultFramingOption:%s", framingOptions, defaultFramingOption);

    setModal(false);
    setTitle(AndroidAdbUiBundle.message("screenshot.action.title"));

    myProject = project;
    myScreenshotSupplier = screenshotSupplier;
    myScreenshotDecorator = screenshotDecorator;
    mySourceImageRef.set(screenshotImage);
    myRotationQuadrants = screenshotImage.getScreenshotRotationQuadrants();

    myBackingFile = backingFile;

    if (screenshotSupplier == null) {
      hideComponent(myRefreshButton);
    }
    else {
      myRefreshButton.setIcon(AllIcons.Actions.Refresh);
      runOnDisposalOfAnyOf(new Disposable[] { screenshotSupplier, getDisposable() }, () -> myRefreshButton.setEnabled(false));
    }

    myEditorProvider = getImageFileEditorProvider();
    myImageFileEditor = (ImageFileEditor)myEditorProvider.createEditor(myProject, myBackingFile);
    myContentPane.setLayout(new BorderLayout());
    myContentPane.add(myImageFileEditor.getComponent(), BorderLayout.CENTER);

    myPersistentStorage = PersistentState.getInstance(myProject);

    DefaultComboBoxModel<ScreenshotDecorationOption> decorationOptions = new DefaultComboBoxModel<>();
    decorationOptions.addElement(ScreenshotDecorationOption.RECTANGULAR);
    // Clipping is available when the postprocessor supports it and for round devices.
    boolean canClipDeviceMask = screenshotDecorator.getCanClipToDisplayShape() || screenshotImage.isRoundDisplay();
    if (canClipDeviceMask) {
      decorationOptions.addElement(ScreenshotDecorationOption.DISPLAY_SHAPE_CLIP);
    }
    // DAC specifies a 384x384 minimum size requirement but that requirement is actually not enforced.
    // The 1:1 image aspect ratio is enforced, however.
    boolean isPlayCompatibleWearScreenshot = screenshotImage.isWear() && screenshotImage.getWidth() == screenshotImage.getHeight();
    if (isPlayCompatibleWearScreenshot) {
      decorationOptions.addElement(ScreenshotDecorationOption.PLAY_COMPATIBLE);
    }
    int frameOptionStartIndex = decorationOptions.getSize();
    for (FramingOption framingOption : framingOptions) {
      decorationOptions.addElement(new ScreenshotDecorationOption(framingOption));
    }
    myDecorationComboBox.setModel(decorationOptions);

    if (myPersistentStorage.frameScreenshot && myDecorationComboBox.getItemCount() > defaultFramingOption + frameOptionStartIndex) {
      myDecorationComboBox.setSelectedIndex(defaultFramingOption + frameOptionStartIndex); // Select the default framing option.
    }
    else if (isPlayCompatibleWearScreenshot) {
      myDecorationComboBox.setSelectedItem(ScreenshotDecorationOption.PLAY_COMPATIBLE);
    }
    else if (canClipDeviceMask) {
      myDecorationComboBox.setSelectedItem(ScreenshotDecorationOption.DISPLAY_SHAPE_CLIP);
    }
    else {
      myDecorationComboBox.setSelectedItem(ScreenshotDecorationOption.RECTANGULAR);
    }

    ActionListener decorationListener = event -> {
      myPersistentStorage.frameScreenshot = ((ScreenshotDecorationOption)decorationOptions.getSelectedItem()).getFramingOption() != null;
      updateImageFrame();
    };
    myDecorationComboBox.addActionListener(decorationListener);

    myRefreshButton.addActionListener(event -> doRefreshScreenshot());

    if (screenshotViewerOptions.contains(Option.ALLOW_IMAGE_ROTATION)) {
      myRotateLeftButton.addActionListener(event -> updateImageRotation(1));
      myRotateRightButton.addActionListener(event -> updateImageRotation(3));
      myRotateOnRefresh = true;
    }
    else {
      hideComponent(myRotateLeftButton);
      hideComponent(myRotateRightButton);
      myRotateOnRefresh = false;
    }

    myCopyButton.addActionListener(event -> {
      BufferedImage currentImage = myImageFileEditor.getImageEditor().getDocument().getValue();
      CopyPasteManager.getInstance().setContents(new BufferedImageTransferable(currentImage));
      NotificationGroup group = NotificationGroup.findRegisteredGroup("Screen Capture");
      assert group != null;
      Notifications.Bus.notify(group.createNotification(AndroidAdbUiBundle.message("screenshot.notification.copied.to.clipboard"), NotificationType.INFORMATION), project);
      logScreenshotUsage();
    });

    init();

    updateImageFrame();
  }

  public static @NotNull ScreenshotDecorationOption getDefaultDecoration(
      @NotNull ScreenshotImage screenshotImage, @NotNull ScreenshotDecorator screenshotDecorator,
      @Nullable FramingOption defaultFramingOption, @NotNull Project project) {
    // Clipping is available when either the postprocessor supports it or for round devices.
    boolean canClipDeviceMask = screenshotDecorator.getCanClipToDisplayShape() || screenshotImage.isRoundDisplay();
    // DAC specifies a 384x384 minimum size requirement but that requirement is actually not enforced.
    // The 1:1 image aspect ratio is enforced, however.
    boolean isPlayCompatibleWearScreenshot = screenshotImage.isWear() && screenshotImage.getWidth() == screenshotImage.getHeight();

    if (PersistentState.getInstance(project).frameScreenshot && defaultFramingOption != null) {
      return new ScreenshotDecorationOption(defaultFramingOption);
    }
    else if (canClipDeviceMask) {
      return ScreenshotDecorationOption.DISPLAY_SHAPE_CLIP;
    }
    else if (isPlayCompatibleWearScreenshot) {
      return ScreenshotDecorationOption.PLAY_COMPATIBLE;
    }
    return ScreenshotDecorationOption.RECTANGULAR;
  }

  private void hideComponent(@NotNull Component component) {
    component.getParent().remove(component);
  }

  /**
   * Makes the screenshot viewer's focus on the image itself when opened, to allow keyboard shortcut copying
   */
  @Override
  public @NotNull JComponent getPreferredFocusedComponent() {
    return myImageFileEditor.getComponent();
  }

  @Override
  protected void dispose() {
    myEditorProvider.disposeEditor(myImageFileEditor);
    try {
      ApplicationManager.getApplication().runWriteAction(() -> {
        try {
          myBackingFile.delete(this);
        }
        catch (IOException e) {
          logger().error(e);
        }
      });
    }
    finally {
      super.dispose();
    }
  }

  private void doRefreshScreenshot() {
    assert myScreenshotSupplier != null;
    new ScreenshotTask(myProject, myScreenshotSupplier) {
      @Override
      public void run(@NotNull ProgressIndicator indicator) {
        Disposer.register(getDisposable(), indicator::cancel);
        super.run(indicator);
      }

      @Override
      public void onSuccess() {
        String msg = getError();
        if (msg != null) {
          Messages.showErrorDialog(myProject, msg, AndroidAdbUiBundle.message("screenshot.action.title"));
          return;
        }

        ScreenshotImage screenshotImage = getScreenshot();
        mySourceImageRef.set(screenshotImage);
        processScreenshot(myRotateOnRefresh ? myRotationQuadrants : 0);
      }
    }.queue();
  }

  private void updateImageRotation(int numQuadrants) {
    myRotationQuadrants = (myRotationQuadrants + numQuadrants) & 0x3;
    processScreenshot(numQuadrants);
  }

  private void updateImageFrame() {
    processScreenshot(0);
  }

  private void processScreenshot(int rotationQuadrants) {
    ScreenshotImage rotatedImage = mySourceImageRef.get().rotated(rotationQuadrants);
    BufferedImage processedImage = processImage(rotatedImage);

    // Update the backing file, this is necessary for operations that read the backing file from the editor,
    // such as: Right click image -> Open in external editor
    ApplicationManager.getApplication().runWriteAction(() -> {
      try (OutputStream stream = myBackingFile.getOutputStream(this)) {
        writePng(processedImage, stream);
      }
      catch (IOException e) {
        logger().error("Unexpected error while writing to " + VfsUtilCore.virtualToIoFile(myBackingFile).toPath(), e);
      }
    });
    mySourceImageRef.set(rotatedImage);
    myDisplayedImageRef.set(processedImage);
    updateEditorImage();
  }

  private @NotNull BufferedImage processImage(@NotNull ScreenshotImage sourceImage) {
    ScreenshotDecorationOption decoration = (ScreenshotDecorationOption)Objects.requireNonNull(myDecorationComboBox.getSelectedItem());
    return myScreenshotDecorator.decorate(sourceImage, decoration);
  }

  @VisibleForTesting
  void updateEditorImage() {
    BufferedImage image = myDisplayedImageRef.get();
    myImageFileEditor.getImageEditor().getDocument().setValue(image);
    pack();

    // After image has updated, set the focus to image to allow keyboard shortcut copying.
    IdeFocusManager.getInstance(myProject).requestFocusInProject(getPreferredFocusedComponent(), myProject);
  }

  private @NotNull FileEditorProvider getImageFileEditorProvider() {
    List<FileEditorProvider> providers = FileEditorProviderManager.getInstance().getProviderList(myProject, myBackingFile);
    assert !providers.isEmpty();

    // Note: In case there are multiple providers for image files, we'd prefer to get the bundled
    // image editor, but we don't have access to any of its implementation details, so we rely
    // on the editor type id being "images" as defined by ImageFileEditorProvider#EDITOR_TYPE_ID.
    for (FileEditorProvider p : providers) {
      if (p.getEditorTypeId().equals("images")) {
        return p;
      }
    }

    return providers.get(0);
  }

  @Override
  protected @Nullable JComponent createCenterPanel() {
    return myPanel;
  }

  @Override
  protected @NonNls @NotNull String getDimensionServiceKey() {
    return SCREENSHOT_VIEWER_DIMENSIONS_KEY;
  }

  @Override
  public @Nullable Object getData(@NonNls @NotNull String dataId) {
    // This is required since the Image Editor's actions are dependent on the context
    // being a ImageFileEditor.
    return PlatformCoreDataKeys.FILE_EDITOR.is(dataId) ? myImageFileEditor : null;
  }

  @Override
  protected @NotNull String getHelpId() {
    return HELP_PREFIX + "r/studio-ui/am-screenshot.html";
  }

  @Override
  protected void createDefaultActions() {
    super.createDefaultActions();
    getOKAction().putValue(Action.NAME, AndroidAdbUiBundle.message("screenshot.dialog.ok.button.text"));
  }

  @Override
  protected void doOKAction() {
    FileSaverDescriptor descriptor = new FileSaverDescriptor(AndroidAdbUiBundle.message("screenshot.dialog.title"), "", EXT_PNG);
    FileSaverDialog saveFileDialog = FileChooserFactory.getInstance().createSaveFileDialog(descriptor, myProject);
    VirtualFile baseDir = loadScreenshotPath();
    VirtualFileWrapper fileWrapper = saveFileDialog.save(baseDir, adjustedFileName(getDefaultFileName()));
    if (fileWrapper == null) {
      return;
    }

    myScreenshotFile = fileWrapper.getFile().toPath();
    try {
      writePng(myDisplayedImageRef.get(), myScreenshotFile);
      logScreenshotUsage();
    }
    catch (IOException e) {
      Messages.showErrorDialog(myProject, AndroidAdbUiBundle.message("screenshot.dialog.error", e),
                               AndroidAdbUiBundle.message("screenshot.action.title"));
      return;
    }

    VirtualFile virtualFile = fileWrapper.getVirtualFile();
    if (virtualFile != null) {
      PropertiesComponent properties = PropertiesComponent.getInstance(myProject);
      properties.setValue(SCREENSHOT_SAVE_PATH_KEY, virtualFile.getParent().getPath());

      FileEditorManager.getInstance(myProject).openFile(virtualFile, true);
    }

    super.doOKAction();
  }

  private @NotNull String adjustedFileName(@NotNull String fileName) {
    // Add extension to filename on Mac only see: b/38447816.
    return SystemInfo.isMac ? fileName + ".png" : fileName;
  }

  private static void writePng(@NotNull BufferedImage image, @NotNull Path outFile) throws IOException {
    try (OutputStream stream = Files.newOutputStream(outFile)) {
      writePng(image, stream);
    }
    catch (IOException e) {
      Files.deleteIfExists(outFile);
      throw e;
    }
  }

  private static void writePng(@NotNull BufferedImage image, @NotNull OutputStream outputStream) throws IOException {
    ImageWriter pngWriter = null;
    ImageTypeSpecifier imageType = ImageTypeSpecifier.createFromRenderedImage(image);
    Iterator<ImageWriter> iterator = ImageIO.getImageWriters(imageType, EXT_PNG);
    if (iterator.hasNext()) {
      pngWriter = iterator.next();
    }
    if (pngWriter == null) {
      throw new IOException("Failed to find PNG writer");
    }

    try (ImageOutputStream stream = ImageIO.createImageOutputStream(outputStream)) {
      pngWriter.setOutput(stream);

      if (image.getColorModel().getColorSpace() instanceof ICC_ColorSpace colorSpace) {
        ImageTypeSpecifier type = ImageTypeSpecifier.createFromRenderedImage(image);
        ImageWriteParam writeParams = pngWriter.getDefaultWriteParam();
        IIOMetadata metadata = pngWriter.getDefaultImageMetadata(type, writeParams);
        Node node = metadata.getAsTree("javax_imageio_png_1.0");
        IIOMetadataNode metadataNode = new IIOMetadataNode("iCCP");
        metadataNode.setUserObject(deflate(colorSpace.getProfile().getData()));
        metadataNode.setAttribute("profileName", Colors.getIccProfileDescription(colorSpace.getProfile()));
        metadataNode.setAttribute("compressionMethod", "deflate");
        node.appendChild(metadataNode);
        metadata.setFromTree("javax_imageio_png_1.0", node);

        pngWriter.write(new IIOImage(image, null, metadata));
      }
      else {
        pngWriter.write(image);
      }
      pngWriter.dispose();
    }
  }

  private static byte[] deflate(byte[] data) {
    ByteArrayOutputStream out = new ByteArrayOutputStream(data.length);

    Deflater deflater = new Deflater();
    deflater.setInput(data);
    deflater.finish();

    byte[] buffer = new byte[4096];
    while (!deflater.finished()) {
      int count = deflater.deflate(buffer);
      out.write(buffer, 0, count);
    }
    data = out.toByteArray();
    return data;
  }

  private @NotNull String getDefaultFileName() {
    Date timestamp = new Date();
    String timestampSuffix = myTimestampFormat.format(timestamp);
    return String.format("Screenshot_%s", timestampSuffix);
  }

  /**
   * Returns the saved screenshot file, or null of the screenshot was not saved.
   */
  public @Nullable Path getScreenshotFile() {
    return myScreenshotFile;
  }

  private @Nullable VirtualFile loadScreenshotPath() {
    PropertiesComponent properties = PropertiesComponent.getInstance(myProject);
    String lastPath = properties.getValue(SCREENSHOT_SAVE_PATH_KEY);

    if (lastPath != null) {
      return LocalFileSystem.getInstance().findFileByPath(lastPath);
    }

    return ProjectUtil.guessProjectDir(myProject);
  }

  private static @NotNull Logger logger() {
    return Logger.getInstance(ScreenshotViewer.class);
  }

  private void logScreenshotUsage() {
    var event = DeviceScreenshotEvent.newBuilder()
      .setDeviceType(getUsageDeviceType())
      .setDecorationOption(getUsageDecorationOption());

    UsageTracker.log(
      AndroidStudioEvent.newBuilder()
        .setKind(DEVICE_SCREENSHOT_EVENT)
        .setDeviceScreenshotEvent(event));
  }

  private @NotNull DeviceScreenshotEvent.DeviceType getUsageDeviceType() {
    return switch (mySourceImageRef.get().getDeviceType()) {
      case WEAR -> DeviceScreenshotEvent.DeviceType.WEAR;
      case HANDHELD -> DeviceScreenshotEvent.DeviceType.PHONE;
      case TV -> DeviceScreenshotEvent.DeviceType.TV;
      default -> DeviceScreenshotEvent.DeviceType.UNKNOWN_DEVICE_TYPE;
    };
  }

  private @NotNull DeviceScreenshotEvent.DecorationOption getUsageDecorationOption() {
    ScreenshotDecorationOption selectedDecoration = (ScreenshotDecorationOption)Objects.requireNonNull(myDecorationComboBox.getSelectedItem());
    if (ScreenshotDecorationOption.RECTANGULAR.equals(selectedDecoration)) {
      return DeviceScreenshotEvent.DecorationOption.RECTANGULAR;
    }
    if (ScreenshotDecorationOption.DISPLAY_SHAPE_CLIP.equals(selectedDecoration)) {
      return DeviceScreenshotEvent.DecorationOption.DISPLAY_SHAPE_CLIP;
    }
    if (ScreenshotDecorationOption.PLAY_COMPATIBLE.equals(selectedDecoration)) {
      return DeviceScreenshotEvent.DecorationOption.PLAY_COMPATIBLE;
    }
    return DeviceScreenshotEvent.DecorationOption.FRAMED;
  }

  private static class BufferedImageTransferable implements Transferable {
    private final @NotNull BufferedImage myImage;

    public BufferedImageTransferable(@NotNull BufferedImage image) {
      myImage = image;
    }

    @Override
    public @NotNull DataFlavor[] getTransferDataFlavors() {
      return new DataFlavor[] { DataFlavor.imageFlavor };
    }

    @Override
    public boolean isDataFlavorSupported(@NotNull DataFlavor dataFlavor) {
      return DataFlavor.imageFlavor.equals(dataFlavor);
    }

    @Override
    public @NotNull BufferedImage getTransferData(@NotNull DataFlavor dataFlavor) throws UnsupportedFlavorException {
      if (!DataFlavor.imageFlavor.equals(dataFlavor)) {
        throw new UnsupportedFlavorException(dataFlavor);
      }
      return myImage;
    }
  }

  public enum Option {
    ALLOW_IMAGE_ROTATION // Enables the image rotation buttons.
  }

  @State(name = "ScreenshotViewer", storages = @Storage(NON_ROAMABLE_FILE))
  public static class PersistentState implements PersistentStateComponent<PersistentState> {
    public boolean frameScreenshot;

    @Override
    public @Nullable ScreenshotViewer.PersistentState getState() {
      return this;
    }

    @Override
    public void loadState(@NotNull ScreenshotViewer.PersistentState state) {
      XmlSerializerUtil.copyBean(state, this);
    }

    public static PersistentState getInstance(@NotNull Project project) {
      return project.getService(PersistentState.class);
    }
  }
}
