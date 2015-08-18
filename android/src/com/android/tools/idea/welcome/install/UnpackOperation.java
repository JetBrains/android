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
package com.android.tools.idea.welcome.install;

import com.android.SdkConstants;
import com.android.tools.idea.welcome.wizard.WelcomeUIUtils;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.process.CapturingAnsiEscapesAwareProcessHandler;
import com.intellij.execution.process.ProcessAdapter;
import com.intellij.execution.process.ProcessEvent;
import com.intellij.execution.process.ProcessOutputTypes;
import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.platform.templates.github.ZipUtil;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;

/**
 * Unzips the archives required for Android Studio setup.
 */
public final class UnpackOperation extends InstallOperation<File, File> {
  public static final String EXTRACT_OPERATION_OUTPUT = "x ";
  public static final String TAR_FLAGS_EXTRACT_UNPACK_VERBOSE_FILENAME_TARGETDIR = "xzvfC";
  public static final String DEFAULT_TAR_EXECUTABLE_PATH = "/usr/bin/tar";
  private final InstallContext myContext;

  public UnpackOperation(@NotNull InstallContext context, double progressShare) {
    super(context, progressShare);
    myContext = context;
  }

  @NotNull
  private static File unzip(File archive, File destination, @NotNull InstallContext context, ProgressIndicator progressIndicator) throws IOException {
    ZipUtil.unzip(progressIndicator, destination, archive, null, null, true);
    if (archive.getCanonicalPath().startsWith(context.getTempDirectory().getCanonicalPath())) {
      FileUtil.delete(archive); // Even if this fails, there's nothing we can do, and the folder should be deleted on exit anyways
    }
    return destination;
  }

  /**
   * @throws IOException     when tar fails in a way that we may retry the operation
   * @throws WizardException if retry is not possible (e.g. no tar executable)
   */
  @NotNull
  private static File untar(final File archive, File destination, final InstallContext context, final ProgressIndicator indicator)
    throws IOException, WizardException {
    if (!destination.mkdirs()) {
      throw new WizardException("Cannot create temporary directory to extract files");
    }
    indicator.start();
    indicator.setFraction(0.0); // 0%
    try {
      GeneralCommandLine line =
        new GeneralCommandLine(getTarExecutablePath(), TAR_FLAGS_EXTRACT_UNPACK_VERBOSE_FILENAME_TARGETDIR, archive.getAbsolutePath(),
                               destination.getAbsolutePath());
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
      return destination;
    }
    catch (ExecutionException e) {
      throw new WizardException("Unable to run tar utility");
    }
    finally {
      indicator.setFraction(1.0); // 100%
      indicator.stop();
    }
  }

  @NotNull
  private static String getTarExecutablePath() {
    File file = new File(DEFAULT_TAR_EXECUTABLE_PATH);
    if (file.isFile()) {
      return file.getAbsolutePath();
    }
    else {
      return "tar"; // Some strange distro or Windows... Hope the environment is properly configured...
    }
  }

  @NotNull
  @Override
  protected File perform(@NotNull ProgressIndicator indicator, @NotNull File archive) throws WizardException {
    String fileName = archive.getName();
    ArchiveType archiveType = ArchiveType.fromFileName(fileName);
    myContext.print(String.format("Unpacking %s\n", fileName), ConsoleViewContentType.SYSTEM_OUTPUT);
    File dir = new File(myContext.getTempDirectory(), fileName + "-unpacked");
    do {
      try {
        return archiveType.unpack(archive, dir, myContext, indicator);
      }
      catch (IOException e) {
        String failure = String.format("Unable to unpack file %1$s", fileName);
        String message = WelcomeUIUtils.getMessageWithDetails(failure, e.getMessage());
        promptToRetry(message + " Make sure you have enough disk space on destination drive and retry.", message, e);
      }
    }
    while (true);
  }

  @Override
  public void cleanup(@NotNull File result) {
    if (result.exists()) {
      FileUtil.delete(result);
    }
  }

  /**
   * Different archive types welcome wizard can download and unpack.
   */
  private enum ArchiveType {
    ZIP, TAR, NOT_AN_ARCHIVE;

    private static final String[] TAR_EXTENSIONS = {"tgz", "tar", "tar.gz"};
    private static final String[] ZIP_EXTENSIONS = {SdkConstants.EXT_ZIP};

    @NotNull
    public static ArchiveType fromFileName(@NotNull String fileName) {
      String lowerCaseName = fileName.toLowerCase();
      if (extensionIsOneOf(lowerCaseName, TAR_EXTENSIONS)) {
        return TAR;
      }
      else if (extensionIsOneOf(lowerCaseName, ZIP_EXTENSIONS)) {
        return ZIP;
      }
      else {
        return NOT_AN_ARCHIVE;
      }
    }

    private static boolean extensionIsOneOf(@NotNull String name, @NotNull String[] extensions) {
      for (String extension : extensions) {
        if (FileUtilRt.extensionEquals(name, extension)) {
          return true;
        }
      }
      return false;
    }

    public File unpack(@NotNull File archive, @NotNull File destination,
                       @NotNull InstallContext context, @NotNull ProgressIndicator indicator)
      throws IOException, WizardException {
      switch (this) {
        case ZIP:
          return unzip(archive, destination, context, indicator);
        case TAR:
          return untar(archive, destination, context, indicator);
        case NOT_AN_ARCHIVE:
          throw new WizardException(String.format("Unrecognized archive file format for file %s", archive.getName()));
        default:
          throw new IllegalArgumentException(String.format("Archive %s has format %s", archive.getName(), name()));
      }
    }
  }
}
