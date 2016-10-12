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
package com.android.tools.idea.welcome.config;

import com.android.tools.idea.sdk.IdeSdks;
import com.google.common.base.Function;
import com.google.common.base.Strings;
import com.google.common.collect.Iterables;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.projectRoots.JavaSdk;
import com.intellij.openapi.projectRoots.JavaSdkVersion;
import com.intellij.openapi.projectRoots.JdkUtil;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Holds methods for testing typical locations for compatible JDKs.
 */
public class JdkDetection {

  private static final AtomicBoolean myJdkDetectionInProgress = new AtomicBoolean();

  private JdkDetection() {}

  public static interface JdkDetectionResult {
    void onSuccess(String newJdkPath);
    void onCancel();
  }

  public static void start(JdkDetectionResult result) {
    new DetectJdkTask(result, true).queue();
  }

  public static void startWithProgressIndicator(JdkDetectionResult result) {
    new DetectJdkTask(result, false).queue();
  }

  @Nullable
  public static String validateJdkLocation(@Nullable File location) {
    if (location == null) {
      return "Path is empty";
    }
    if (!JdkUtil.checkForJdk(location)) {
      return "Path specified is not a valid JDK location";
    }
    if (!isJdk7(location)) {
      return "JDK 7.0 or newer is required";
    }
    return null;
  }

  private static boolean isJdk7(@NotNull File path) {
    String jdkVersion = JavaSdk.getJdkVersion(path.getAbsolutePath());
    if (jdkVersion != null) {
      JavaSdkVersion version = JavaSdk.getInstance().getVersion(jdkVersion);
      if (version != null && !version.isAtLeast(JavaSdkVersion.JDK_1_7)) {
        return false;
      }
    }
    return true;
  }

  private static class DetectJdkTask extends Task.Modal {
    private static final String MAC_JDKS_DIR = "/Library/Java/JavaVirtualMachines/";
    private static final String WINDOWS_JDKS_DIR = "C:\\Program Files\\Java";
    private static final String LINUX_SDK_DIR = "/usr/lib/jvm";

    private final JdkDetectionResult myResult;
    private final boolean myHeadless;

    private String myPath = null;

    public DetectJdkTask(JdkDetectionResult result, boolean headless) {
      super(null, "Detect JDK", true);
      myResult = result;
      myHeadless = headless;
    }

    @Override
    public void run(@NotNull ProgressIndicator indicator) {
      indicator.setIndeterminate(true);
      if (myJdkDetectionInProgress.compareAndSet(false, true)) {
        try {
          myPath = detectJdkPath(indicator);
        }
        finally {
          myJdkDetectionInProgress.set(false);
        }
      }
      while (myJdkDetectionInProgress.get()) {
        try {
          // Just wait until previously run detection completes (progress dialog is shown then)
          //noinspection BusyWait
          Thread.sleep(300);
        }
        catch (InterruptedException ignore) {
        }
      }
    }

    @Override
    public boolean isHeadless() {
      if (myHeadless) {
        return false;
      }
      return super.isHeadless();
    }

    @Override
    public void onSuccess() {
      myResult.onSuccess(myPath);
    }

    @Override
    public void onCancel() {
      myResult.onCancel();
    }

    @Override
    public void onError(@NotNull Exception error) {
      super.onError(error);
      myResult.onCancel();
    }

    @Nullable
    private static String detectJdkPath(@NotNull ProgressIndicator indicator) {
      String topVersion = null;
      String chosenPath = null;
      for (String path : getCandidatePaths()) {
        indicator.checkCanceled();
        if (StringUtil.isEmpty(validateJdkLocation(new File(path)))) {
          String version = JavaSdk.getInstance().getVersionString(path);
          if (topVersion == null || version == null || topVersion.compareTo(version) < 0) {
            topVersion = version;
            chosenPath = path;
          }
        }
      }
      return chosenPath;
    }

    @NotNull
    private static Iterable<String> getCandidatePaths() {
      return Iterables.concat(deduceFromJavaHome(), deduceFromPath(), deduceFromCurrentJvm(), getOsSpecificCandidatePaths());
    }

