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
package com.android.tools.idea.actions;

import com.android.SdkConstants;
import com.android.ide.common.repository.GradleVersion;
import com.android.repository.Revision;
import com.android.repository.api.LocalPackage;
import com.android.repository.api.ProgressIndicator;
import com.android.sdklib.repository.AndroidSdkHandler;
import com.android.sdklib.repository.meta.DetailsTypes;
import com.android.tools.idea.gradle.plugin.AndroidPluginInfo;
import com.android.tools.idea.gradle.util.GradleVersions;
import com.android.tools.idea.gradle.util.LocalProperties;
import com.android.tools.idea.sdk.AndroidSdks;
import com.android.tools.idea.sdk.progress.StudioLoggerProgressIndicator;
import com.android.utils.FileUtils;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.process.CapturingAnsiEscapesAwareProcessHandler;
import com.intellij.execution.process.ProcessAdapter;
import com.intellij.execution.process.ProcessEvent;
import com.intellij.ide.BrowserUtil;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ex.ApplicationInfoEx;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.EnvironmentUtil;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.android.sdklib.internal.project.ProjectProperties.PROPERTY_CMAKE;
import static com.android.sdklib.internal.project.ProjectProperties.PROPERTY_NDK;
import static java.nio.file.Files.readAllBytes;

/**
 * This one is inspired by on com.intellij.ide.actions.SendFeedbackAction, however in addition to the basic
 * IntelliJ / Java / OS information, it enriches the bug template with Android-specific version context we'd like to
 * see pre-populated in our bug reports.
 */
public class SendFeedbackAction extends AnAction implements DumbAware {
  private static final Logger LOG = Logger.getInstance(SendFeedbackAction.class);
  private static final Pattern CMAKE_VERSION_PATTERN = Pattern.compile("cmake version\\s+(.*)");

  @Override
  public void actionPerformed(AnActionEvent e) {
    launchBrowser(e.getProject());
  }

  public static void launchBrowser(@Nullable Project project) {
    final ApplicationInfoEx appInfo = ApplicationInfoEx.getInstanceEx();
    boolean eap = appInfo.isEAP();
    String urlTemplate = eap ? appInfo.getEAPFeedbackUrl() : appInfo.getReleaseFeedbackUrl();
    urlTemplate = urlTemplate
      .replace("$BUILD", eap ? appInfo.getBuild().asStringWithoutProductCode() : appInfo.getBuild().asString())
      .replace("$TIMEZONE", System.getProperty("user.timezone"))
      .replace("$VERSION", appInfo.getFullVersion())
      .replace("$EVAL", "false") // always false for Android Studio
      .replace("$DESCR", getDescription(project));
    BrowserUtil.browse(urlTemplate, project);
  }

  private static String getBasicDescription() {
    StringBuilder sb = new StringBuilder("\n\n");
    sb.append(ApplicationInfoEx.getInstanceEx().getBuild().asString()).append(", ");
    String javaVersion = System.getProperty("java.runtime.version", System.getProperty("java.version", "unknown"));
    sb.append("JRE ");
    sb.append(javaVersion);
    String archDataModel = System.getProperty("sun.arch.data.model");
    if (archDataModel != null) {
      sb.append("x").append(archDataModel);
    }
    String javaVendor = System.getProperty("java.vm.vendor");
    if (javaVendor != null) {
      sb.append(" ").append(javaVendor);
    }
    sb.append(", OS ").append(System.getProperty("os.name"));
    String osArch = System.getProperty("os.arch");
    if (osArch != null) {
      sb.append("(").append(osArch).append(")");
    }

    String osVersion = System.getProperty("os.version");
    String osPatchLevel = System.getProperty("sun.os.patch.level");
    if (osVersion != null) {
      sb.append(" v").append(osVersion);
      if (osPatchLevel != null) {
        sb.append(" ").append(osPatchLevel);
      }
    }
    if (!GraphicsEnvironment.isHeadless()) {
      sb.append(", screens ");
      GraphicsDevice[] devices = GraphicsEnvironment.getLocalGraphicsEnvironment().getScreenDevices();
      for (int i = 0; i < devices.length; i++) {
        if (i > 0) sb.append(", ");
        GraphicsDevice device = devices[i];
        Rectangle bounds = device.getDefaultConfiguration().getBounds();
        sb.append(bounds.width).append("x").append(bounds.height);
      }
      if (UIUtil.isRetina()) sb.append(SystemInfo.isMac ? "; Retina" : "; HiDPI");
    }
    return sb.toString();
  }

