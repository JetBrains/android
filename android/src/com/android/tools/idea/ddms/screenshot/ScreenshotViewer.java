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
package com.android.tools.idea.ddms.screenshot;

import com.android.SdkConstants;
import com.android.ddmlib.IDevice;
import com.android.resources.ScreenOrientation;
import com.android.tools.idea.rendering.ImageUtils;
import com.intellij.icons.AllIcons;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileChooser.FileChooserFactory;
import com.intellij.openapi.fileChooser.FileSaverDescriptor;
import com.intellij.openapi.fileChooser.FileSaverDialog;
import com.intellij.openapi.fileEditor.FileEditorProvider;
import com.intellij.openapi.fileEditor.ex.FileEditorProviderManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileWrapper;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.util.containers.ContainerUtil;
import org.intellij.images.editor.ImageEditor;
import org.intellij.images.editor.ImageFileEditor;
import org.intellij.images.editor.ImageZoomModel;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.event.ActionListener;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.Calendar;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

public class ScreenshotViewer extends DialogWrapper implements DataProvider {
  @NonNls private static final String SCREENSHOT_VIEWER_DIMENSIONS_KEY = "ScreenshotViewer.Dimensions";
  @NonNls private static final String SCREENSHOT_SAVE_PATH_KEY = "ScreenshotViewer.SavePath";

  private final Project myProject;
  private final IDevice myDevice;

  private final VirtualFile myBackingVirtualFile;
  private final ImageFileEditor myImageFileEditor;
  private final FileEditorProvider myProvider;

  private final List<DeviceArtDescriptor> myDeviceArtDescriptors;

  private JPanel myPanel;
  private JButton myRefreshButton;
  private JButton myRotateButton;
  private JBScrollPane myScrollPane;
  private JCheckBox myFrameScreenshotCheckBox;
  private JComboBox<String> myDeviceArtCombo;
  private JCheckBox myDropShadowCheckBox;
  private JCheckBox myScreenGlareCheckBox;

  /** Angle in degrees by which the screenshot from the device has been rotated. One of 0, 90, 180 or 270. */
  private int myRotationAngle = 0;

  /**
   * Reference to the screenshot obtained from the device and then rotated by {@link #myRotationAngle} degrees.
   * Accessed from both EDT and background threads.
   */
  private AtomicReference<BufferedImage> mySourceImageRef = new AtomicReference<>();

  /** Reference to the framed screenshot displayed on screen. Accessed from both EDT and background threads. */
  private AtomicReference<BufferedImage> myDisplayedImageRef = new AtomicReference<>();

  /** User specified destination where the screenshot is saved. */
  private File myScreenshotFile;

  public ScreenshotViewer(@NotNull Project project,
                          @NotNull BufferedImage image,
                          @NotNull File backingFile,
                          @Nullable IDevice device,
                          @Nullable String deviceModel) {
    super(project, true);

    myProject = project;
    myDevice = device;
    mySourceImageRef.set(image);
    myDisplayedImageRef.set(image);

    myBackingVirtualFile = LocalFileSystem.getInstance().findFileByIoFile(backingFile);
    assert myBackingVirtualFile != null;

    myRefreshButton.setIcon(AllIcons.Actions.Refresh);
    myRefreshButton.setEnabled(device != null);
    myRotateButton.setIcon(AllIcons.Actions.AllRight);

    myProvider = getImageFileEditorProvider();
    myImageFileEditor = (ImageFileEditor)myProvider.createEditor(myProject, myBackingVirtualFile);
    myScrollPane.getViewport().add(myImageFileEditor.getComponent());

    ActionListener l = actionEvent -> {
      if (actionEvent.getSource() == myRefreshButton) {
        doRefreshScreenshot();
      } else if (actionEvent.getSource() == myRotateButton) {
        doRotateScreenshot();
      } else if (actionEvent.getSource() == myFrameScreenshotCheckBox
                 || actionEvent.getSource() == myDeviceArtCombo
                 || actionEvent.getSource() == myDropShadowCheckBox
                 || actionEvent.getSource() == myScreenGlareCheckBox) {
        doFrameScreenshot();
      }
    };

    myRefreshButton.addActionListener(l);
    myRotateButton.addActionListener(l);
    myFrameScreenshotCheckBox.addActionListener(l);
    myDeviceArtCombo.addActionListener(l);
    myDropShadowCheckBox.addActionListener(l);
    myScreenGlareCheckBox.addActionListener(l);

    myDeviceArtDescriptors = getDescriptorsToFrame(image);
    String[] titles = new String[myDeviceArtDescriptors.size()];
    for (int i = 0; i < myDeviceArtDescriptors.size(); i++) {
      titles[i] = myDeviceArtDescriptors.get(i).getName();
    }
    DefaultComboBoxModel<String> model = new DefaultComboBoxModel<>(titles);
    myDeviceArtCombo.setModel(model);

    // Set the default device art descriptor selection
    myDeviceArtCombo.setSelectedIndex(getDefaultDescriptor(myDeviceArtDescriptors, image, deviceModel));

    setModal(false);
    init();
  }

