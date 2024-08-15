/*
 * Copyright 2023 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.cpp.qsync;

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import com.google.idea.blaze.base.util.UrlUtil;
import com.google.idea.blaze.common.Context;
import com.google.idea.blaze.common.PrintOutput;
import com.google.idea.blaze.cpp.CppSupportChecker;
import com.google.idea.blaze.qsync.cc.FlagResolver;
import com.google.idea.blaze.qsync.project.ProjectPath;
import com.google.idea.blaze.qsync.project.ProjectProto;
import com.google.idea.blaze.qsync.project.ProjectProto.CcCompilationContext;
import com.google.idea.blaze.qsync.project.ProjectProto.CcCompilerFlagSet;
import com.google.idea.blaze.qsync.project.ProjectProto.CcCompilerSettings;
import com.google.idea.blaze.qsync.project.ProjectProto.CcLanguage;
import com.google.idea.blaze.qsync.project.ProjectProto.CcSourceFile;
import com.google.idea.blaze.qsync.project.ProjectProto.CcWorkspace;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.EmptyProgressIndicator;
import com.intellij.util.containers.MultiMap;
import com.jetbrains.cidr.lang.CLanguageKind;
import com.jetbrains.cidr.lang.toolchains.CidrCompilerSwitches;
import com.jetbrains.cidr.lang.toolchains.CidrToolEnvironment;
import com.jetbrains.cidr.lang.workspace.OCCompilerSettings;
import com.jetbrains.cidr.lang.workspace.OCResolveConfiguration;
import com.jetbrains.cidr.lang.workspace.OCWorkspace;
import com.jetbrains.cidr.lang.workspace.compiler.ClangCompilerKind;
import com.jetbrains.cidr.lang.workspace.compiler.CompilerInfoCache;
import com.jetbrains.cidr.lang.workspace.compiler.CompilerInfoCache.Message;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Map;

/** Updates the IJ project model based a {@link CcWorkspace} proto message. */
public class CcProjectModelUpdateOperation implements Disposable {

  private static final String CLIENT_KEY = "ASwB";
  private static final int CLIENT_VERSION = 1;

  private static final Logger logger = Logger.getInstance(CcProjectModelUpdateOperation.class);
  private final Context<?> context;
  private final ProjectPath.Resolver pathResolver;
  private final FlagResolver flagResolver;
  private final OCWorkspace.ModifiableModel modifiableOcWorkspace;
  private final Map<String, CidrCompilerSwitches> compilerSwitches = Maps.newHashMap();
  private final Map<String, OCResolveConfiguration.ModifiableModel> resolveConfigs =
      Maps.newLinkedHashMap();
  private final File compilerWorkingDir;

  CcProjectModelUpdateOperation(
      Context<?> context, OCWorkspace readonlyOcWorkspace, ProjectPath.Resolver pathResolver) {
    this.context = context;
    this.pathResolver = pathResolver;
    this.flagResolver = new FlagResolver(pathResolver);
    // TODO(mathewi) should we use clear=false here and do the diff instead?
    modifiableOcWorkspace = readonlyOcWorkspace.getModifiableModel(CLIENT_KEY, /* clear= */ true);
    modifiableOcWorkspace.setClientVersion(CLIENT_VERSION);
    compilerWorkingDir = pathResolver.resolve(ProjectPath.WORKSPACE_ROOT).toFile();
  }

  /** Visit a {@link CcWorkspace} proto. Should be called from a background thread. */
  public void visitWorkspace(CcWorkspace proto) {
    visitSwitchesMap(proto.getFlagSetsMap());
    for (CcCompilationContext compilationContext : proto.getContextsList()) {
      visitCompilationContext(compilationContext);
    }
  }

  private void visitSwitchesMap(Map<String, CcCompilerFlagSet> flagsetMap) {
    for (Map.Entry<String, CcCompilerFlagSet> e : flagsetMap.entrySet()) {
      compilerSwitches.put(
          e.getKey(), new CidrCompilerSwitches(flagResolver.resolveAll(e.getValue())));
    }
  }

  private void visitCompilationContext(CcCompilationContext ccCc) {
    OCResolveConfiguration.ModifiableModel config =
        modifiableOcWorkspace.addConfiguration(ccCc.getId(), ccCc.getHumanReadableName());

    visitLanguageCompilerSettingsMap(ccCc.getLanguageToCompilerSettingsMap(), config);
    for (CcSourceFile source : ccCc.getSourcesList()) {
      visitSourceFile(source, config);
    }
    resolveConfigs.put(ccCc.getId(), config);
  }

