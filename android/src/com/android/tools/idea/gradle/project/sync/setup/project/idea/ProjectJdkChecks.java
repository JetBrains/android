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
package com.android.tools.idea.gradle.project.sync.setup.project.idea;

import com.android.builder.model.AndroidProject;
import com.android.sdklib.AndroidTargetHash;
import com.android.sdklib.AndroidVersion;
import com.android.tools.idea.gradle.AndroidGradleModel;
import com.android.tools.idea.gradle.project.sync.GradleSyncState;
import com.android.tools.idea.gradle.project.sync.messages.SyncMessage;
import com.android.tools.idea.gradle.project.sync.messages.SyncMessages;
import com.android.tools.idea.gradle.service.notification.hyperlink.NotificationHyperlink;
import com.android.tools.idea.gradle.service.notification.hyperlink.OpenFileHyperlink;
import com.android.tools.idea.gradle.util.PositionInFile;
import com.android.tools.idea.sdk.IdeSdks;
import com.android.tools.idea.sdk.Jdks;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Lists;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.NonNavigatable;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.TokenType;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyLexer;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;

import java.util.List;

import static com.android.tools.idea.gradle.project.sync.messages.MessageType.ERROR;
import static com.android.tools.idea.gradle.service.notification.hyperlink.JdkQuickFixes.getJdkQuickFixes;
import static com.android.tools.idea.gradle.util.GradleUtil.getGradleBuildFile;

final class ProjectJdkChecks {
  private ProjectJdkChecks() {
  }

  static boolean hasCorrectJdkVersion(@NotNull Module module) {
    AndroidGradleModel androidModel = AndroidGradleModel.get(module);
    if (androidModel != null) {
      return hasCorrectJdkVersion(module, androidModel);
    }
    return true;
  }

  static boolean hasCorrectJdkVersion(@NotNull Module module, @NotNull AndroidGradleModel androidModel) {
    AndroidProject androidProject = androidModel.getAndroidProject();
    String compileTarget = androidProject.getCompileTarget();

    AndroidVersion version = AndroidTargetHash.getPlatformVersion(compileTarget);
    if (version != null && version.getFeatureLevel() >= 21) {
      Sdk jdk = IdeSdks.getInstance().getJdk();
      if (jdk != null && !Jdks.getInstance().isApplicableJdk(jdk, LanguageLevel.JDK_1_7)) {
        Project project = module.getProject();

        List<NotificationHyperlink> quickFixes = Lists.newArrayList(getJdkQuickFixes(project));
        SyncMessage msg;
        String text = "compileSdkVersion " + compileTarget + " requires compiling with JDK 7 or newer";
        VirtualFile buildFile = getGradleBuildFile(module);
        String groupName = "Project Configuration";

        if (buildFile != null) {
          int line = -1;
          int column = -1;
          Document document = FileDocumentManager.getInstance().getDocument(buildFile);
          if (document != null) {
            int offset = findCompileSdkVersionValueOffset(document.getText());
            if (offset > -1) {
              line = document.getLineNumber(offset);
              if (line > -1) {
                int lineStartOffset = document.getLineStartOffset(line);
                column = offset - lineStartOffset;
              }
            }
          }

          quickFixes.add(new OpenFileHyperlink(buildFile.getPath(), "Open build.gradle File", line, column));
          PositionInFile position = new PositionInFile(buildFile, line, column);
          msg = new SyncMessage(project, groupName, ERROR, position, text);
        }
        else {
          msg = new SyncMessage(groupName, ERROR, NonNavigatable.INSTANCE, text);
        }

        msg.add(quickFixes);
        SyncMessages.getInstance(project).report(msg);
        GradleSyncState.getInstance(project).getSummary().setWrongJdkFound(true);
        return false;
      }
    }
    return true;
  }

  // Returns the offset where the 'compileSdkVersion' value is in a build.gradle file.
  @VisibleForTesting
  static int findCompileSdkVersionValueOffset(@NotNull String buildFileContents) {
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
