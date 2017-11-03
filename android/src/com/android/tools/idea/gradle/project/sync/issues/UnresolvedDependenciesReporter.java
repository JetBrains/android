/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.gradle.project.sync.issues;

import com.android.builder.model.SyncIssue;
import com.android.ide.common.repository.GradleCoordinate;
import com.android.repository.api.ProgressIndicator;
import com.android.repository.api.RemotePackage;
import com.android.repository.api.RepoPackage;
import com.android.repository.impl.meta.RepositoryPackages;
import com.android.sdklib.repository.AndroidSdkHandler;
import com.android.tools.idea.IdeInfo;
import com.android.tools.idea.gradle.dsl.model.GradleBuildModel;
import com.android.tools.idea.gradle.dsl.model.repositories.RepositoriesModel;
import com.android.tools.idea.gradle.project.sync.GradleSyncState;
import com.android.tools.idea.gradle.project.sync.hyperlink.*;
import com.android.tools.idea.gradle.util.PositionInFile;
import com.android.tools.idea.project.hyperlink.NotificationHyperlink;
import com.android.tools.idea.project.messages.SyncMessage;
import com.android.tools.idea.sdk.AndroidSdks;
import com.android.tools.idea.sdk.progress.StudioLoggerProgressIndicator;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.NonNavigatable;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.Function;
import org.gradle.tooling.model.UnsupportedMethodException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyLexer;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static com.android.builder.model.SyncIssue.TYPE_UNRESOLVED_DEPENDENCY;
import static com.android.ide.common.repository.SdkMavenRepository.*;
import static com.android.tools.idea.gradle.dsl.model.util.GoogleMavenRepository.hasGoogleMavenRepository;
import static com.android.tools.idea.gradle.project.sync.issues.ConstraintLayoutFeature.isSupportedInSdkManager;
import static com.android.tools.idea.gradle.util.GradleUtil.getGradleBuildFile;
import static com.android.tools.idea.gradle.util.Projects.isOfflineBuildModeEnabled;
import static com.android.tools.idea.project.messages.MessageType.ERROR;
import static com.android.tools.idea.sdk.StudioSdkUtil.reloadRemoteSdkWithModalProgress;
import static com.intellij.openapi.util.text.StringUtil.unquoteString;
import static org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes.mSTRING_LITERAL;

public class UnresolvedDependenciesReporter extends BaseSyncIssuesReporter {
  private static final String UNRESOLVED_DEPENDENCIES_GROUP = "Unresolved dependencies";
  private static final String OPEN_FILE_HYPERLINK_TEXT = "Open File";

  @NotNull
  public static UnresolvedDependenciesReporter getInstance() {
    return ServiceManager.getService(UnresolvedDependenciesReporter.class);
  }

  @Override
  int getSupportedIssueType() {
    return TYPE_UNRESOLVED_DEPENDENCY;
  }

  @Override
  void report(@NotNull SyncIssue syncIssue, @NotNull Module module, @Nullable VirtualFile buildFile) {
    String dependency = syncIssue.getData();
    if (dependency != null) {
      report(dependency, module, buildFile);
    }
    else {
      reportWithoutDependencyInfo(syncIssue, module, buildFile);
    }
  }

  public void report(@NotNull Collection<String> unresolvedDependencies, @NotNull Module module) {
    if (unresolvedDependencies.isEmpty()) {
      return;
    }
    VirtualFile buildFile = getGradleBuildFile(module);
    for (String dependency : unresolvedDependencies) {
      report(dependency, module, buildFile);
    }

    GradleSyncState.getInstance(module.getProject()).getSummary().setSyncErrorsFound(true);
  }

  private void report(@NotNull String dependency, @NotNull Module module, @Nullable VirtualFile buildFile) {
    String group = "Unresolved Android dependencies";
    GradleCoordinate coordinate = GradleCoordinate.parseCoordinateString(dependency);

    RepoPackage constraintPackage = null;
    if (coordinate != null) {
      ProgressIndicator indicator = new StudioLoggerProgressIndicator(getClass());
      reloadRemoteSdkWithModalProgress();
      Collection<RemotePackage> remotePackages = getRemotePackages(indicator);
      constraintPackage = findBestPackageMatching(coordinate, remotePackages);
    }

    List<NotificationHyperlink> quickFixes = new ArrayList<>();
    if (dependency.startsWith("com.android.support.constraint:constraint-layout:") && !isSupportedInSdkManager(module)) {
      quickFixes.add(new FixAndroidGradlePluginVersionHyperlink());
    }
    else if (constraintPackage != null) {
      quickFixes.add(new InstallArtifactHyperlink(constraintPackage.getPath()));
    }
    else if (dependency.startsWith("com.android.support")) {
      addGoogleMavenRepositoryHyperlink(module, buildFile, quickFixes);
    }
    else if (dependency.startsWith("com.google.android")) {
      quickFixes.add(new InstallRepositoryHyperlink(GOOGLE, dependency));
    }
    else {
      group = UNRESOLVED_DEPENDENCIES_GROUP;
      Project project = module.getProject();

      if (isOfflineBuildModeEnabled(project)) {
        quickFixes.add(new DisableOfflineModeHyperlink());
      }
    }

    String text = "Failed to resolve: " + dependency;

    SyncMessage message;
    if (buildFile != null) {
      PositionInFile position = findDependencyPosition(dependency, buildFile);
      message = new SyncMessage(module.getProject(), group, ERROR, position, text);
      String hyperlinkText = position.line > -1 ? "Show in File" : OPEN_FILE_HYPERLINK_TEXT;
      quickFixes.add(new OpenFileHyperlink(buildFile.getPath(), hyperlinkText, position.line, position.column));
    }
    else {
      message = new SyncMessage(group, ERROR, NonNavigatable.INSTANCE, text);
    }

    if (IdeInfo.getInstance().isAndroidStudio()) {
      if (coordinate != null) {
        quickFixes.add(new ShowDependencyInProjectStructureHyperlink(module, coordinate));
      }
    }

    message.add(quickFixes);
    getSyncMessages(module).report(message);
  }