  // returns the list of descriptors capable of framing the given image
  private static List<DeviceArtDescriptor> getDescriptorsToFrame(final BufferedImage image) {
    double imgAspectRatio = image.getWidth() / (double) image.getHeight();
    final ScreenOrientation orientation =
      imgAspectRatio >= (1 - ImageUtils.EPSILON) ? ScreenOrientation.LANDSCAPE : ScreenOrientation.PORTRAIT;

    List<DeviceArtDescriptor> allDescriptors = DeviceArtDescriptor.getDescriptors(null);
    return ContainerUtil.filter(allDescriptors, descriptor -> {
      return descriptor.canFrameImage(image, orientation);
    });
  }

  private static int getDefaultDescriptor(List<DeviceArtDescriptor> deviceArtDescriptors, BufferedImage image,
                                          @Nullable String deviceModel) {
    int index = -1;

    if (deviceModel != null) {
      index = findDescriptorIndexForProduct(deviceArtDescriptors, deviceModel);
    }

    if (index < 0) {
      // Assume that if the min resolution is > 1280, then we are on a tablet
      String defaultDevice = Math.min(image.getWidth(), image.getHeight()) > 1280 ? "Generic Tablet" : "Generic Phone";
      index = findDescriptorIndexForProduct(deviceArtDescriptors, defaultDevice);
    }

    // If we can't find anything (which shouldn't happen since we should get the Generic Phone/Tablet),
    // default to the first one.
    if (index < 0) {
      index = 0;
    }

    return index;
  }

  private static int findDescriptorIndexForProduct(List<DeviceArtDescriptor> descriptors, String deviceModel) {
    for (int i = 0; i < descriptors.size(); i++) {
      DeviceArtDescriptor d = descriptors.get(i);
      if (d.getName().equalsIgnoreCase(deviceModel)) {
        return i;
      }
    }
    return -1;
  }

  @Override
  protected void dispose() {
    myProvider.disposeEditor(myImageFileEditor);
    super.dispose();
  }

  private void doRefreshScreenshot() {
    assert myDevice != null;
    new ScreenshotTask(myProject, myDevice) {
      @Override
      public void onSuccess() {
        String msg = getError();
        if (msg != null) {
          Messages.showErrorDialog(myProject, msg, AndroidBundle.message("android.ddms.actions.screenshot"));
          return;
        }

        BufferedImage image = getScreenshot();
        mySourceImageRef.set(image);
        processScreenshot(myFrameScreenshotCheckBox.isSelected(), myRotationAngle);
      }
    }.queue();
  }

  private void doRotateScreenshot() {
    myRotationAngle = (myRotationAngle + 90) % 360;
    processScreenshot(myFrameScreenshotCheckBox.isSelected(), 90);
  }

  private void doFrameScreenshot() {
    boolean shouldFrame = myFrameScreenshotCheckBox.isSelected();

    myDeviceArtCombo.setEnabled(shouldFrame);
    myDropShadowCheckBox.setEnabled(shouldFrame);
    myScreenGlareCheckBox.setEnabled(shouldFrame);

    if (shouldFrame) {
      processScreenshot(true, 0);
    } else {
      myDisplayedImageRef.set(mySourceImageRef.get());
      updateEditorImage();
    }
  }

  private void processScreenshot(boolean addFrame, int rotateByAngle) {
    DeviceArtDescriptor spec = addFrame ? myDeviceArtDescriptors.get(myDeviceArtCombo.getSelectedIndex()) : null;
    boolean shadow = addFrame && myDropShadowCheckBox.isSelected();
    boolean reflection = addFrame && myScreenGlareCheckBox.isSelected();

    new ImageProcessorTask(myProject, mySourceImageRef.get(), rotateByAngle, spec, shadow, reflection, myBackingVirtualFile) {
      @Override
      public void onSuccess() {
        mySourceImageRef.set(getRotatedImage());
        myDisplayedImageRef.set(getProcessedImage());
        updateEditorImage();
      }
    }.queue();
  }

  private static class ImageProcessorTask extends Task.Modal {
    private final BufferedImage mySrcImage;
    private final int myRotationAngle;
    private final DeviceArtDescriptor myDescriptor;
    private final boolean myAddShadow;
    private final boolean myAddReflection;
    private final VirtualFile myDestinationFile;

    private BufferedImage myRotatedImage;
    private BufferedImage myProcessedImage;

    public ImageProcessorTask(@Nullable Project project,
                              @NotNull BufferedImage srcImage,
                              int rotateByAngle,
                              @Nullable DeviceArtDescriptor descriptor,
                              boolean addShadow,
                              boolean addReflection,
                              VirtualFile writeToFile) {
      super(project, AndroidBundle.message("android.ddms.screenshot.image.processor.task.title"), false);

      mySrcImage = srcImage;
      myRotationAngle = rotateByAngle;
      myDescriptor = descriptor;
      myAddShadow = addShadow;
      myAddReflection = addReflection;
      myDestinationFile = writeToFile;
    }

