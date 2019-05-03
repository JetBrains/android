// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.android.compiler.tools;

import com.android.sdklib.BuildToolInfo;
import com.android.sdklib.IAndroidTarget;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.configurations.JavaParameters;
import com.intellij.execution.configurations.ParametersList;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.util.PathUtil;
import com.intellij.util.PathsList;
import com.intellij.util.containers.HashMap;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.android.util.AndroidCommonUtils;
import org.jetbrains.android.util.AndroidCompilerMessageKind;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * @author Eugene.Kudelevsky
 */
public class AndroidDxWrapper {
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.android.compiler.tools.AndroidDx");

  private AndroidDxWrapper() {
  }

  @SuppressWarnings({"IOResourceOpenedButNotSafelyClosed"})
  public static Map<AndroidCompilerMessageKind, List<String>> execute(@NotNull Module module,
                                                                      @NotNull IAndroidTarget target,
                                                                      @NotNull String outputDir,
                                                                      @NotNull String[] compileTargets,
                                                                      @NotNull String additionalVmParams,
                                                                      int maxHeapSize,
                                                                      boolean optimize) {
    BuildToolInfo buildToolInfo = target.getBuildToolInfo();
    if (buildToolInfo == null) {
      return Collections.singletonMap(AndroidCompilerMessageKind.ERROR, Collections.singletonList("No Build Tools in the Android SDK."));
    }

    String outFile = outputDir + File.separatorChar + AndroidCommonUtils.CLASSES_FILE_NAME;

    final Map<AndroidCompilerMessageKind, List<String>> messages = new HashMap<>(2);
    messages.put(AndroidCompilerMessageKind.ERROR, new ArrayList<>());
    messages.put(AndroidCompilerMessageKind.INFORMATION, new ArrayList<>());
    messages.put(AndroidCompilerMessageKind.WARNING, new ArrayList<>());

    String dxJarPath = buildToolInfo.getPath(BuildToolInfo.PathId.DX_JAR);
    File dxJar = new File(dxJarPath);
    if (!dxJar.isFile()) {
      messages.get(AndroidCompilerMessageKind.ERROR).add(AndroidBundle.message("android.file.not.exist.error", dxJarPath));
      return messages;
    }

    JavaParameters parameters = new JavaParameters();
    Sdk sdk = ModuleRootManager.getInstance(module).getSdk();

    // dex runs after simple java compilation, so JDK must be specified
    assert sdk != null;

    parameters.setJdk(sdk);
    parameters.setMainClass(AndroidDxRunner.class.getName());

    ParametersList programParamList = parameters.getProgramParametersList();
    programParamList.add(dxJarPath);
    programParamList.add(outFile);
    programParamList.add("--optimize", Boolean.toString(optimize));
    programParamList.addAll(compileTargets);
    programParamList.add("--exclude");

    ParametersList vmParamList = parameters.getVMParametersList();

    if (!additionalVmParams.isEmpty()) {
      vmParamList.addParametersString(additionalVmParams);
    }
    if (!AndroidCommonUtils.hasXmxParam(vmParamList.getParameters())) {
      vmParamList.add("-Xmx" + maxHeapSize + "M");
    }
    final PathsList classPath = parameters.getClassPath();
    classPath.add(PathUtil.getJarPathForClass(AndroidDxRunner.class));
    classPath.add(PathUtil.getJarPathForClass(FileUtilRt.class));

    // delete file to check if it will exist after dex compilation
    if (!new File(outFile).delete()) {
      LOG.info("Cannot delete file " + outFile);
    }

    Process process;
    try {
      parameters.setUseDynamicClasspath(true);
      GeneralCommandLine commandLine = parameters.toCommandLine();
      LOG.info(commandLine.getCommandLineString());
      process = commandLine.createProcess();
      AndroidCommonUtils.handleDexCompilationResult(process, commandLine.getCommandLineString(), outFile, messages, false);
    }
    catch (ExecutionException e) {
      messages.get(AndroidCompilerMessageKind.ERROR).add("ExecutionException: " + e.getMessage());
      LOG.info(e);
      return messages;
    }

    return messages;
  }
}
