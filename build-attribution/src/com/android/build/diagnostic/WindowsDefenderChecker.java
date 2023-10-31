/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.build.diagnostic;

import static com.intellij.openapi.util.NullableLazyValue.volatileLazyNullable;
import static java.util.Objects.requireNonNull;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.configurations.PathEnvironmentVariableUtil;
import com.intellij.execution.process.ProcessOutput;
import com.intellij.execution.util.ExecUtil;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.jna.JnaLoader;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectUtil;
import com.intellij.openapi.util.NullableLazyValue;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.sun.jna.platform.win32.COM.COMException;
import com.sun.jna.platform.win32.COM.Wbemcli;
import com.sun.jna.platform.win32.COM.WbemcliUtil;
import com.sun.jna.platform.win32.Ole32;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;
import java.util.stream.Stream;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * This is a copy of {@link com.intellij.diagnostic.WindowsDefenderChecker} from the platform.
 * That class has important API on package-private level, so we can not use it.
 * We should remove this copy once <a href="https://github.com/JetBrains/intellij-community/pull/2490">fixing pull request</a> is merged.
 * <p>
 * Sources:
 * <a href="https://learn.microsoft.com/en-us/microsoft-365/security/defender-endpoint/configure-extension-file-exclusions-microsoft-defender-antivirus">Defender Settings</a>,
 * <a href="https://learn.microsoft.com/en-us/powershell/module/defender/">Defender PowerShell Module</a>.
 */
@SuppressWarnings("MethodMayBeStatic")
public class WindowsDefenderChecker {
  private static final Logger LOG = Logger.getInstance(WindowsDefenderChecker.class);

  private static final String IGNORE_STATUS_CHECK = "ignore.virus.scanning.warn.message";
  private static final String HELPER_SCRIPT_NAME = "defender-exclusions.ps1";
  private static final String SIG_MARKER = "# SIG # Begin signature block";
  private static final int WMIC_COMMAND_TIMEOUT_MS = 10_000, POWERSHELL_COMMAND_TIMEOUT_MS = 30_000;
  private static final ExtensionPointName<com.intellij.diagnostic.WindowsDefenderChecker.Extension> EP_NAME = ExtensionPointName.create("com.intellij.defender.config");

  public static WindowsDefenderChecker getInstance() {
    return ApplicationManager.getApplication().getService(WindowsDefenderChecker.class);
  }

  private final NullableLazyValue<Path> myHelper = volatileLazyNullable(() -> {
    var candidate = PathManager.findBinFile(HELPER_SCRIPT_NAME);
    if (candidate != null) {
      try {
        if (Files.readString(candidate).contains(SIG_MARKER)) {
          return candidate;
        }
      }
      catch (IOException e) {
        LOG.warn(e);
      }
    }
    LOG.info("'" + HELPER_SCRIPT_NAME + (candidate == null ? "' is missing" : "' is unsigned"));
    return null;
  });

  public boolean isStatusCheckIgnored(@NotNull Project project) {
    return PropertiesComponent.getInstance().isTrueValue(IGNORE_STATUS_CHECK) ||
           PropertiesComponent.getInstance(project).isTrueValue(IGNORE_STATUS_CHECK);
  }

  final void ignoreStatusCheck(@Nullable Project project, boolean ignore) {
    var component = project == null ? PropertiesComponent.getInstance() : PropertiesComponent.getInstance(project);
    if (ignore) {
      component.setValue(IGNORE_STATUS_CHECK, true);
    }
    else {
      component.unsetValue(IGNORE_STATUS_CHECK);
    }
  }

