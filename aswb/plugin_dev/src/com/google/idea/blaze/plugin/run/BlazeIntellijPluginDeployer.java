/*
 * Copyright 2016 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.idea.blaze.plugin.run;

import static com.google.common.collect.ImmutableList.toImmutableList;
import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.io.Files;
import com.google.devtools.intellij.plugin.IntellijPluginTargetDeployInfo.IntellijPluginDeployFile;
import com.google.devtools.intellij.plugin.IntellijPluginTargetDeployInfo.IntellijPluginDeployInfo;
import com.google.idea.blaze.base.async.executor.BlazeExecutor;
import com.google.idea.blaze.base.command.buildresult.BuildResultHelper.GetArtifactsException;
import com.google.idea.blaze.base.sync.aspects.BlazeBuildOutputs;
import com.google.idea.blaze.common.artifact.OutputArtifact;
import com.google.idea.common.experiments.BoolExperiment;
import com.google.protobuf.TextFormat;
import com.intellij.concurrency.AsyncUtil;
import com.intellij.execution.ExecutionException;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.text.StringUtil;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Future;

/** Handles finding files to deploy and copying these into the sandbox. */
class BlazeIntellijPluginDeployer {

  private static final BoolExperiment deployJavaAgents =
      new BoolExperiment("blaze.plugin.run.deploy.javaagents", true);

  static final Key<BlazeIntellijPluginDeployer> USER_DATA_KEY =
      Key.create(BlazeIntellijPluginDeployer.class.getName());

  private final String sandboxHome;
  private final Map<String, OutputArtifact> buildArtifactsMap = new HashMap<>();
  private final List<OutputArtifact> deployInfoArtifacts = new ArrayList<>();
  private final Map<OutputArtifact, File> filesToDeploy = Maps.newHashMap();

  private Future<Void> fileCopyingTask;

  BlazeIntellijPluginDeployer(String sandboxHome) {
    this.sandboxHome = sandboxHome;
  }

  /**
   * Clear data from the last build -- if this build fails, we don't want to silently launch the
   * previously built plugin.
   */
  void buildStarted() {
    deployInfoArtifacts.clear();
    buildArtifactsMap.clear();
  }

  void reportBuildComplete(BlazeBuildOutputs blazeBuildOutputs) throws GetArtifactsException {
    ImmutableList<OutputArtifact> buildArtifacts =
        blazeBuildOutputs.artifacts.values().stream()
            .map(a -> a.artifact)
            .collect(toImmutableList());
    buildArtifactsMap.clear();
    buildArtifacts.forEach(a -> buildArtifactsMap.put(a.getBazelOutRelativePath(), a));

    deployInfoArtifacts.clear();
    deployInfoArtifacts.addAll(
        buildArtifacts.stream()
            .filter(a -> a.getBazelOutRelativePath().endsWith(".intellij-plugin-debug-target-deploy-info"))
            .collect(toImmutableList()));
  }

  /**
   * Returns information about which plugins will be deployed, and asynchronously copies the
   * corresponding files to the sandbox.
   */
  DeployedPluginInfo deployNonBlocking(String buildSystem) throws ExecutionException {
    if (deployInfoArtifacts.isEmpty()) {
      throw new ExecutionException("No plugin files found. Did the build fail?");
    }
    List<IntellijPluginDeployInfo> deployInfoList = Lists.newArrayList();
    for (OutputArtifact deployInfoFile : deployInfoArtifacts) {
      deployInfoList.addAll(readDeployInfoFromFile(deployInfoFile));
    }
    ImmutableMap<OutputArtifact, File> filesToDeploy =
        getFilesToDeploy(deployInfoList, buildSystem);
    this.filesToDeploy.putAll(filesToDeploy);
    ImmutableSet<File> javaAgentJars =
        deployJavaAgents.getValue() ? listJavaAgentFiles(deployInfoList) : ImmutableSet.of();

    // kick off file copying task asynchronously, so it doesn't block the EDT.
    fileCopyingTask =
        BlazeExecutor.getInstance()
            .submit(
                () -> {
                  for (Map.Entry<OutputArtifact, File> entry : filesToDeploy.entrySet()) {
                    copyFileToSandbox(entry.getKey(), entry.getValue());
                  }
                  return null;
                });

    return new DeployedPluginInfo(javaAgentJars);
  }