    @NotNull
    private static Iterable<String> deduceFromJavaHome() {
      String javaHome = System.getenv("JAVA_HOME");
      return Strings.isNullOrEmpty(javaHome) ? Collections.<String>emptySet() : Collections.singleton(javaHome);
    }

    @NotNull
    private static Iterable<String> deduceFromPath() {
      String path = System.getenv("PATH");
      if (Strings.isNullOrEmpty(path)) {
        return Collections.emptyList();
      }
      String[] pathEntries = path.split(File.pathSeparator);
      for (String entry : pathEntries) {
        if (Strings.isNullOrEmpty(entry)) {
          continue;
        }

        // Check if current PATH entry points to a directory which has a file named 'java'.
        File javaParentDir = new File(entry);
        File javaFile = new File(javaParentDir, "java");
        if (!javaParentDir.isDirectory() || !javaFile.isFile()) {
          continue;
        }
        try {
          // There is a possible case that target java is a symlink like /usr/bin/java. We want to resolve it and use hard link then.
          File canonicalJavaFile = javaFile.getCanonicalFile();
          return forJavaBinParent(canonicalJavaFile.getParentFile());
        }
        catch (IOException ignore) {
        }
      }
      return Collections.emptyList();
    }

    @NotNull
    private static Iterable<String> deduceFromCurrentJvm() {
      String javaHome = System.getProperty("java.home");
      return Strings.isNullOrEmpty(javaHome) ? Collections.<String>emptySet() : forJavaBinParent(new File(javaHome));
    }

    /**
     * We assume that <code>'java'</code> executable is located inside a directory named <code>'bin'</code> (given as an argument).
     * However, there are multiple cases about its ancestors though:
     * <ul>
     *   <li>JDK_HOME/bin</li>
     *   <li>JDK_HOME/jre/bin</li>
     *   <li>JRE_HOME/bin</li>
     * </ul>
     * This tries to handle them and return an iterable with one element which points to the potential java home or an empty
     * iterable otherwise.
     *
     * @param javaBinParent  parent directory for a directory which contains <code>'java'</code> executable
     * @return               iterable which is empty or contains entry(ies) which is java home candidate path
     */
    @NotNull
    private static Iterable<String> forJavaBinParent(@NotNull File javaBinParent) {
      if (!javaBinParent.isDirectory()) {
        return Collections.emptySet();
      }
      if (!"jre".equals(javaBinParent.getName())) {
        return Collections.singleton(javaBinParent.getAbsolutePath());
      }
      File parentFile = javaBinParent.getParentFile();
      if (parentFile.isDirectory()) {
        return Collections.singleton(parentFile.getAbsolutePath());
      }
      return Collections.emptySet();
    }

    @NotNull
    private static Iterable<String> getOsSpecificCandidatePaths() {
      if (SystemInfo.isMac) {
        return getMacCandidateJdks();
      }
      else if (SystemInfo.isWindows) {
        return getWindowsCandidateJdks();
      }
      else if (SystemInfo.isLinux) {
        return getLinuxCandidateJdks();
      }
      else {
        return Collections.emptyList();
      }
    }

    @NotNull
    private static Iterable<String> getMacCandidateJdks() {
      // See http://docs.oracle.com/javase/7/docs/webnotes/install/mac/mac-jdk.html
      return getCandidatePaths(MAC_JDKS_DIR, IdeSdks.MAC_JDK_CONTENT_PATH);
    }

    @NotNull
    private static Iterable<String> getWindowsCandidateJdks() {
      // See http://docs.oracle.com/javase/7/docs/webnotes/install/windows/jdk-installation-windows.html
      return getCandidatePaths(WINDOWS_JDKS_DIR, "");
    }

    @NotNull
    private static Iterable<String> getLinuxCandidateJdks() {
      return getCandidatePaths(LINUX_SDK_DIR, "");
    }

    private static Iterable<String> getCandidatePaths(String basedir, final String suffix) {
      final File location = new File(basedir);
      if (location.isDirectory()) {
        return Iterables.transform(Arrays.asList(location.list()), new Function<String, String>() {
          @Override
          public String apply(@Nullable String dir) {
            return new File(location, dir + suffix).getAbsolutePath();
          }
        });
      }
      return Collections.emptyList();
    }

  }
}
