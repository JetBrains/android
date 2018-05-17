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
package com.android.tools.idea.rendering.webp;

import static com.intellij.util.Alarm.ThreadToUse.POOLED_THREAD;
import static com.intellij.util.ui.update.Update.LOW_PRIORITY;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.update.MergingUpdateQueue;
import com.intellij.util.ui.update.Update;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.List;
import javax.imageio.ImageIO;
import javax.swing.Action;
import javax.swing.Icon;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JSlider;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class WebpPreviewDialog extends DialogWrapper implements ChangeListener, KeyListener {
  private final Project myProject;
  private final WebpConversionSettings mySettings;
  private JBLabel myPngSizeLabel;
  private JSlider myQualitySlider;
  private JBLabel myQualityText;
  private JPanel myPanel;
  private JBLabel myFileIndexLabel;
  @SuppressWarnings("unused")
  private JComponent myPreviewImage;
  private JBLabel myWebpSizeLabel;
  private JBLabel myQualityLabel;
  private JBLabel myPngLabel;

  private BufferedImage myPngImage;
  private BufferedImage myWebpImage;
  private BufferedImage myDeltaImage;
  private int myPngBytes;
  private int myWebpBytes;
  private final boolean myAllowLossless;
  private final List<WebpConvertedFile> myFiles;
  private int myFileIndex;
  private PrevAction myPrevAction;
  private NextAction myNextAction;
  private final MergingUpdateQueue myRenderingQueue;
  private AcceptAllAction myAcceptAll;

  WebpPreviewDialog(@NotNull Project project, @NotNull WebpConversionSettings settings, @NotNull List<WebpConvertedFile> files) {
    super(project);
    setTitle("Preview and Adjust Converted Images");
    myProject = project;
    mySettings = settings;
    myFiles = files;
    myQualitySlider.setValue(settings.quality);
    myQualitySlider.addChangeListener(this);
    myAllowLossless = settings.allowLossless;
    init();
    myRenderingQueue = new MergingUpdateQueue(WebpPreviewDialog.class.getSimpleName(), 50, true,
                                              null, getDisposable(), null, POOLED_THREAD);
    myRenderingQueue.setRestartTimerOnAdd(true);
    addKeyListener(this);
    myPanel.addKeyListener(this);
    myPreviewImage.setFocusable(true);
    myPreviewImage.requestFocus();

    selectImage(0);
  }

  private void selectImage(int index) {
    myFileIndex = index;
    myPngImage = null;
    myPngBytes = 0;
    myWebpImage = null;
    myWebpBytes = 0;
    myDeltaImage = null;

    myPrevAction.setEnabled(myFileIndex > 0);
    boolean isLast = myFileIndex == myFiles.size() - 1;
    myNextAction.setEnabled(!isLast);
    myAcceptAll.putValue(Action.NAME, isLast ? "Finish" : "Accept All");

    myPanel.repaint();
    requestUpdatePreview();
  }

  @Nullable
  @Override
  protected JComponent createCenterPanel() {
    myPanel.setPreferredSize(JBUI.size(1200, 600));
    return myPanel;
  }

  private void createUIComponents() {
    myPreviewImage = new JComponent() {
      @Override
      protected void paintComponent(Graphics g) {
        Graphics2D graphics = (Graphics2D)g.create();
        try {
          int width = getWidth();
          int height = getHeight();
          int third = width / 3;

          graphics.setColor(JBColor.WHITE);
          graphics.fillRect(0, 0, width, height);

          if (myPngImage == null) {
            Icon icon = AllIcons.Process.Big.Step_8;
            icon.paintIcon(this, g, width / 2 - icon.getIconWidth() / 2, height / 2 - icon.getIconHeight() / 2);
            return;
          }

          // Decide whether we're constrained by width or height
          int imageWidth = myPngImage.getWidth();
          int imageHeight = myPngImage.getHeight();
          if (imageWidth < third && imageHeight < height) {
            // Image totally fits: no need to zoom. Show 1x
            int centerX = (third - imageWidth) / 2;
            int centerY = (height - imageHeight) / 2;

            graphics.drawImage(myPngImage, null, centerX, centerY);
            if (myDeltaImage != null) {
              graphics.drawImage(myDeltaImage, null, third + centerX, centerY);
            }
            if (myWebpImage != null) {
              graphics.drawImage(myWebpImage, null, 2 * third + centerX, centerY);
            }
            // TODO: Show 2x, 4x versions too? Or add a zoom?
          }
          else {
            // Zoom
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            double scale = Math.min(height / (double)imageHeight, third / (double)imageWidth);
            int dx = (int)(third - (imageWidth * scale)) / 2;
            int dy = (int)(height - (imageHeight * scale)) / 2;
            graphics.drawImage(myPngImage, dx, dy, (int)(imageWidth * scale), (int)(imageHeight * scale), null);
            if (myDeltaImage != null) {
              graphics.drawImage(myDeltaImage, third + dx, dy, (int)(imageWidth * scale), (int)(imageHeight * scale), null);
            }
            if (myWebpImage != null) {
              graphics.drawImage(myWebpImage, 2 * third + dx, dy, (int)(imageWidth * scale), (int)(imageHeight * scale), null);
            }
          }

          graphics.setColor(JBColor.GRAY);
          graphics.drawLine(third, 0, third, height);
          graphics.drawLine(2 * third, 0, 2 * third, height);
        } finally {
          graphics.dispose();
        }
      }
    };
  }

  protected class PrevAction extends DialogWrapperAction {
    private PrevAction() {
      super("Previous");
    }

    @Override
    protected void doAction(ActionEvent e) {
      selectPrevious();
    }
  }

  private void selectPrevious() {
    if (myFileIndex > 0) {
      selectImage(myFileIndex - 1);
    }
  }

  private void selectNext() {
    if (myFileIndex < myFiles.size() - 1) {
      selectImage(myFileIndex + 1);
    }
  }

  protected class NextAction extends DialogWrapperAction {
    private NextAction() {
      super("Next");
    }

    @Override
    protected void doAction(ActionEvent e) {
      selectNext();
    }
  }

  protected class AcceptAllAction extends DialogWrapperAction {
    private AcceptAllAction() {
      super("Accept All");
    }

    @Override
    protected void doAction(ActionEvent e) {
      doOKAction();
    }
  }

  @Override
  protected @NotNull Action[] createActions() {
    myPrevAction = new PrevAction();
    myNextAction = new NextAction();
    myAcceptAll = new AcceptAllAction();

    myPrevAction.setEnabled(false);
    myNextAction.setEnabled(myFiles.size() > 1);

    if (SystemInfo.isMac) {
      return new Action[] { getCancelAction(), myPrevAction, myNextAction, myAcceptAll };
    }

    return new Action[] { myPrevAction, myNextAction, myAcceptAll, getCancelAction() };
  }

  private void updatePreview() {
    if (myFiles.isEmpty()) {
      return;
    }

    WebpConvertedFile convertedFile = myFiles.get(myFileIndex);
    if (myPngImage == null) {
      try {
        myPngImage = convertedFile.getSourceImage();
        myPngBytes = (int)convertedFile.sourceFile.getLength();
      }
      catch (IOException ignore) {
      }
    }

    if (myPngImage != null) {
      mySettings.quality = myQualitySlider.getValue();
      mySettings.lossless = myAllowLossless && mySettings.quality == 100;

      convertedFile.convert(myPngImage, mySettings);
      byte[] bytes = convertedFile.encoded;
      try {
        myWebpImage = bytes != null ? ImageIO.read(new ByteArrayInputStream(bytes)) : null;
        myWebpBytes = bytes != null ? bytes.length : 0;

        if (myWebpImage != null) {
          int imageWidth = myPngImage.getWidth();
          int imageHeight = myPngImage.getHeight();
          int level = JBColor.WHITE.getRed();
          boolean light = level >= 128;
          //noinspection UndesirableClassUsage
          myDeltaImage = new BufferedImage(imageWidth, imageHeight, BufferedImage.TYPE_INT_ARGB);
          if (!mySettings.lossless) {
            for (int y = 0; y < imageHeight; y++) {
              for (int x = 0; x < imageWidth; x++) {
                int webpPixel = myWebpImage.getRGB(x, y);
                int pngPixel = myPngImage.getRGB(x, y);

                int webpBlue = webpPixel & 0xFF;
                int pngBlue = pngPixel & 0xFF;
                int blueDelta = Math.abs(webpBlue - pngBlue);

                webpPixel >>>= 8;
                pngPixel >>>= 8;

                int webpGreen = webpPixel & 0xFF;
                int pngGreen = pngPixel & 0xFF;
                int greenDelta = Math.abs(webpGreen - pngGreen);

                webpPixel >>>= 8;
                pngPixel >>>= 8;

                int webpRed = webpPixel & 0xFF;
                int pngRed = pngPixel & 0xFF;
                int redDelta = Math.abs(webpRed - pngRed);

                pngPixel >>>= 8;
                int pngAlpha = pngPixel & 0xFF;
                int alpha = pngAlpha << 24;

                int deltaColor;
                if (light) {
                  deltaColor = alpha | (level - redDelta) << 16 | (level - greenDelta) << 8 | (level - blueDelta);
                }
                else {
                  deltaColor = alpha | (level + redDelta) << 16 | (level + greenDelta) << 8 | (level + blueDelta);
                }
                myDeltaImage.setRGB(x, y, deltaColor);
              }
            }
          }
        } else {
          myDeltaImage = null;
        }
      } catch (IOException ignore) {
      }

      UIUtil.invokeLaterIfNeeded(() -> {
        myPngSizeLabel.setText(ConvertToWebpAction.formatSize(myPngBytes));
        int percentage = myWebpBytes * 100 / myPngBytes;
        myWebpSizeLabel.setText(ConvertToWebpAction.formatSize(myWebpBytes) + " (" + percentage + "% of original size)");
        VirtualFile file = myFiles.get(myFileIndex).sourceFile;
        String path = VfsUtilCore.getRelativePath(file, myProject.getBaseDir());
        myFileIndexLabel.setText(path + " (" + (myFileIndex + 1) + "/" + myFiles.size() + ")");
        myQualityLabel.setText(mySettings.lossless ? "Lossless" : "Quality (Default 75%)");

        String extension = convertedFile.sourceFile.getExtension();
        if (extension == null) {
          extension = "Source";
        } else {
          extension = StringUtil.toUpperCase(extension);
        }
        myPngLabel.setText(extension);

        repaint();
      });
    }
  }

  private void requestUpdatePreview() {
    // This method will be removed once we only do direct rendering (see RenderTask.render(Graphics2D))
    // This update is low priority so the model updates take precedence
    myRenderingQueue.queue(new Update(WebpPreviewDialog.class.getSimpleName(), LOW_PRIORITY) {
      @Override
      public void run() {
        if (myProject.isDisposed()) {
          return;
        }

        updatePreview();
      }

      @Override
      public boolean canEat(Update update) {
        return this.equals(update);
      }
    });
  }

  private void updateQualityText() {
    myQualityText.setText(myQualitySlider.getValue() + "%");
  }

  // Implements ChangeListener

  @Override
  public void stateChanged(ChangeEvent e) {
    // Slider dragged
    requestUpdatePreview();
    updateQualityText();
  }

  // ---- Implements KeyListener ----

  @Override
  public void keyTyped(KeyEvent e) {
    if (e.getKeyCode() == KeyEvent.VK_DOWN || e.getKeyCode() == KeyEvent.VK_RIGHT) {
      selectNext();
    }
    else if (e.getKeyCode() == KeyEvent.VK_UP || e.getKeyCode() == KeyEvent.VK_LEFT) {
      selectPrevious();
    }
  }

  @Override
  public void keyPressed(KeyEvent e) {
  }

  @Override
  public void keyReleased(KeyEvent e) {
  }
}