  public static String getDescription(@Nullable Project project) {
    // Use safe call wrapper extensively to make sure that as much as possible version context is collected and
    // that any exceptions along the way do not actually break the feedback sending flow (we're already reporting a bug,
    // so let's not make that process prone to exceptions)
    return safeCall(() -> {
      StringBuilder sb = new StringBuilder(getBasicDescription());
      ProgressIndicator progress = new StudioLoggerProgressIndicator(SendFeedbackAction.class);
      AndroidSdkHandler sdkHandler = AndroidSdks.getInstance().tryToChooseSdkHandler();
      // Add Android Studio custom information we want to see prepopulated in the bug reports
      sb.append("\n\n");
      if (project != null) {
        sb.append(String.format("Android Gradle Plugin: %1$s\n", safeCall(() -> getGradlePluginDetails(project))));
        sb.append(String.format("Gradle: %1$s\n", safeCall(() -> getGradleDetails(project))));
      }
      sb.append(String.format("NDK: %1$s\n", safeCall(() -> getNdkDetails(project, sdkHandler, progress))));
      sb.append(String.format("LLDB: %1$s\n", safeCall(() -> getLldbDetails(sdkHandler, progress))));
      sb.append(String.format("CMake: %1$s\n", safeCall(() -> getCMakeDetails(project, sdkHandler, progress))));
      return sb.toString();
    });
  }

  private static String safeCall(@NotNull Supplier<String> runnable) {
    try {
      return runnable.get();
    }
    catch (Throwable e) {
      LOG.info("Unable to prepopulate additional version information - proceeding with sending feedback anyway. ", e);
      return "(unable to retrieve additional version information)";
    }
  }

  private static String getGradlePluginDetails(@NotNull Project project) {
    AndroidPluginInfo androidPluginInfo = AndroidPluginInfo.find(project);
    if (androidPluginInfo != null) {
      GradleVersion androidPluginVersion = androidPluginInfo.getPluginVersion();
      if (androidPluginVersion != null) {
        return androidPluginVersion.toString();
      }
    }
    return "(plugin information not found)";
  }

  private static String getGradleDetails(@NotNull Project project) {
    GradleVersion gradleVersion = GradleVersions.getInstance().getGradleVersion(project);
    if (gradleVersion != null) {
      return gradleVersion.toString();
    }
    return "(gradle version information not found)";
  }

  private static String getNdkDetails(@Nullable Project project,
                                      @NotNull AndroidSdkHandler sdkHandler,
                                      @NotNull ProgressIndicator progress) {
    StringBuilder sb = new StringBuilder();
    // Get version information from all the channels we know, and include it all into the bug to provide
    // the entire context.
    // NDK specified in local.properties (if any)
    if (project != null) {
      try {
        String ndkDir = new LocalProperties(project).getProperty(PROPERTY_NDK);
        sb.append(String.format("from local.properties: %1$s; ",
                                ndkDir == null ? "(not specified)"
                                               : getNdkVersion(ndkDir)));
      }
      catch (IOException e) {
        LOG.info(String.format("Unable to read local.properties file of Project '%1$s'", project.getName()), e);
      }
    }
    // Latest NDK package in the SDK (if any)
    LocalPackage p = sdkHandler.getLatestLocalPackageForPrefix(SdkConstants.FD_NDK, null,false, progress);
    sb.append(String.format("latest from SDK: %1$s; ",
                            p == null ? "(not found)"
                                      : getNdkVersion(p.getLocation().getAbsolutePath())));
    return sb.toString();
  }

  /**
   *  Taken with slight modifications from NdkHelper.getNdkVersion() in android-ndk, but not called directly to
   *  avoid dependency of 'android' on 'android-ndk'.
   *  TODO: Consider factoring out all version info helpers into a separate module.
   */
  private static String getNdkVersion(@NotNull String ndkDir) {
    File sourcePropertiesFile = new File(ndkDir, "source.properties");
    if (sourcePropertiesFile.exists()) {
      //NDK 11+
      InputStream fileInput = null;
      try {
        fileInput = new FileInputStream(sourcePropertiesFile);
        Properties props = new Properties();
        props.load(fileInput);
        return props.getProperty("Pkg.Revision");
      }
      catch (Exception e) {
        LOG.info("Could not read NDK version", e);
        return "(unable to read)";
      }
      finally {
        if (fileInput != null) {
          try {
            fileInput.close();
          }
          catch (IOException e) {
            LOG.warn("Failed to close '" + sourcePropertiesFile.getPath() + "'", e);
          }
        }
      }
    }
    File releaseTxtFile = new File(ndkDir, "RELEASE.TXT");
    if (releaseTxtFile.exists()) {
      try {
        // NDK 10
        byte[] content = readAllBytes(releaseTxtFile.toPath());
        return new String(content).trim();
      }
      catch (IOException e) {
        LOG.info("Could not read NDK version", e);
        return "(unable to read)";
      }
    }
    return "UNKNOWN";
  }

