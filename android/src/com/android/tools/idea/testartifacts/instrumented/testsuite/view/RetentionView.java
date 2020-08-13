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
package com.android.tools.idea.testartifacts.instrumented.testsuite.view;

import com.android.tools.idea.concurrency.AndroidExecutors;
import com.android.tools.idea.testartifacts.instrumented.RetentionConstantsKt;
import com.android.tools.idea.testartifacts.instrumented.testsuite.model.AndroidDevice;
import com.google.common.annotations.VisibleForTesting;
import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.diagnostic.Logger;
import java.awt.Image;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.image.BufferedImage;
import java.awt.image.ImageObserver;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.imageio.ImageIO;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextPane;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;
import org.apache.commons.compress.utils.FileNameUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

// TODO(yahan@) rework this view when we have the UI mock

/**
 * Shows the Android Test Retention artifacts
 */
public class RetentionView {
  private static final Logger LOG = Logger.getInstance(RetentionView.class);

  private class RetentionPanel extends JPanel implements DataProvider {
    private final String retentionArtifactRegrex = ".*-(failure[0-9]+).tar(.gz)?";
    private final Pattern retentionArtifactPattern = Pattern.compile(retentionArtifactRegrex);
    private File snapshotFile = null;
    private String snapshotId = "";
    private AndroidDevice device = null;

    public void setAndroidDevice(AndroidDevice device) {
      this.device = device;
    }

    public void setSnapshotFile(File snapshotFile) {
      this.snapshotFile = snapshotFile;
      snapshotId = "";
      if (snapshotFile == null) {
        return;
      }
      Matcher matcher = retentionArtifactPattern.matcher(snapshotFile.getName());
      if (matcher.find()) {
        snapshotId = matcher.group(1);
      }
    }

    public File getSnapshotFile() {
      return snapshotFile;
    }

    public AndroidDevice getAndroidDevice() {
      return device;
    }

    @Nullable
    @Override
    public Object getData(@NotNull String dataId) {
      if (dataId == RetentionConstantsKt.EMULATOR_SNAPSHOT_ID_KEY.getName()) {
        return snapshotId;
      } else if (dataId == RetentionConstantsKt.EMULATOR_SNAPSHOT_FILE_KEY.getName()) {
        return snapshotFile;
      } else if (dataId == RetentionConstantsKt.PACKAGE_NAME_KEY.getName()) {
        return packageName;
      } else if (dataId == RetentionConstantsKt.RETENTION_AUTO_CONNECT_DEBUGGER_KEY.getName()) {
        return true;
      } else if (dataId == RetentionConstantsKt.RETENTION_ON_FINISH_KEY.getName()) {
        return (Runnable)() -> myRetentionDebugButton.setEnabled(true);
      } else if (dataId == RetentionConstantsKt.DEVICE_NAME_KEY.getName()) {
        return device.getDeviceName();
      }
      return null;
    }
  }

  @NotNull private String packageName = "";
  private JPanel myRootPanel;
  private JButton myRetentionDebugButton;
  private JTextPane myInfoText;
  private JLabel myImageLabel;
  @VisibleForTesting
  Image image = null;
  private ImageObserver observer = new ImageObserver() {
    @Override
    public boolean imageUpdate(Image img, int infoflags, int x, int y, int width, int height) {
      if ((infoflags & WIDTH) == 0 || (infoflags & HEIGHT) == 0) {
        return true;
      }
      updateSnapshotImage(image, width, height);
      return false;
    }
  };

  /**
   * Returns the root panel.
   */
  @NotNull
  public JPanel getRootPanel() {
    return myRootPanel;
  }

  public void setPackageName(@NotNull String packageName) {
    this.packageName = packageName;
  }

  private void updateSnapshotImage(Image image, int imageWidth, int imageHeight) {
    int rootWidth = getRootPanel().getWidth();
    if (rootWidth == 0 || imageHeight <= 0 || imageWidth <= 0) {
      return;
    }
    int targetWidth = getRootPanel().getWidth() / 4;
    int targetHeight = targetWidth * imageHeight / imageWidth;
    Image newImage = image.getScaledInstance(targetWidth, targetHeight, Image.SCALE_DEFAULT);
    myImageLabel.setIcon(new ImageIcon(newImage));
  }

  public void setSnapshotFile(File snapshotFile) {
    ((RetentionPanel)myRootPanel).setSnapshotFile(snapshotFile);
    updateInfoText();
    myImageLabel.setIcon(null);
    image = null;
    if (snapshotFile != null) {
      AndroidExecutors.Companion.getInstance().getIoThreadExecutor().execute(() -> {
        try {
          InputStream inputStream = new FileInputStream(snapshotFile);
          if (FileNameUtils.getExtension(snapshotFile.getName().toLowerCase(Locale.getDefault())).equals("gz")) {
            inputStream = new GzipCompressorInputStream(inputStream);
          }
          TarArchiveInputStream tarInputStream = new TarArchiveInputStream(inputStream);
          TarArchiveEntry entry;
          while ((entry = tarInputStream.getNextTarEntry()) != null) {
            if (entry.getName().equals("screenshot.png")) {
              break;
            }
          }
          if (entry != null) {
            BufferedImage imageStream = ImageIO.read(tarInputStream);
            image = new ImageIcon(imageStream).getImage();
            updateSnapshotImage(image, image.getWidth(observer), image.getHeight(observer));
          }
        } catch (IOException e) {
          LOG.warn("Failed to load snapshot screenshot", e);
        }
      });
    }
  }

  private void updateInfoText() {
    String text = "";
    AndroidDevice device = ((RetentionPanel)myRootPanel).getAndroidDevice();
    File snapshotFile = ((RetentionPanel)myRootPanel).getSnapshotFile();
    if (device != null) {
      text += String.format(Locale.getDefault(), "AVD name: %s\n", device.getDeviceName());
    }
    if (snapshotFile != null) {
      text += String.format(
        Locale.getDefault(),
        "Snapshot file size: %d MB\nSnapshot file path: %s\n",
        snapshotFile.length() / 1024 / 1024,
        snapshotFile.getAbsolutePath());
    }
    myInfoText.setText(text);
  }

  public void setAndroidDevice(AndroidDevice device) {
    ((RetentionPanel)myRootPanel).setAndroidDevice(device);
    updateInfoText();
  }

  private void createUIComponents() {
    myRootPanel = new RetentionPanel();
    myRetentionDebugButton = new JButton();
    myRetentionDebugButton.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        myRetentionDebugButton.setEnabled(false);
        DataContext dataContext = DataManager.getInstance().getDataContext(myRootPanel);
        ActionManager.getInstance().getAction(RetentionConstantsKt.LOAD_RETENTION_ACTION_ID).actionPerformed(
          AnActionEvent.createFromDataContext("", null, dataContext));
      }
    });
    myRootPanel.addComponentListener(new ComponentAdapter() {
      @Override
      public void componentResized(ComponentEvent e) {
        if (image != null) {
          updateSnapshotImage(image, image.getWidth(observer), image.getHeight(observer));
        }
      }
    });
  }
}
