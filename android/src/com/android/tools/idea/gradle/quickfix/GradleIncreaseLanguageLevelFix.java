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
package com.android.tools.idea.gradle.quickfix;

import com.android.tools.idea.gradle.parser.BuildFileKey;
import com.android.tools.idea.gradle.parser.GradleBuildFile;
import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.codeInsight.daemon.impl.analysis.IncreaseLanguageLevelFix;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.JavaSdkVersion;
import com.intellij.openapi.projectRoots.JdkVersionUtil;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.LanguageLevelModuleExtensionImpl;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.android.tools.idea.gradle.util.GradleUtil.getAndroidProject;
import static com.intellij.openapi.module.ModuleUtilCore.findModuleForFile;

/**
 * The quickfix for increasing language level by modifying the build.gradle and sync the project.
 * Most of the code is duplicated from {@link IncreaseLanguageLevelFix} except
 * the {@link GradleIncreaseLanguageLevelFix#invoke} method.
 */
public class GradleIncreaseLanguageLevelFix extends AbstractGradleAwareFix {
  private static final Logger LOG = Logger.getInstance(GradleIncreaseLanguageLevelFix.class);

  @NotNull private final LanguageLevel myLevel;
  @NotNull private final GradleBuildFile myBuildFile;

  public GradleIncreaseLanguageLevelFix(@NotNull LanguageLevel targetLevel, @NotNull GradleBuildFile buildFile) {
    myLevel = targetLevel;
    myBuildFile = buildFile;
  }

  @Override
  @NotNull
  public String getText() {
    return CodeInsightBundle.message("set.language.level.to.0", myLevel.getPresentableText());
  }

  @Override
  @NotNull
  public String getFamilyName() {
    return CodeInsightBundle.message("set.language.level");
  }

  private static boolean isJdkSupportsLevel(@Nullable final Sdk jdk, @NotNull LanguageLevel level) {
    if (jdk == null) {
      return true;
    }
    String versionString = jdk.getVersionString();
    JavaSdkVersion version = versionString == null ? null : JdkVersionUtil.getVersion(versionString);
    return version != null && version.getMaxLanguageLevel().isAtLeast(level);
  }

  @Override
  public boolean isAvailable(@NotNull final Project project, @Nullable final Editor editor, @Nullable final PsiFile file) {
    if (file == null) {
      return false;
    }
    VirtualFile virtualFile = file.getVirtualFile();
    if (virtualFile == null) {
      return false;
    }

    Module module = findModuleForFile(virtualFile, project);
    return module != null && isLanguageLevelAcceptable(project, module, myLevel);
  }

  private static boolean isLanguageLevelAcceptable(@NotNull Project project, @NotNull Module module, @NotNull LanguageLevel level) {
    return isJdkSupportsLevel(getRelevantJdk(project, module), level);
  }

  @Override
  public void invoke(@NotNull final Project project, @Nullable Editor editor, @Nullable PsiFile file) {
    if (file == null) {
      return;
    }
    VirtualFile virtualFile = file.getVirtualFile();
    if (virtualFile == null) {
      return;
    }

    final Module module = findModuleForFile(virtualFile, project);
    final LanguageLevel moduleLevel = module == null ? null : LanguageLevelModuleExtensionImpl.getInstance(module).getLanguageLevel();

    runWriteCommandActionAndSync(project, new Runnable() {
      @Override
      public void run() {
        if (moduleLevel != null && isLanguageLevelAcceptable(project, module, myLevel)) {
          String gradleJavaVersion = getGradleJavaVersionString(myLevel);
          if (getAndroidProject(module) != null) {
            myBuildFile.setValue(BuildFileKey.SOURCE_COMPATIBILITY, gradleJavaVersion);
            myBuildFile.setValue(BuildFileKey.TARGET_COMPATIBILITY, gradleJavaVersion);
          }
          else {
            LOG.error("Setting language level on Java module is not supported");
          }
          registerUndoAction(project);
        }
        else {
          LOG.error("Tried to set language level without specify a module");
        }
      }
    }, null);
  }

  @Nullable
  private static Sdk getRelevantJdk(@NotNull Project project, @Nullable Module module) {
    Sdk projectJdk = ProjectRootManager.getInstance(project).getProjectSdk();
    Sdk moduleJdk = module == null ? null : ModuleRootManager.getInstance(module).getSdk();
    return moduleJdk == null ? projectJdk : moduleJdk;
  }

  @Override
  public boolean startInWriteAction() {
    return false;
  }

  @NotNull
  private static String getGradleJavaVersionString(@NotNull LanguageLevel languageLevel) {
    return "JavaVersion." + languageLevel.name().replaceAll("JDK", "VERSION");
  }
}