  private void visitLanguageCompilerSettingsMap(
      Map<String, CcCompilerSettings> map, OCResolveConfiguration.ModifiableModel config) {
    for (Map.Entry<String, CcCompilerSettings> e : map.entrySet()) {
      CidrCompilerSwitches switches =
          checkNotNull(compilerSwitches.get(e.getValue().getFlagSetId()));
      if (!CppSupportChecker.isSupportedCppConfiguration(switches, compilerWorkingDir.toPath())) {
        return;
      }
      CLanguageKind lang =
          getLanguageKind(
              ProjectProto.CcLanguage.valueOf(
                  ProjectProto.CcLanguage.getDescriptor().findValueByName(e.getKey())),
              "compiler settings");
      OCCompilerSettings.ModifiableModel compilerSettings =
          config.getLanguageCompilerSettings(lang);
      compilerSettings.setCompiler(
          ClangCompilerKind.INSTANCE, getCompilerExecutable(e.getValue()), compilerWorkingDir);
      compilerSettings.setCompilerSwitches(switches);
    }
  }

  private void visitSourceFile(CcSourceFile source, OCResolveConfiguration.ModifiableModel config) {
    CidrCompilerSwitches switches =
        checkNotNull(compilerSwitches.get(source.getCompilerSettings().getFlagSetId()));
    if (!CppSupportChecker.isSupportedCppConfiguration(
        switches, pathResolver.resolve(ProjectPath.WORKSPACE_ROOT))) {
      // Ignore the file if it's not supported by the current IDE.
      return;
    }
    Path srcPath = Path.of(source.getWorkspacePath());
    CLanguageKind language =
        getLanguageKind(source.getLanguage(), "Source file " + source.getWorkspacePath());
    srcPath = pathResolver.resolve(ProjectPath.workspaceRelative(srcPath));
    if (!Files.exists(srcPath)) {
      logger.warn("Src file not found: " + srcPath);
    }
    OCCompilerSettings.ModifiableModel perSourceCompilerSettings =
        config.addSource(UrlUtil.pathToIdeaDirectoryUrl(srcPath), language);
    perSourceCompilerSettings.setCompilerSwitches(switches);
    perSourceCompilerSettings.setCompiler(
        ClangCompilerKind.INSTANCE,
        getCompilerExecutable(source.getCompilerSettings()),
        compilerWorkingDir);
  }

  private static final ImmutableMap<CcLanguage, CLanguageKind> LANGUAGE_MAP =
      ImmutableMap.of(
          CcLanguage.C, CLanguageKind.C,
          CcLanguage.CPP, CLanguageKind.CPP,
          CcLanguage.OBJ_C, CLanguageKind.OBJ_C,
          CcLanguage.OBJ_CPP, CLanguageKind.OBJ_CPP);

  private CLanguageKind getLanguageKind(CcLanguage language, String whatFor) {
    return Preconditions.checkNotNull(
        LANGUAGE_MAP.get(language), "Invalid language " + language + " for " + whatFor);
  }

  private File getCompilerExecutable(CcCompilerSettings compilerSettings) {
    return pathResolver
        .resolve(ProjectPath.create(compilerSettings.getCompilerExecutablePath()))
        .toFile();
  }

  /** Pre-commits the project update. Should be called from a background thread. */
  public void preCommit() {
    modifiableOcWorkspace.preCommit();
    processCompilerSettings();
  }

  /** Commits the project update. Must be called from the write thread. */
  public void commit() {
    boolean didChange = WriteAction.compute(modifiableOcWorkspace::commit);
    if (!didChange) {
      logger.warn("Project model update did not result in any changes.");
    }
  }

  private void processCompilerSettings() {
    EmptyProgressIndicator indicator = new EmptyProgressIndicator();
    CompilerInfoCache cache = new CompilerInfoCache();
    var session = cache.<String>createSession(indicator);
    boolean sessionClosed = false;
    try {
      var toolEnvironment = new CidrToolEnvironment();
      session.setExpectedJobsCount(resolveConfigs.size());
      for (Map.Entry<String, OCResolveConfiguration.ModifiableModel> e :
          resolveConfigs.entrySet()) {
        session.schedule(
            e.getKey(),
            e.getValue(),
            toolEnvironment,
            pathResolver.resolve(ProjectPath.WORKSPACE_ROOT).toString());
      }

      // Compute all configurations. Block until complete.
      var messages = new MultiMap<String, Message>();
      session.waitForAll(messages);
      sessionClosed = true;

      ImmutableList<Message> frozenMessages =
          messages.freezeValues().values().stream()
              .flatMap(Collection::stream)
              .collect(ImmutableList.toImmutableList());
      frozenMessages.forEach(
          m -> context.output(PrintOutput.log(m.getType().name() + ": " + m.getText())));
    } finally {
      if (!sessionClosed) {
        session.dispose();
      }
    }
  }

  @Override
  public void dispose() {
    OCWorkspaceModifiableModelDisposer.dispose(modifiableOcWorkspace);
  }
}