  /**
   * {@link Boolean#TRUE} means Defender is present, active, and real-time protection check is enabled.
   * {@link Boolean#FALSE} means something from the above list is not true.
   * {@code null} means the IDE cannot detect the status.
   */
  public @Nullable Boolean isRealTimeProtectionEnabled() {
    if (!JnaLoader.isLoaded()) {
      LOG.debug("JNA is not loaded");
      return null;
    }

    try {
      var comInit = Ole32.INSTANCE.CoInitializeEx(null, Ole32.COINIT_APARTMENTTHREADED);
      if (LOG.isDebugEnabled()) LOG.debug("CoInitializeEx: " + comInit);

      var avQuery = new WbemcliUtil.WmiQuery<>("Root\\SecurityCenter2", "AntivirusProduct", AntivirusProduct.class);
      var avResult = avQuery.execute(WMIC_COMMAND_TIMEOUT_MS);
      if (LOG.isDebugEnabled()) LOG.debug("results: " + avResult.getResultCount());
      for (var i = 0; i < avResult.getResultCount(); i++) {
        var name = avResult.getValue(AntivirusProduct.DisplayName, i);
        if (LOG.isDebugEnabled()) LOG.debug("DisplayName[" + i + "]: " + name + " (" + name.getClass().getName() + ')');
        if (name instanceof String s && (s.contains("Windows Defender") || s.contains("Microsoft Defender"))) {
          var state = avResult.getValue(AntivirusProduct.ProductState, i);
          if (LOG.isDebugEnabled()) LOG.debug("ProductState: " + state + " (" + state.getClass().getName() + ')');
          var enabled = state instanceof Integer intState && (intState.intValue() & 0x1000) != 0;
          if (!enabled) return false;
          break;
        }
      }

      var statusQuery  = new WbemcliUtil.WmiQuery<>("Root\\Microsoft\\Windows\\Defender", "MSFT_MpComputerStatus", MpComputerStatus.class);
      var statusResult = statusQuery.execute(WMIC_COMMAND_TIMEOUT_MS);
      if (LOG.isDebugEnabled()) LOG.debug("results: " + statusResult.getResultCount());
      if (statusResult.getResultCount() != 1) return false;
      var rtProtection = statusResult.getValue(MpComputerStatus.RealTimeProtectionEnabled, 0);
      if (LOG.isDebugEnabled()) LOG.debug("RealTimeProtectionEnabled: " + rtProtection + " (" + rtProtection.getClass().getName() + ')');
      return Boolean.TRUE.equals(rtProtection);
    }
    catch (COMException e) {
      if (e.matchesErrorCode(Wbemcli.WBEM_E_INVALID_NAMESPACE)) return false;  // Microsoft Defender not installed
      var message = "WMI Microsoft Defender check failed";
      var hresult = e.getHresult();
      if (hresult != null) message += " [0x" + Integer.toHexString(hresult.intValue()) + ']';
      LOG.warn(message, e);
      return null;
    }
    catch (Exception e) {
      LOG.warn("WMI Microsoft Defender check failed", e);
      return null;
    }
  }

  final boolean canRunScript() {
    return myHelper.getValue() != null;
  }

  private enum AntivirusProduct {DisplayName, ProductState}
  private enum MpComputerStatus {RealTimeProtectionEnabled}

  final @NotNull List<Path> getImportantPaths(@NotNull Project project) {
    var paths = new TreeSet<Path>();

    var projectDir = ProjectUtil.guessProjectDir(project);
    if (projectDir != null && projectDir.getFileSystem() instanceof LocalFileSystem) {
      paths.add(projectDir.toNioPath());
    }

    paths.add(PathManager.getSystemDir());

    EP_NAME.forEachExtensionSafe(ext -> {
      paths.addAll(ext.getPaths(project));
    });

    return new ArrayList<>(paths);
  }

  final boolean excludeProjectPaths(@NotNull List<Path> paths) {
    try {
      var script = requireNonNull(myHelper.getValue(), "missing/dysfunctional helper");

      var psh = PathEnvironmentVariableUtil.findInPath("powershell.exe");
      if (psh == null) {
        LOG.info("no 'powershell.exe' on " + PathEnvironmentVariableUtil.getPathVariableValue());
        return false;
      }
      var sane = Stream.of("SystemRoot", "ProgramFiles").map(System::getenv).anyMatch(val -> val != null && psh.toPath().startsWith(val));
      if (!sane) {
        LOG.info("suspicious 'powershell.exe' location: " + psh);
        return false;
      }

      var scriptlet = "(Get-AuthenticodeSignature '" + script + "').Status";
      var command = new GeneralCommandLine(psh.getPath(), "-NoProfile", "-NonInteractive", "-Command", scriptlet);
      var output = run(command);
      if (output.getExitCode() != 0 || !"Valid".equals(output.getStdout().trim())) {
        LOG.info("validation failed:\n[" + output.getExitCode() + "] " + command + "\noutput: " + output.getStdout().trim());
        return false;
      }

      command = ExecUtil.sudoCommand(
        new GeneralCommandLine(Stream.concat(
          Stream.of(psh.getPath(), "-ExecutionPolicy", "Bypass", "-NoProfile", "-NonInteractive", "-File", script.toString()),
          paths.stream().map(Path::toString)
        ).toList()),
        ""
      ).withCharset(StandardCharsets.UTF_8);
      output = run(command);
      if (output.getExitCode() != 0) {
        LOG.info("script failed:\n[" + output.getExitCode() + "] " + command + "\noutput: " + output.getStdout().trim());
        return false;
      }
      else {
        LOG.info("OK; script output:\n" + output.getStdout().trim());
        return true;
      }
    }
    catch (Exception e) {
      LOG.warn(e);
      return false;
    }
  }

  private static ProcessOutput run(GeneralCommandLine command) throws ExecutionException {
    command.getEnvironment().remove("PSModulePath");
    return ExecUtil.execAndGetOutput(
      command.withRedirectErrorStream(true).withWorkDirectory(PathManager.getTempPath()),
      POWERSHELL_COMMAND_TIMEOUT_MS);
  }

  public @NotNull String getConfigurationInstructionsUrl() {
    return "https://intellij.com/antivirus-impact-on-build-speed";
  }
}
