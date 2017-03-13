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
package com.android.tools.idea.gradle.project.sync.setup.module.android;

import com.android.builder.model.AndroidProject;
import com.android.sdklib.AndroidTargetHash;
import com.android.sdklib.AndroidVersion;
import com.android.tools.idea.IdeInfo;
import com.android.tools.idea.gradle.project.model.AndroidModuleModel;
import com.android.tools.idea.gradle.project.sync.GradleSyncState;
import com.android.tools.idea.gradle.project.sync.ng.SyncAction;
import com.android.tools.idea.project.hyperlink.NotificationHyperlink;
import com.android.tools.idea.gradle.project.sync.hyperlink.OpenFileHyperlink;
import com.android.tools.idea.project.messages.SyncMessage;
import com.android.tools.idea.gradle.project.sync.messages.GradleSyncMessages;
import com.android.tools.idea.gradle.project.sync.setup.module.AndroidModuleSetupStep;
import com.android.tools.idea.gradle.util.PositionInFile;
import com.android.tools.idea.sdk.IdeSdks;
import com.android.tools.idea.sdk.Jdks;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Lists;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.NonNavigatable;
import com.intellij.psi.TokenType;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyLexer;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;

import java.util.List;

import static com.android.tools.idea.project.messages.MessageType.ERROR;
import static com.android.tools.idea.gradle.util.GradleUtil.getGradleBuildFile;
import static com.intellij.pom.java.LanguageLevel.JDK_1_7;

public class JdkModuleSetupStep extends AndroidModuleSetupStep {
  private static final String PROJECT_CONFIGURATION_SYNC_MESSAGE_GROUP = "Project Configuration";

  @NotNull private final IdeSdks myIdeSdks;
  @NotNull private final Jdks myJdks;
  @NotNull private final CompileSdkVersionFinder myCompileSdkVersionFinder;

  @SuppressWarnings("unused") // Invoked by IDEA
  public JdkModuleSetupStep() {
    this(IdeSdks.getInstance(), Jdks.getInstance());
  }

  @VisibleForTesting
  JdkModuleSetupStep(@NotNull IdeSdks ideSdks, @NotNull Jdks jdks) {
    myIdeSdks = ideSdks;
    myJdks = jdks;
    myCompileSdkVersionFinder = new CompileSdkVersionFinder();
  }

  @Override
  protected void doSetUpModule(@NotNull Module module,
                               @NotNull IdeModifiableModelsProvider ideModelsProvider,
                               @NotNull AndroidModuleModel androidModel,
                               @Nullable SyncAction.ModuleModels gradleModels,
                               @Nullable ProgressIndicator indicator) {
    if (IdeInfo.getInstance().isAndroidStudio()) {
      setUpInAndroidStudio(module, androidModel);
    }
  }

  @VisibleForTesting
  void setUpInAndroidStudio(@NotNull Module module, @NotNull AndroidModuleModel androidModel) {
    AndroidProject androidProject = androidModel.getAndroidProject();
    String compileTarget = androidProject.getCompileTarget();

    AndroidVersion version = AndroidTargetHash.getPlatformVersion(compileTarget);
    if (version != null && version.getFeatureLevel() >= 21) {
      Sdk jdk = myIdeSdks.getJdk();
      if (jdk != null && !myJdks.isApplicableJdk(jdk, JDK_1_7)) {
        Project project = module.getProject();

        SyncMessage msg;
        String text = "compileSdkVersion " + compileTarget + " requires compiling with JDK 7 or newer.";
        VirtualFile buildFile = getGradleBuildFile(module);

        if (buildFile != null) {
          msg = reportWrongJdkError(project, text, buildFile);
        }
        else {
          msg = reportWrongJdkError(project, text);
        }

        GradleSyncMessages.getInstance(project).report(msg);
        GradleSyncState.getInstance(project).getSummary().setWrongJdkFound(true);
      }
    }
  }

  @NotNull
  private SyncMessage reportWrongJdkError(@NotNull Project project, @NotNull String text, @NotNull VirtualFile buildFile) {
    int line = -1;
    int column = -1;
    Document document = FileDocumentManager.getInstance().getDocument(buildFile);
    if (document != null) {
      int offset = myCompileSdkVersionFinder.findOffsetIn(document.getText());
      if (offset > -1) {
        line = document.getLineNumber(offset);
        if (line > -1) {
          int lineStartOffset = document.getLineStartOffset(line);
          column = offset - lineStartOffset;
        }
      }
    }

    PositionInFile position = new PositionInFile(buildFile, line, column);
    SyncMessage msg = new SyncMessage(project, PROJECT_CONFIGURATION_SYNC_MESSAGE_GROUP, ERROR, position, text);

    List<NotificationHyperlink> quickFixes = Lists.newArrayList(myJdks.getWrongJdkQuickFixes(project));
    quickFixes.add(new OpenFileHyperlink(buildFile.getPath(), "Open build.gradle File", line, column));
    msg.add(quickFixes);

    return msg;
  }

  @NotNull
  private SyncMessage reportWrongJdkError(@NotNull Project project, @NotNull String text) {
    SyncMessage msg = new SyncMessage(PROJECT_CONFIGURATION_SYNC_MESSAGE_GROUP, ERROR, NonNavigatable.INSTANCE, text);
    msg.add(myJdks.getWrongJdkQuickFixes(project));

    return msg;
  }

  @VisibleForTesting
  static class CompileSdkVersionFinder {
    // Returns the offset where the 'compileSdkVersion' value is in a build.gradle file.
    int findOffsetIn(@NotNull String buildFileContents) {
      GroovyLexer lexer = new GroovyLexer();
      lexer.start(buildFileContents);

      int end = -1;

      while (lexer.getTokenType() != null) {
        IElementType type = lexer.getTokenType();
        String text = lexer.getTokenText();
        if (type == GroovyTokenTypes.mIDENT) {
          if ("compileSdkVersion".equals(text)) {
            end = lexer.getTokenEnd();
          }
          else if (end > -1) {
            return end;
          }
        }
        else if (type == TokenType.WHITE_SPACE && end > -1) {
          end++;
        }
        else if (end > -1) {
          return end;
        }
        lexer.advance();
      }

      return -1;
    }
  }
}
