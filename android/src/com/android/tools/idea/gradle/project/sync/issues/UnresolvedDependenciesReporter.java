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

import com.android.annotations.VisibleForTesting;
import com.android.builder.model.SyncIssue;
import com.android.ide.common.repository.GradleCoordinate;
import com.android.repository.api.ProgressIndicator;
import com.android.repository.api.RemotePackage;
import com.android.repository.api.RepoPackage;
import com.android.repository.impl.meta.RepositoryPackages;
import com.android.sdklib.repository.AndroidSdkHandler;
import com.android.tools.idea.IdeInfo;
import com.android.tools.idea.gradle.project.model.AndroidModuleModel;
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
import static com.android.tools.idea.gradle.util.GradleUtil.getGradleBuildFile;
import static com.android.tools.idea.gradle.util.Projects.isOfflineBuildModeEnabled;
import static com.android.tools.idea.project.messages.MessageType.ERROR;
import static com.android.tools.idea.sdk.StudioSdkUtil.reloadRemoteSdkWithModalProgress;
import static com.intellij.openapi.util.text.StringUtil.unquoteString;
import static org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes.mSTRING_LITERAL;

public class UnresolvedDependenciesReporter extends BaseSyncIssuesReporter {
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
      // getData can be null if the unresolved dependency is on a sub-module due to non-matching variant attributes.
      // Use getMessage to display the sync error in that case.
      // b/64213214.
      List<String> messages = new ArrayList<>();
      messages.add(syncIssue.getMessage());
      try {
        List<String> multiLineMessage = syncIssue.getMultiLineMessage();
        if (multiLineMessage != null) {
          messages.addAll(multiLineMessage);
        }
      }
      catch (UnsupportedMethodException ex) {
        // SyncIssue.getMultiLineMessage() is not available for pre 3.0 plugins.
      }

      // Since the problem is caused by mismatch between mutliple modules, don't offer open file hyperlinks or other quickfixes.
      SyncMessage syncMessage =
        new SyncMessage("Unresolved dependencies", ERROR, NonNavigatable.INSTANCE, messages.toArray(new String[messages.size()]));
      getSyncMessages(module).report(syncMessage);
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
    if (dependency.startsWith("com.android.support.constraint:constraint-layout:") && !canGetConstraintLayoutFromSdkManager(module)) {
      quickFixes.add(new FixAndroidGradlePluginVersionHyperlink());
    }
    else if (constraintPackage != null) {
      quickFixes.add(new InstallArtifactHyperlink(constraintPackage.getPath()));
    }
    else if (dependency.startsWith("com.android.support")) {
      quickFixes.add(new InstallRepositoryHyperlink(ANDROID, dependency));
    }
    else if (dependency.startsWith("com.google.android")) {
      quickFixes.add(new InstallRepositoryHyperlink(GOOGLE, dependency));
    }
    else {
      group = "Unresolved dependencies";
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
      String hyperlinkText = position.line > -1 ? "Show in File" : "Open File";
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

  @VisibleForTesting
  static boolean canGetConstraintLayoutFromSdkManager(@NotNull Module module) {
    AndroidModuleModel model = AndroidModuleModel.get(module);
    // see https://code.google.com/p/android/issues/detail?id=360563
    return model == null /* 'null' means this is a brand-new project */ || model.getFeatures().isConstraintLayoutSdkLocationSupported();
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
}
