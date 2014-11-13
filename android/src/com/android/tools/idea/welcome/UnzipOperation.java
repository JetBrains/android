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
import com.intellij.platform.templates.github.ZipUtil;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;

/**
 * Unzips the archives required for Android Studio setup.
 */
public final class UnzipOperation extends PreinstallOperation<File> {
  private final InstallContext myContext;
  private final File myArchive;

  public UnzipOperation(InstallContext context, File archive, double progressShare) {
    super(context, progressShare);
    myContext = context;
    myArchive = archive;
  }

  private File unzip(ProgressIndicator progressIndicator, File archive, File tempDirectory) throws WizardException {
    myContext.print(String.format("Unpacking %s\n", archive.getName()), ConsoleViewContentType.SYSTEM_OUTPUT);
    File dir = new File(tempDirectory, archive.getName() + "-unpacked");
    do {
      try {
        ZipUtil.unzip(progressIndicator, dir, archive, null, null, true);
        if (archive.getCanonicalPath().startsWith(myContext.getTempDirectory().getCanonicalPath())) {
          FileUtil.delete(archive); // Even if this fails, there's nothing we can do, and the folder should be deleted on exit anyways
        }
        return dir;
      }
      catch (IOException e) {
        String failure = String.format("Unable to unzip file %1$s", archive.getName());
        String message = WelcomeUIUtils.getMessageWithDetails(failure, e.getMessage());
        promptToRetry(message + " Make sure you have enough disk space on destination drive and retry.", message, e);
      }
    }
    while (true);
  }

  @Nullable
  @Override
  protected File perform() throws WizardException {
    ProgressIndicator progressIndicator = ProgressManager.getInstance().getProgressIndicator();
    if (progressIndicator.isCanceled()) {
      return null;
    }
    return unzip(progressIndicator, myArchive, myContext.getTempDirectory());
  }
}