  @NotNull
  private static PositionInFile findDependencyPosition(@NotNull String dependency, @NotNull VirtualFile buildFile) {
    int line = -1;
    int column = -1;

    Document document = FileDocumentManager.getInstance().getDocument(buildFile);
    if (document != null) {
      TextRange textRange = findDependency(dependency, document);
      if (textRange != null) {
        line = document.getLineNumber(textRange.getStartOffset());
        if (line > -1) {
          int lineStartOffset = document.getLineStartOffset(line);
          column = textRange.getStartOffset() - lineStartOffset;
        }
      }
    }

    return new PositionInFile(buildFile, line, column);
  }

  @NotNull
  private static Collection<RemotePackage> getRemotePackages(@NotNull ProgressIndicator indicator) {
    AndroidSdkHandler sdkHandler = AndroidSdks.getInstance().tryToChooseSdkHandler();
    RepositoryPackages packages = sdkHandler.getSdkManager(indicator).getPackages();
    return packages.getRemotePackages().values();
  }

  @Nullable
  private static TextRange findDependency(@NotNull String dependency, @NotNull Document buildFile) {
    Function<Pair<String, GroovyLexer>, TextRange> consumer = pair -> {
      GroovyLexer lexer = pair.getSecond();
      return TextRange.create(lexer.getTokenStart() + 1, lexer.getTokenEnd() - 1);
    };
    GroovyLexer lexer = new GroovyLexer();
    lexer.start(buildFile.getText());
    while (lexer.getTokenType() != null) {
      IElementType type = lexer.getTokenType();
      if (type == mSTRING_LITERAL) {
        String text = unquoteString(lexer.getTokenText());
        if (text.startsWith(dependency)) {
          return consumer.fun(Pair.create(text, lexer));
        }
      }
      lexer.advance();
    }
    return null;
  }

  private void reportWithoutDependencyInfo(@NotNull SyncIssue syncIssue, @NotNull Module module, @Nullable VirtualFile buildFile) {
    // getData can be null if the unresolved dependency is on a sub-module due to non-matching variant attributes.
    // Use getMessage to display the sync error in that case.
    // b/64213214.
    String text = syncIssue.getMessage();
    List<NotificationHyperlink> quickFixes = new ArrayList<>();

    SyncMessage message;
    if (buildFile != null) {
      PositionInFile position = new PositionInFile(buildFile, -1, 1);
      message = new SyncMessage(module.getProject(), UNRESOLVED_DEPENDENCIES_GROUP, ERROR, position, text);
      quickFixes.add(new OpenFileHyperlink(buildFile.getPath(), OPEN_FILE_HYPERLINK_TEXT, position.line, position.column));
    }
    else {
      message = new SyncMessage(UNRESOLVED_DEPENDENCIES_GROUP, ERROR, NonNavigatable.INSTANCE, text);
    }

    // Show the "extra info" of the SyncIssue in a dialog.
    // See: https://issuetracker.google.com/62251247
    List<String> extraInfo = new ArrayList<>();
    try {
      List<String> multiLineMessage = syncIssue.getMultiLineMessage();
      if (multiLineMessage != null) {
        extraInfo.addAll(multiLineMessage);
      }
    }
    catch (UnsupportedMethodException ex) {
      // SyncIssue.getMultiLineMessage() is not available for pre 3.0 plugins.
    }

    if (!extraInfo.isEmpty()) {
      quickFixes.add(new ShowSyncIssuesDetailsHyperlink(text, extraInfo));
    }

    message.add(quickFixes);
    getSyncMessages(module).report(message);
  }

  /**
   * Append a quick fix to add Google Maven repository to solve a dependency in a module in a list of fixes if needed.
   *
   * @param module Module that has a dependency on the repository.
   * @param buildFile Build file where the dependency is.
   * @param fixes List of hyperlinks in which the quickfix will be added if the reposirory is not already used.
   */
  private static void addGoogleMavenRepositoryHyperlink(@NotNull Module module,
                                                        @Nullable VirtualFile buildFile,
                                                        @NotNull List<NotificationHyperlink> fixes) {
    Project project = module.getProject();
    if (buildFile != null) {
      GradleBuildModel moduleBuildModel = GradleBuildModel.parseBuildFile(buildFile, project, module.getName());
      if (!hasGoogleMavenRepository(moduleBuildModel.repositories())) {
        fixes.add(new AddGoogleMavenRepositoryHyperlink(buildFile));
      }
    }
    else {
      GradleBuildModel projectBuildModel = GradleBuildModel.get(project);
      if (projectBuildModel != null) {
        RepositoriesModel repositories = projectBuildModel.repositories();
        if (!hasGoogleMavenRepository(repositories)) {
          fixes.add(new AddGoogleMavenRepositoryHyperlink(projectBuildModel.getVirtualFile()));
        }
      }
    }
  }
}
