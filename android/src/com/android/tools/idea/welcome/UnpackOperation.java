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

import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.process.CapturingAnsiEscapesAwareProcessHandler;
import com.intellij.execution.process.ProcessAdapter;
import com.intellij.execution.process.ProcessEvent;
import com.intellij.execution.process.ProcessOutputTypes;
import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.platform.templates.github.ZipUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;

/**
 * Unzips the archives required for Android Studio setup.
 */
public final class UnpackOperation extends PreinstallOperation<File> {
  public static final String EXTRACT_OPERATION_OUTPUT = "x ";
  public static final String TAR_FLAGS_EXTRACT_UNPACK_VERBOSE_FILENAME_TARGETDIR = "xzvfC";
  public static final String DEFAULT_TAR_EXECUTABLE_PATH = "/usr/bin/tar";
  private final InstallContext myContext;
  private final File myArchive;

  public UnpackOperation(@NotNull InstallContext context, @NotNull File archive, double progressShare) {
    super(context, progressShare);
    myContext = context;
    myArchive = archive;
  }

  private File unpack(ProgressIndicator progressIndicator, File archive, File tempDirectory) throws WizardException {
    String fileName = archive.getName();
    ArchiveType archiveType = ArchiveType.fromFileName(fileName);
    myContext.print(String.format("Unpacking %s\n", fileName), ConsoleViewContentType.SYSTEM_OUTPUT);
    File dir = new File(tempDirectory, fileName + "-unpacked");
    do {
      try {
        switch (archiveType) {
          case ZIP:
            return unzip(progressIndicator, archive, dir);
          case TAR:
            return untar(myContext, progressIndicator, archive, dir);
          case NOT_AN_ARCHIVE:
            throw new WizardException(String.format("Unrecognized archive file format for file %s", fileName));
        }
      }
      catch (IOException e) {
        String failure = String.format("Unable to unpack file %1$s", fileName);
        String message = WelcomeUIUtils.getMessageWithDetails(failure, e.getMessage());
        promptToRetry(message + " Make sure you have enough disk space on destination drive and retry.", message, e);
      }
    }
    while (true);
  }

  private File unzip(ProgressIndicator progressIndicator, File archive, File dir) throws IOException {
    ZipUtil.unzip(progressIndicator, dir, archive, null, null, true);
    if (archive.getCanonicalPath().startsWith(myContext.getTempDirectory().getCanonicalPath())) {
      FileUtil.delete(archive); // Even if this fails, there's nothing we can do, and the folder should be deleted on exit anyways
    }
    return dir;
  }

  /**
   * @throws IOException     when tar fails in a way that we may retry the operation
   * @throws WizardException if retry is not possible (e.g. no tar executable)
   */
  private static File untar(final InstallContext context, final ProgressIndicator indicator, final File file, File tempDir)
    throws IOException, WizardException {
    if (!tempDir.mkdirs()) {
      throw new WizardException("Cannot create temporary directory to extract files");
    }
    indicator.start();
    indicator.setFraction(0.0); // 0%
    try {
      GeneralCommandLine line =
        new GeneralCommandLine(getTarExecutablePath(), TAR_FLAGS_EXTRACT_UNPACK_VERBOSE_FILENAME_TARGETDIR, file.getAbsolutePath(),
                               tempDir.getAbsolutePath());
      CapturingAnsiEscapesAwareProcessHandler handler = new CapturingAnsiEscapesAwareProcessHandler(line);
      handler.addProcessListener(new ProcessAdapter() {
        @Override
        public void onTextAvailable(ProcessEvent event, Key outputType) {
          String string = event.getText();
          if (!StringUtil.isEmptyOrSpaces(string)) {
            if (string.startsWith(EXTRACT_OPERATION_OUTPUT)) { // Extract operation prefix
              String fileName = string.substring(EXTRACT_OPERATION_OUTPUT.length()).trim();
              indicator.setText(fileName);
            }
            else if (ProcessOutputTypes.STDOUT.equals(outputType)) {
              indicator.setText(string.trim());
            }
            else {
              context.print(string, ConsoleViewContentType.getConsoleViewType(outputType));
            }
          }
        }
      });
      if (handler.runProcess().getExitCode() != 0) {
        throw new IOException("Unable to unpack archive file");
      }
      return tempDir;
    }
    catch (ExecutionException e) {
      throw new WizardException("Unable to run tar utility");
    }
    finally {
      indicator.setFraction(1.0); // 100%
      indicator.stop();
    }
  }

  private static String getTarExecutablePath() {
    File file = new File(DEFAULT_TAR_EXECUTABLE_PATH);
    if (file.isFile()) {
      return file.getAbsolutePath();
    }
    else {
      return "tar"; // Some strange distro or Windows... Hope the environment is properly configured...
    }
  }

  @Nullable
  @Override
  protected File perform() throws WizardException {
    ProgressIndicator progressIndicator = ProgressManager.getInstance().getProgressIndicator();
    if (progressIndicator.isCanceled()) {
      return null;
    }
    return unpack(progressIndicator, myArchive, myContext.getTempDirectory());
  }
}
