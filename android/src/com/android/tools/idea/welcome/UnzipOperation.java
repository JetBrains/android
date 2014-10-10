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
package com.android.tools.idea.welcome;

import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.download.DownloadableFileDescription;
import com.intellij.util.io.ZipUtil;

import java.io.File;
import java.io.IOException;

/**
 * Unzips the archives required for Android Studio setup.
 */
public final class UnzipOperation extends PreinstallOperation {
  public UnzipOperation(InstallContext context) {
    super(context, 0.3);
  }

  private File unzip(File archive, File tempDirectory) throws WizardException {
    myContext.getProgressStep().print(String.format("Unpacking %s\n", archive.getName()), ConsoleViewContentType.SYSTEM_OUTPUT);
    File dir = new File(tempDirectory, archive.getName() + "-unpacked");
    do {
      try {
        ZipUtil.extract(archive, dir, null);
        if (archive.getCanonicalPath().startsWith(myContext.getTempDirectory().getCanonicalPath())) {
          FileUtil.delete(archive); // Even if this fails, there's nothing we can do, and the folder should be deleted on exit anyways
        }
        return dir;
      }
      catch (IOException e) {
        String failure = String.format("Unable to unzip file %1$s", archive.getName());
        String cause = e.getMessage();
        String message = failure + (StringUtil.isEmptyOrSpaces(cause) ? "." : ": " + cause);
        promptToRetry(message, failure + ":", e);
      }
    }
    while (true);
  }

  @Override
  protected void perform() throws WizardException {
    long allFiles = 0, done = 0;
    ProgressIndicator progressIndicator = ProgressManager.getInstance().getProgressIndicator();
    progressIndicator.start();
    progressIndicator.setText("Unpacking archives");
    for (File file : myContext.getDownloadedFiles()) {
      allFiles += file.length();
    }
    try {
      for (DownloadableFileDescription description : myContext.getFilesToDownload()) {
        if (progressIndicator.isCanceled()) {
          break;
        }
        File first = myContext.getDownloadLocation(description);
        myContext.setExpandedLocation(description, unzip(first, myContext.getTempDirectory()));
        done += first.length();
        progressIndicator.setFraction(1.0 * done / allFiles);
      }
    }
    finally {
      progressIndicator.stop();
    }
  }
}
