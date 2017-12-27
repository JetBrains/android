/*
 * Copyright (C) 2015 The Android Open Source Project
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
import com.google.common.base.Joiner;
import com.google.common.collect.Lists;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.process.CapturingAnsiEscapesAwareProcessHandler;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.List;

/**
 * <p>Checks SDK install to ensure install may proceed.</p>
 * <p/>
 * <p>This is done by trying to run the mksdcard executable from the SDK emulator directory (falling back to the older tools directory).
 * This ensures that the tools are installed and that necessary shared libraries are present.</p>
 */
public class CheckSdkOperation extends InstallOperation<File, File> {
  private static final String ERROR_CANT_EXECUTE = "%1$s file is not a valid executable";
  private static final String ERROR_NO_TOOLS_DIR = "SDK tools directory is missing";
  private static final String MESSAGE_CANT_RUN_TOOL;
  private static final String ERROR_CANT_RUN_TOOL;
  private static final String URL_MISSING_LIBRARIES = "https://developer.android.com/studio/troubleshoot.html#linux-libraries";
  private static final String LINK_MISSING_LIBRARIES = "Show Android SDK web page";
  private static final String TOOL_NAME = "mksdcard" + (SystemInfo.isWindows ? ".exe" : "");

  static {
    ERROR_CANT_RUN_TOOL = "Unable to run " + TOOL_NAME + " SDK tool.";
    MESSAGE_CANT_RUN_TOOL = "<html><p>" + Joiner.on("</p><p>").join(getUnableToRunMessage()) + "</p></html>";
  }

  public CheckSdkOperation(InstallContext context) {
    super(context, 0);
  }

  private static Iterable<?> getUnableToRunMessage() {
    boolean isLinux64 = SystemInfo.isLinux && SystemInfo.is64Bit;

    String likelyReason = isLinux64
                          ? "One common reason for this is missing 32 bit compatibility libraries."
                          : "One common reason for this failure is missing required libraries";

    String message = "Unable to run <strong>" + TOOL_NAME + "</strong> SDK tool.";

    List<String> lines = Lists.newArrayList(message, likelyReason, "Please fix the underlying issue and retry.");
    if (isLinux64) {
      String docHyperlink = "<a href=\"" + URL_MISSING_LIBRARIES + "\">" + LINK_MISSING_LIBRARIES + "</a>";
      lines.add(docHyperlink);
    }
    return lines;
  }

  private static boolean checkCanRunSdkTool(File executable) throws ExecutionException {
    GeneralCommandLine commandLine = new GeneralCommandLine(executable.getAbsolutePath());
    CapturingAnsiEscapesAwareProcessHandler handler = new CapturingAnsiEscapesAwareProcessHandler(commandLine);
    final int exitCode = handler.runProcess().getExitCode();
    return exitCode == 1; // 1 means help was printed
  }

  private static boolean checkExecutePermission(@NotNull File executable) {
    if (executable.canExecute()) {
      return true;
    }
    else {
      return SystemInfo.isUnix && executable.setExecutable(true);
    }
  }

  private static boolean retryPrompt() {
    int button = Messages.showOkCancelDialog(MESSAGE_CANT_RUN_TOOL, "Android Studio", "Retry", "Cancel", Messages.getErrorIcon());
    return button == Messages.OK;
  }

  private static boolean checkRuns(File executable) {
    try {
      while (!checkCanRunSdkTool(executable)) {
        boolean shouldRetry = UIUtil.invokeAndWaitIfNeeded(CheckSdkOperation::retryPrompt);
        if (!shouldRetry) {
          return false;
        }
      }
    }
    catch (ExecutionException e) {
      return false;
    }
    return true;
  }

  @NotNull
  @Override
  protected File perform(@NotNull ProgressIndicator indicator, @NotNull File file) throws WizardException, InstallationCancelledException {
    File tool = new File(file, SdkConstants.FD_EMULATOR + File.separator + TOOL_NAME);
    if (!tool.isFile()) {
      tool = new File(file, SdkConstants.FD_TOOLS + File.separator + TOOL_NAME);
    }
    if (!tool.isFile()) {
      throw new WizardException(ERROR_NO_TOOLS_DIR);
    }
    if (!checkExecutePermission(tool)) {
      throw new WizardException(String.format(ERROR_CANT_EXECUTE, tool.getAbsoluteFile()));
    }
    if (!checkRuns(tool)) {
      throw new WizardException(ERROR_CANT_RUN_TOOL);
    }
    return file;
  }

  @Override
  public void cleanup(@NotNull File result) {
    // Nothing to do
  }
}