  private static String getLldbDetails(@NotNull AndroidSdkHandler sdkHandler, @NotNull ProgressIndicator progress) {
    String path = DetailsTypes.getLldbPath(Revision.parseRevision(SdkConstants.LLDB_PINNED_REVISION));
    LocalPackage p = sdkHandler.getLocalPackage(path, progress);
    if (p == null) {
      // OK, the version of LLDB compatible with the running version of Studio not found, display the latest installed
      // information instead (and indicate that the supported version is not found)
      p = sdkHandler.getLatestLocalPackageForPrefix(SdkConstants.FD_LLDB, null, false, progress);
      return String.format("pinned revision %1$s not found; latest from SDK: %2$s; ", SdkConstants.LLDB_PINNED_REVISION,
                           getLocalPackageDisplayInfo(p));
    }
    return getLocalPackageDisplayInfo(p);
  }

  private static String getCMakeDetails(@Nullable Project project,
                                        @NotNull AndroidSdkHandler sdkHandler,
                                        @NotNull ProgressIndicator progress) {
    StringBuilder sb = new StringBuilder();
    // Get version information from all the channels we know, and include it all into the bug to provide
    // the entire context.
    if (project != null) {
      // CMake specified in local.properties (if any)
      try {
        String cmakeDir = new LocalProperties(project).getProperty(PROPERTY_CMAKE);
        sb.append(String.format("from local.properties: %1$s; ",
                                cmakeDir == null ? "(not specified)"
                                                 : runAndGetCMakeVersion(getCMakeExecutablePath(cmakeDir))));
      }
      catch (IOException e) {
        LOG.info(String.format("Unable to read local.properties file of Project '%1$s'", project.getName()), e);
      }
    }
    // Latest CMake package in the SDK (if any)
    LocalPackage p = sdkHandler.getLatestLocalPackageForPrefix(SdkConstants.FD_CMAKE, null,false, progress);
    sb.append(String.format("latest from SDK: %1$s; ",
                            p == null ? "(not found)"
                                      : runAndGetCMakeVersion(getCMakeExecutablePath(p.getLocation().getAbsolutePath()))));
    // CMake from PATH (if any)
    String cmakeBinFromPath = findOnPath("cmake");
    sb.append(String.format("from PATH: %1$s; ",
                            cmakeBinFromPath == null ? "(not found)"
                                                     : runAndGetCMakeVersion(cmakeBinFromPath)));
    return sb.toString();
  }

  @SuppressWarnings("SameParameterValue")
  @Nullable
  private static String findOnPath(@NotNull String executableName) {
    String path = EnvironmentUtil.getValue("PATH");
    if (path != null) {
      for (String dir : StringUtil.tokenize(path, File.pathSeparator)) {
        File candidate = new File(dir, executableName);
        if (candidate.canExecute()) {
          return candidate.getAbsolutePath();
        }
      }
    }
    return null;
  }

  private static String getCMakeExecutableName() {
    String cmakeExecutableName = "cmake";
    if (SystemInfo.isWindows) {
      cmakeExecutableName += ".exe";
    }
    return cmakeExecutableName;
  }

  private static String getCMakeExecutablePath(@NotNull String cmakeDir) {
    String cmakeBinDirectory = FileUtils.join(cmakeDir, "bin");
    String cmakeExecutableName = getCMakeExecutableName();
    File cmakeExecutableFile = new File(FileUtils.join(cmakeBinDirectory, cmakeExecutableName));
    if (!cmakeExecutableFile.exists() || !cmakeExecutableFile.canExecute()) {
      return "(binary doesn't exist or is not executable)";
    }
    return cmakeExecutableFile.getAbsolutePath();
  }

  private static String runAndGetCMakeVersion(@NotNull String cmakeExecutableFile) {
    LOG.info("CMake binary: " + cmakeExecutableFile);
    GeneralCommandLine commandLine = new GeneralCommandLine(cmakeExecutableFile);
    commandLine.addParameter("-version");
    try {
      CapturingAnsiEscapesAwareProcessHandler process = new CapturingAnsiEscapesAwareProcessHandler(commandLine);
      final StringBuffer output = new StringBuffer();
      process.addProcessListener(new ProcessAdapter() {
        @Override
        public void onTextAvailable(@NotNull ProcessEvent event, @NotNull Key outputType) {
          output.append(event.getText());
          super.onTextAvailable(event, outputType);
        }
      });
      int exitCode = process.runProcess().getExitCode();
      if (exitCode == 0) {
        Matcher m = CMAKE_VERSION_PATTERN.matcher(output.toString());
        if (m.find()) {
          return m.group(1);
        }
      }
      return output.length() > 0 ? output.toString() : "(empty output)";
    }
    catch (ExecutionException e) {
      LOG.info("Could not invoke 'cmake -version'", e);
      return "(unable to invoke cmake)";
    }
  }

  private static String getLocalPackageDisplayInfo(@Nullable LocalPackage p) {
    if (p == null) {
      return "(package not found)";
    }
    return String.format("%1$s (revision: %2$s)", p.getDisplayName() , p.getVersion());
  }

  @Override
  public void update(AnActionEvent e) {
    super.update(e);
    if (e.getPresentation().isEnabled()) {
      e.getPresentation().setEnabled(SystemInfo.isMac || SystemInfo.isLinux || SystemInfo.isWindows);
    }
  }
}
