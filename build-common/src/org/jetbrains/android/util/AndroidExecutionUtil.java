// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.android.util;

import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.*;

/**
 * Abstract external tool for compiler.
 *
 * @author Alexey Efimov
 */
public final class AndroidExecutionUtil {

  private AndroidExecutionUtil() {
  }

  @NotNull
    public static Map<AndroidCompilerMessageKind, List<String>> doExecute(String... argv) throws IOException {
    return doExecute(argv, Collections.<String, String>emptyMap());
  }

  @NotNull
  public static Map<AndroidCompilerMessageKind, List<String>> doExecute(String[] argv,
                                                                        @NotNull Map<? extends String, ? extends String> enviroment)
    throws IOException {
    final AndroidBuildTestingManager testingManager = AndroidBuildTestingManager.getTestingManager();
    final Process process;

    if (testingManager != null) {
      process = testingManager.getCommandExecutor().createProcess(argv, enviroment);
    }
    else {
      ProcessBuilder builder = new ProcessBuilder(argv);
      builder.environment().putAll(enviroment);
      process = builder.start();
    }
    ProcessResult result = readProcessOutput(process, StringUtil.join(argv, " "));
    Map<AndroidCompilerMessageKind, List<String>> messages = result.getMessages();
    int code = result.getExitCode();
    List<String> errMessages = messages.get(AndroidCompilerMessageKind.ERROR);

    if (code != 0 && errMessages.isEmpty()) {
      throw new IOException("Command \"" + concat(argv) + "\" execution failed with exit code " + code);
    }
    else {
      if (code == 0) {
        messages.get(AndroidCompilerMessageKind.WARNING).addAll(errMessages);
        errMessages.clear();
      }
      return messages;
    }
  }

  private static String concat(String... strs) {
    StringBuilder builder = new StringBuilder();
    for (int i = 0, n = strs.length; i < n; i++) {
      builder.append(strs[i]);
      if (i < n - 1) {
        builder.append(' ');
      }
    }
    return builder.toString();
  }

  @NotNull
  private static ProcessResult readProcessOutput(@NotNull Process process, @NotNull String commandLine) throws IOException {
    final AndroidOSProcessHandler handler = new AndroidOSProcessHandler(process, commandLine);
    handler.startNotify();
    handler.waitFor();
    int exitCode = handler.getProcess().exitValue();
    return new ProcessResult(handler.getInfoMessages(), handler.getErrorMessages(), exitCode);
  }

  private static final class ProcessResult {
    private final int myExitCode;
    private final Map<AndroidCompilerMessageKind, List<String>> myMessages;

    ProcessResult(List<String> information, List<String> error, int exitCode) {
      myExitCode = exitCode;
      myMessages = new HashMap<>(2);
      myMessages.put(AndroidCompilerMessageKind.INFORMATION, information);
      myMessages.put(AndroidCompilerMessageKind.ERROR, error);
      myMessages.put(AndroidCompilerMessageKind.WARNING, new ArrayList<>());
    }

    public Map<AndroidCompilerMessageKind, List<String>> getMessages() {
      return myMessages;
    }

    public int getExitCode() {
      return myExitCode;
    }
  }

  public static <T> void addMessages(@NotNull Map<T, List<String>> messages, @NotNull Map<T, List<String>> toAdd) {
    for (Map.Entry<T, List<String>> entry : toAdd.entrySet()) {
      List<String> list = messages.get(entry.getKey());
      if (list == null) {
        list = new ArrayList<>();
        messages.put(entry.getKey(), list);
      }
      list.addAll(entry.getValue());
    }
  }
}
