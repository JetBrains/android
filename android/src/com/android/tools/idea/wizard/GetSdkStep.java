/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.android.tools.idea.wizard;

import com.android.tools.idea.sdk.DefaultSdks;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileChooserDialog;
import com.intellij.openapi.fileChooser.FileChooserFactory;
import com.intellij.openapi.fileChooser.FileSaverDescriptor;
import com.intellij.openapi.fileChooser.ex.FileChooserDialogImpl;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileWrapper;
import com.intellij.platform.templates.github.DownloadUtil;
import com.intellij.platform.templates.github.Outcome;
import com.intellij.platform.templates.github.ZipUtil;
import org.jetbrains.android.sdk.AndroidSdkType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.util.concurrent.Callable;

import static com.android.tools.idea.wizard.ScopedStateStore.Key;
import static com.android.tools.idea.wizard.ScopedStateStore.Scope.STEP;
import static com.android.tools.idea.wizard.ScopedStateStore.createKey;

/**
 * Step to get the sdk location from the user.
 */
public class GetSdkStep extends DynamicWizardStepWithHeaderAndDescription {
  private static final Logger LOG = Logger.getInstance(GetSdkStep.class);
  // TODO: Update these to a stable link
  private static final String MAC_SDK_URL = "http://dl.google.com/android/android-sdk_r22.6.2-macosx.zip";
  private static final String LINUX_SDK_URL = "http://dl.google.com/android/android-sdk_r22.6.2-linux.tgz";
  private static final String WINDOWS_SDK_URL = "http://dl.google.com/android/android-sdk_r22.6.2-windows.zip";
  private TextFieldWithBrowseButton mySdkLocationField;
  private JPanel myPanel;
  private JButton myDownloadANewSDKButton;

  private static final Key<String> SDK_PATH_KEY = createKey("sdkPath", STEP, String.class);

  public GetSdkStep(@NotNull Disposable parentDisposable) {
    super("SDK Setup", "You may use an existing SDK or download a new one", null, parentDisposable);
    setBodyComponent(myPanel);
  }

  @Override
  public void init() {
    register(SDK_PATH_KEY, mySdkLocationField);
    File androidHome = DefaultSdks.getDefaultAndroidHome();
    if (androidHome != null) {
      myState.put(SDK_PATH_KEY, androidHome.getPath());
    }

    mySdkLocationField.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent actionEvent) {
        String path = getSdkPath(mySdkLocationField.getText());
        mySdkLocationField.setText(path == null ? "" : path);
      }
    });

    myDownloadANewSDKButton.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent actionEvent) {
        String sdkPath = getSaveLocation(myState.get(SDK_PATH_KEY));
        if (sdkPath != null) {
          boolean success = downloadSdkToPath(sdkPath);
          if (success) {
            myState.put(SDK_PATH_KEY, sdkPath);
          } else {
            setErrorHtml("Download failed, please try again.");
          }
        }
      }
    });

    super.init();
  }

  private boolean downloadSdkToPath(@NotNull String sdkPath) {
    File outputFile = new File(sdkPath);
    File parentDir = outputFile.getParentFile();
    if (parentDir == null) {
      setErrorHtml("You may not choose an empty (or root) path as an output directory");
      return false;
    }

    final File tempZipFile = new File(parentDir, outputFile.getName() + ".zip");


    Outcome<File> outcome = DownloadUtil.provideDataWithProgressSynchronously(
      null,
      "Installing SDK",
      "Downloading SDK Archive" + DownloadUtil.CONTENT_LENGTH_TEMPLATE + " ...",
      new Callable<File>() {
        @Override
        public File call() throws Exception {
          ProgressIndicator progress = ProgressManager.getInstance().getProgressIndicator();
          String downloadUrl = getDownloadUrl();
          if (downloadUrl == null) {
            setErrorHtml("We cannot recognize your OS. Please visit http://developer.android.com/sdk/index.html and select" +
                         "the appropriate SDK bundle.");
            return tempZipFile;
          }
          DownloadUtil.downloadAtomically(progress, downloadUrl, tempZipFile);
          return tempZipFile;
        }
      }, null
    );

    Exception e = outcome.getException();
    if (e != null) {
      setErrorHtml("Could not download SDK: " + e.getMessage());
      return false;
    }
    try {
      ZipUtil.unzipWithProgressSynchronously(null, "Extracting SDK", tempZipFile, outputFile, true);
      FileUtil.delete(tempZipFile);
    }
    catch (Exception e2) {
      setErrorHtml("Installing SDK failed: " + e2.getMessage());
      return false;
    }
    return true;
  }

  @Override
  public boolean commitStep() {
    String sdkPath = myState.get(SDK_PATH_KEY);
    if (sdkPath == null) {
      return false;
    }
    final File sdkFile = new File(sdkPath);
    if (!sdkFile.exists() || !AndroidSdkType.validateAndroidSdk(sdkPath).getFirst()) {
      return false;
    }
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      @Override
      public void run() {
        DefaultSdks.setDefaultAndroidHome(sdkFile);
      }
    });
    return true;
  }

  @Override
  public boolean validate() {
    setErrorHtml("");
    String path = myState.get(SDK_PATH_KEY);
    if (path == null || StringUtil.isEmpty(path)) {
      setErrorHtml("Android SDK path not specified.");
      return false;
    }

    Pair<Boolean, String> validationResult = AndroidSdkType.validateAndroidSdk(path);
    String error = validationResult.getSecond();
    if (!validationResult.getFirst()) {
      setErrorHtml(String.format("Invalid Android SDK (%1$s): %2$s", path, error));
      return false;
    } else {
      return true;
    }
  }

  @Override
  public boolean isStepVisible() {
    return DefaultSdks.getDefaultAndroidHome() == null;
  }

  @NotNull
  @Override
  public String getStepName() {
    return "Find or download SDK";
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    return mySdkLocationField;
  }

  @Nullable
  private String getSdkPath(@Nullable String currentPath) {
    VirtualFile currentFile = null;
    if (currentPath != null && !currentPath.isEmpty()) {
      currentFile = VfsUtil.findFileByIoFile(new File(currentPath), false);
    }
    FileChooserDescriptor chooserDescriptor = AndroidSdkType.getInstance().getHomeChooserDescriptor();
    FileChooserDialog chooser = new FileChooserDialogImpl(chooserDescriptor, (Project)null);
    VirtualFile[] files = chooser.choose(currentFile, null);
    if (files.length == 0) {
      return null;
    } else {
      return files[0].getPath();
    }
  }

  @Nullable
  private String getSaveLocation(@Nullable String currentPath) {
    VirtualFile currentFile = null;
    if (currentPath != null && !currentPath.isEmpty()) {
      currentFile = VfsUtil.findFileByIoFile(new File(currentPath), false);
    }
    FileSaverDescriptor fileSaverDescriptor = new FileSaverDescriptor("SDK location", "Please choose an installation location for your SDK");
    VirtualFileWrapper fileWrapper =
      FileChooserFactory.getInstance().createSaveFileDialog(fileSaverDescriptor, (Project)null).save(currentFile, "android_sdk");
    if (fileWrapper != null) {
      return fileWrapper.getFile().getPath();
    } else {
      return null;
    }
  }

  @Nullable
  private String getDownloadUrl() {
    if (SystemInfo.isLinux) {
      return LINUX_SDK_URL;
    } else if (SystemInfo.isWindows) {
      return WINDOWS_SDK_URL;
    } else if (SystemInfo.isMac) {
      return MAC_SDK_URL;
    } else {
      return null;
    }
  }
}