  /** Blocks until the plugin files have been copied to the sandbox */
  void blockUntilDeployComplete() {
    AsyncUtil.get(fileCopyingTask);
    fileCopyingTask = null;
  }

  void deleteDeployment() {
    for (File file : filesToDeploy.values()) {
      if (file.exists()) {
        file.delete();
      }
    }
  }

  private static ImmutableList<IntellijPluginDeployInfo> readDeployInfoFromFile(
      OutputArtifact deployInfoArtifact) throws ExecutionException {
    ImmutableList.Builder<IntellijPluginDeployInfo> result = ImmutableList.builder();
    try (InputStream inputStream = deployInfoArtifact.getInputStream()) {
      IntellijPluginDeployInfo.Builder builder = IntellijPluginDeployInfo.newBuilder();
      TextFormat.Parser parser = TextFormat.Parser.newBuilder().setAllowUnknownFields(true).build();
      parser.merge(new InputStreamReader(inputStream, UTF_8), builder);
      IntellijPluginDeployInfo deployInfo = builder.build();
      result.add(deployInfo);
    } catch (IOException e) {
      throw new ExecutionException(e);
    }
    return result.build();
  }

  private ImmutableMap<OutputArtifact, File> getFilesToDeploy(
      Collection<IntellijPluginDeployInfo> deployInfos, String buildSystem)
      throws ExecutionException {
    ImmutableMap.Builder<OutputArtifact, File> result = ImmutableMap.builder();
    for (IntellijPluginDeployInfo deployInfo : deployInfos) {
      for (IntellijPluginDeployFile deployFile : deployInfo.getDeployFilesList()) {
        result.put(
            getArtifactFromDeployFile(deployFile, buildSystem),
            new File(sandboxPluginDirectory(sandboxHome), deployFile.getDeployLocation()));
      }
      for (IntellijPluginDeployFile deployFile : deployInfo.getJavaAgentDeployFilesList()) {
        result.put(
            getArtifactFromDeployFile(deployFile, buildSystem),
            new File(sandboxPluginDirectory(sandboxHome), deployFile.getDeployLocation()));
      }
    }
    return result.build();
  }

  private OutputArtifact getArtifactFromDeployFile(
      IntellijPluginDeployFile deployFile, String buildSystem) throws ExecutionException {
    String relativePath =
        buildArtifactsMap.keySet().stream()
            .filter(
                key ->
                    key.endsWith(
                        StringUtil.trimStart(
                            deployFile.getExecutionPath(),
                            String.format("%s-out/", buildSystem.toLowerCase(Locale.ROOT)))))
            .findAny()
            .orElseThrow(
                () ->
                    new ExecutionException(
                        String.format(
                            "Plugin file '%s' not found. Did the build fail?",
                            deployFile.getExecutionPath())));
    return buildArtifactsMap.get(relativePath);
  }

  private ImmutableSet<File> listJavaAgentFiles(Collection<IntellijPluginDeployInfo> deployInfos) {
    ImmutableSet.Builder<File> result = ImmutableSet.builder();
    for (IntellijPluginDeployInfo deployInfo : deployInfos) {
      for (IntellijPluginDeployFile deployFile : deployInfo.getJavaAgentDeployFilesList()) {
        result.add(new File(sandboxPluginDirectory(sandboxHome), deployFile.getDeployLocation()));
      }
    }
    return result.build();
  }

  private static File sandboxPluginDirectory(String sandboxHome) {
    return new File(sandboxHome, "plugins");
  }

  private static void copyFileToSandbox(OutputArtifact deployArtifact, File dest)
      throws ExecutionException {
    try (BufferedInputStream stream = deployArtifact.getInputStream()) {
      boolean unused = dest.getParentFile().mkdirs();
      Files.write(stream.readAllBytes(), dest);
      unused = dest.setExecutable(true, true);
      dest.deleteOnExit();
    } catch (IOException e) {
      throw new ExecutionException("Error copying plugin file to sandbox", e);
    }
  }

  static class DeployedPluginInfo {
    final ImmutableSet<File> javaAgents;

    DeployedPluginInfo(ImmutableSet<File> javaAgents) {
      this.javaAgents = javaAgents;
    }
  }
}