    @Override
    public void run(@NotNull ProgressIndicator indicator) {
      if (myRotationAngle != 0) {
        myRotatedImage = ImageUtils.rotateByRightAngle(mySrcImage, myRotationAngle);
      } else {
        myRotatedImage = mySrcImage;
      }

      if (myDescriptor != null) {
        myProcessedImage = DeviceArtPainter.createFrame(myRotatedImage, myDescriptor, myAddShadow, myAddReflection);
      } else {
        myProcessedImage = myRotatedImage;
      }

      myProcessedImage = ImageUtils.cropBlank(myProcessedImage, null);

      // update backing file, this is necessary for operations that read the backing file from the editor,
      // such as: Right click image -> Open in external editor
      if (myDestinationFile != null) {
        File file = VfsUtilCore.virtualToIoFile(myDestinationFile);
        try {
          ImageIO.write(myProcessedImage, SdkConstants.EXT_PNG, file);
        }
        catch (IOException e) {
          Logger.getInstance(ImageProcessorTask.class).error("Unexpected error while writing to backing file", e);
        }
      }
    }

    protected BufferedImage getProcessedImage() {
      return myProcessedImage;
    }

    protected BufferedImage getRotatedImage() {
      return myRotatedImage;
    }
  }

  private void updateEditorImage() {
    BufferedImage image = myDisplayedImageRef.get();
    ImageEditor imageEditor = myImageFileEditor.getImageEditor();

    ImageZoomModel zoomModel = imageEditor.getZoomModel();
    double zoom = zoomModel.getZoomFactor();

    imageEditor.getDocument().setValue(image);
    pack();

    zoomModel.setZoomFactor(zoom);
  }

  private FileEditorProvider getImageFileEditorProvider() {
    FileEditorProvider[] providers = FileEditorProviderManager.getInstance().getProviders(myProject, myBackingVirtualFile);
    assert providers.length > 0;

    // Note: In case there are multiple providers for image files, we'd prefer to get the bundled
    // image editor, but we don't have access to any of its implementation details so we rely
    // on the editor type id being "images" as defined by ImageFileEditorProvider#EDITOR_TYPE_ID.
    for (FileEditorProvider p : providers) {
      if (p.getEditorTypeId().equals("images")) {
        return p;
      }
    }

    return providers[0];
  }

  @Nullable
  @Override
  protected JComponent createCenterPanel() {
    return myPanel;
  }

  @NonNls
  @Override
  @Nullable
  protected String getDimensionServiceKey() {
    return SCREENSHOT_VIEWER_DIMENSIONS_KEY;
  }

  @Nullable
  @Override
  public Object getData(@NonNls String dataId) {
    // This is required since the Image Editor's actions are dependent on the context
    // being a ImageFileEditor.
    return PlatformDataKeys.FILE_EDITOR.getName().equals(dataId) ? myImageFileEditor : null;
  }

  @Override
  protected void createDefaultActions() {
    super.createDefaultActions();
    getOKAction().putValue(Action.NAME, AndroidBundle.message("android.ddms.screenshot.save.ok.button.text"));
  }

  @Override
  protected void doOKAction() {
    FileSaverDescriptor descriptor =
      new FileSaverDescriptor(AndroidBundle.message("android.ddms.screenshot.save.title"), "", SdkConstants.EXT_PNG);
    FileSaverDialog saveFileDialog = FileChooserFactory.getInstance().createSaveFileDialog(descriptor, myProject);
    VirtualFile baseDir = loadScreenshotPath();
    VirtualFileWrapper fileWrapper = saveFileDialog.save(baseDir, getDefaultFileName());
    if (fileWrapper == null) {
      return;
    }

    myScreenshotFile = fileWrapper.getFile();
    try {
      ImageIO.write(myDisplayedImageRef.get(), SdkConstants.EXT_PNG, myScreenshotFile);
    }
    catch (IOException e) {
      Messages.showErrorDialog(myProject,
                               AndroidBundle.message("android.ddms.screenshot.save.error", e),
                               AndroidBundle.message("android.ddms.actions.screenshot"));
      return;
    }

    VirtualFile virtualFile = fileWrapper.getVirtualFile();
    if (virtualFile != null) {
      PropertiesComponent properties = PropertiesComponent.getInstance(myProject);
      properties.setValue(SCREENSHOT_SAVE_PATH_KEY, virtualFile.getParent().getPath());
    }

    super.doOKAction();
  }

  private String getDefaultFileName() {
    Calendar now = Calendar.getInstance();
    return String.format("%s-%tF-%tH%tM%tS.png", myDevice != null ? "device" : "layout", now, now, now, now);
  }

  public File getScreenshot() {
    return myScreenshotFile;
  }

  private VirtualFile loadScreenshotPath() {
    PropertiesComponent properties = PropertiesComponent.getInstance(myProject);
    String lastPath = properties.getValue(SCREENSHOT_SAVE_PATH_KEY);

    if (lastPath != null) {
      return LocalFileSystem.getInstance().findFileByPath(lastPath);
    } else {
      return myProject.getBaseDir();
    }
  }
}
