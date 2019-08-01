/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.util;

import static org.gradle.internal.impldep.org.eclipse.jgit.util.StringUtils.isEmptyOrNull;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.EnvironmentUtil;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.terminal.LocalTerminalDirectRunner;
import org.jetbrains.plugins.terminal.TerminalOptionsProvider;

public class TerminalUtil {
  private static final Logger ourLogger = Logger.getInstance(TerminalUtil.class);
  private static final HashMap<String, String> ourCachedVariables = new HashMap<>();
  private static final Object ourLock = new Object();

  @Nullable
  public static String getJavaHomeFromTerminal() {
    return getEnvVariable("JAVA_HOME");
  }

  @Nullable
  private static String getEnvVariable(@NotNull String name) {
    synchronized (ourLock) {
      // Could not use computeIfAbsent since this will cause to read environment variable every time for not set variables
      if (ourCachedVariables.containsKey(name)) {
        return ourCachedVariables.get(name);
      }
      String result = null;
      try {
        if (SystemInfo.isLinux) {
          result = getEnvVariableLinux(name);
        }
        else if (SystemInfo.isMac) {
          result = getEnvVariableMac(name);
        }
        else if (SystemInfo.isWindows) {
          result = getEnvVariableWindows(name);
        }
      }
      catch (Exception e) {
        ourLogger.warn("Could not get terminal environment variable " + name, e);
        return null;
      }
      ourCachedVariables.put(name, result);
      return result;
    }
  }

  @Nullable
  private static String getEnvVariableWindows(@NotNull String name) throws Exception {
    EnvironmentUtil.ShellEnvReader shEnvReader = new EnvironmentUtil.ShellEnvReader();

    File cmdFile = FileUtil.createTempFile("emptyCommand", ".bat", true);
    FileUtil.writeToFile(cmdFile, "rem this batch file is empty\r\n");

    Map<String, String> env = shEnvReader.readBatEnv(cmdFile, Collections.emptyList());
    if (env.containsKey(name)) {
      return env.get(name);
    }
    return null;
  }

  @Nullable
  private static String getEnvVariableMac(@NotNull String name) throws Exception {
    EnvironmentUtil.ShellEnvReader shEnvReader = new EnvironmentUtil.ShellEnvReader();
    Map<String, String> env = shEnvReader.readShellEnv();
    if (env.containsKey(name)) {
      return env.get(name);
    }
    return null;
  }

  @Nullable
  private static String getEnvVariableLinux(@NotNull String name) throws IOException, InterruptedException {
    String shellPath = TerminalOptionsProvider.getInstance().getShellPath();
    String[] command = LocalTerminalDirectRunner.getCommand(shellPath, new HashMap<>(), true /* Want to process shell scripts */);
    ourLogger.info("Running process with:" + Arrays.toString(command));

    Process process = Runtime.getRuntime().exec(command);
    InputStream out = process.getInputStream();
    OutputStream in = process.getOutputStream();

    String readVar;
    if (SystemInfo.isUnix) {
      readVar = "echo $" + name + "; exit\n";
    }
    else {
      readVar = "echo %" + name + "% & exit\n";
    }
    ourLogger.info("Reading variable with: " + readVar);

    try (BufferedReader reader = new BufferedReader(new InputStreamReader(out, StandardCharsets.UTF_8))) {
      in.write(readVar.getBytes(StandardCharsets.UTF_8));
      in.flush();
      if (!process.waitFor(1, TimeUnit.SECONDS)) {
        ourLogger.warn("Time out while reading environment variable " + name);
      }
      if (reader.ready()) {
        String result = reader.readLine().trim();
        ourLogger.info("Got terminal environment variable " + name + ": " + result);
        if (!isEmptyOrNull(result)) {
          return result;
        }
      }
    }
    return null;
  }
}
