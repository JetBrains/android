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
import com.android.tools.idea.rendering.ImageUtils;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
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
import com.intellij.openapi.vfs.*;
import com.intellij.ui.components.JBScrollPane;
import org.intellij.images.editor.ImageEditor;
import org.intellij.images.editor.ImageFileEditor;
import org.intellij.images.editor.ImageZoomModel;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.Calendar;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

public class ScreenshotViewer extends DialogWrapper implements DataProvider {
  @NonNls private static final String SCREENSHOT_VIEWER_DIMENSIONS_KEY = "ScreenshotViewer.Dimensions";

  private static VirtualFile ourLastSavedFolder = null;

  private final Project myProject;
  private final IDevice myDevice;

  private final VirtualFile myBackingVirtualFile;
  private final ImageFileEditor myImageFileEditor;
  private final FileEditorProvider myProvider;

  private final List<DeviceArtDescriptor> myDeviceArtSpecs;

  private JPanel myPanel;
  private JButton myRefreshButton;
  private JButton myRotateButton;
  private JBScrollPane myScrollPane;
  private JCheckBox myFrameScreenshotCheckBox;
  private JComboBox myDeviceArtCombo;
  private JCheckBox myDropShadowCheckBox;
  private JCheckBox myScreenGlareCheckBox;

  /** Angle in degrees by which the screenshot from the device has been rotated. One of 0, 90, 180 or 270. */
  private int myRotationAngle = 0;

  /**
   * Reference to the screenshot obtained from the device and then rotated by {@link #myRotationAngle} degrees.
   * Accessed from both EDT and background threads.
   */
  private AtomicReference<BufferedImage> mySourceImageRef = new AtomicReference<BufferedImage>();

  /** Reference to the framed screenshot displayed on screen. Accessed from both EDT and background threads. */
  private AtomicReference<BufferedImage> myDisplayedImageRef = new AtomicReference<BufferedImage>();

  /** User specified destination where the screenshot is saved. */
  private File myScreenshotFile;

  public ScreenshotViewer(@NotNull Project project,
                          @NotNull BufferedImage image,
                          @NotNull File backingFile,
                          @Nullable IDevice device) {
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

    ActionListener l = new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent actionEvent) {
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
      }
    };

    myRefreshButton.addActionListener(l);
    myRotateButton.addActionListener(l);
    myFrameScreenshotCheckBox.addActionListener(l);
    myDeviceArtCombo.addActionListener(l);
    myDropShadowCheckBox.addActionListener(l);
    myScreenGlareCheckBox.addActionListener(l);

    myDeviceArtSpecs = DeviceArtDescriptor.getDescriptors(null);
    String[] titles = new String[myDeviceArtSpecs.size()];
    for (int i = 0; i < myDeviceArtSpecs.size(); i++) {
      titles[i] = myDeviceArtSpecs.get(i).getName();
    }
    DefaultComboBoxModel model = new DefaultComboBoxModel(titles);
    myDeviceArtCombo.setModel(model);
    myDeviceArtCombo.setSelectedIndex(0);

    init();
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
        frameScreenshot(myRotationAngle);
      }
    }.queue();
  }

  private void doRotateScreenshot() {
    myRotationAngle = (myRotationAngle + 90) % 360;
    frameScreenshot(90);
  }

  private void doFrameScreenshot() {
    boolean shouldFrame = myFrameScreenshotCheckBox.isSelected();

    myDeviceArtCombo.setEnabled(shouldFrame);
    myDropShadowCheckBox.setEnabled(shouldFrame);
    myScreenGlareCheckBox.setEnabled(shouldFrame);

    if (shouldFrame) {
      frameScreenshot(0);
    } else {
      myDisplayedImageRef.set(mySourceImageRef.get());
      updateEditorImage();
    }
  }

  private void frameScreenshot(int rotateByAngle) {
    DeviceArtDescriptor spec = myDeviceArtSpecs.get(myDeviceArtCombo.getSelectedIndex());
    boolean shadow = myDropShadowCheckBox.isSelected();
    boolean reflection = myScreenGlareCheckBox.isSelected();

    new ImageProcessorTask(myProject, mySourceImageRef.get(), rotateByAngle, spec, shadow, reflection) {
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
    private final DeviceArtDescriptor mySpec;
    private final boolean myAddShadow;
    private final boolean myAddReflection;

    private BufferedImage myRotatedImage;
    private BufferedImage myProcessedImage;

    public ImageProcessorTask(@Nullable Project project,
                              @NotNull BufferedImage srcImage,
                              int rotateByAngle,
                              @Nullable DeviceArtDescriptor spec,
                              boolean addShadow,
                              boolean addReflection) {
      super(project, AndroidBundle.message("android.ddms.screenshot.image.processor.task.title"), false);

      mySrcImage = srcImage;
      myRotationAngle = rotateByAngle;
      mySpec = spec;
      myAddShadow = addShadow;
      myAddReflection = addReflection;
    }

    @Override
    public void run(@NotNull ProgressIndicator indicator) {
      if (myRotationAngle != 0) {
        myRotatedImage = ImageUtils.rotateByRightAngle(mySrcImage, myRotationAngle);
      } else {
        myRotatedImage = mySrcImage;
      }

      if (mySpec != null) {
        myProcessedImage = DeviceArtPainter.createFrame(myRotatedImage, mySpec, myAddShadow, myAddReflection);
      } else {
        myProcessedImage = myRotatedImage;
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
    getOKAction().putValue(Action.NAME, AndroidBundle.message(
      "android.ddms.screenshot.save.ok.button.text"));
  }

  @Override
  protected void doOKAction() {
    FileSaverDescriptor descriptor =
      new FileSaverDescriptor(AndroidBundle.message("android.ddms.screenshot.save.title"), "", SdkConstants.EXT_PNG);
    FileSaverDialog saveFileDialog = FileChooserFactory.getInstance().createSaveFileDialog(descriptor, myProject);
    VirtualFile baseDir = ourLastSavedFolder != null ? ourLastSavedFolder : myProject.getBaseDir();
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
      //noinspection AssignmentToStaticFieldFromInstanceMethod
      ourLastSavedFolder = virtualFile.getParent();
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
}
