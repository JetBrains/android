/*
 * Copyright (C) 2021 The Android Open Source Project
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

package org.jetbrains.android.intentions;

import static com.android.SdkConstants.EXT_GRADLE;
import static com.android.SdkConstants.EXT_GRADLE_KTS;

import com.android.ide.common.gradle.Component;
import com.android.ide.common.gradle.Dependency;
import com.android.ide.common.repository.GoogleMavenArtifactId;
import com.android.ide.common.repository.GradleCoordinate;
import com.android.tools.idea.gradle.dependencies.DependenciesHelper;
import com.android.tools.idea.gradle.dsl.api.GradleBuildModel;
import com.android.tools.idea.gradle.dsl.api.ProjectBuildModel;
import com.android.tools.idea.gradle.dsl.api.dependencies.ArtifactDependencyModel;
import com.android.tools.idea.gradle.dsl.api.dependencies.ArtifactDependencySpec;
import com.android.tools.idea.gradle.dsl.api.dependencies.CommonConfigurationNames;
import com.android.tools.idea.gradle.repositories.RepositoryUrlManager;
import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Sets;
import com.intellij.codeInsight.intention.AbstractIntentionAction;
import com.intellij.codeInsight.intention.HighPriorityAction;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.PopupChooserBuilder;
import com.intellij.psi.PsiFile;
import com.intellij.ui.components.JBList;
import com.intellij.util.IncorrectOperationException;
import java.util.HashSet;
import javax.swing.JList;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.kotlin.psi.KtFile;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;

/**
 * Intention for gradle build files that allows adding library dependencies
 */
public class AndroidAddLibraryDependencyAction extends AbstractIntentionAction implements HighPriorityAction {

  @Override
  @NotNull
  public String getText() {
    return AndroidBundle.message("add.dependency.intention.text");
  }

  @NotNull
  @Override
  public String getFamilyName() {
    return getText();
  }

  @Nullable
  private static Module getModule(@NotNull Project project, @NotNull PsiFile file) {
    ProjectFileIndex index = ProjectRootManager.getInstance(project).getFileIndex();
    return index.getModuleForFile(file.getVirtualFile());
  }

  /**
   * Finds all the "extras repository" dependencies that haven't been already added to the project.
   */
  @NotNull
  private static ImmutableCollection<String> findAllDependencies(@NotNull GradleBuildModel buildModel) {
    HashSet<String> existingDependencies = Sets.newHashSet();
    for (ArtifactDependencyModel dependency : buildModel.dependencies().artifacts()) {
      ProgressManager.checkCanceled();
      existingDependencies.add(dependency.group() + ":" + dependency.name());
    }

    ImmutableList.Builder<String> dependenciesBuilder = ImmutableList.builder();
    RepositoryUrlManager repositoryUrlManager = RepositoryUrlManager.get();
    for (GoogleMavenArtifactId id : GoogleMavenArtifactId.values()) {
      ProgressManager.checkCanceled();
      // Dependency for any version available
      Dependency dependency = id.getDependency("+");

      // Get from the library coordinate only the group and artifactId to check if we have already added it
      if (!existingDependencies.contains(id.toString())) {
        Component resolvedComponent = repositoryUrlManager.resolveDependency(dependency, buildModel.getProject(), null);
        if (resolvedComponent != null) {
          String identifier = resolvedComponent.toIdentifier();
          if (identifier != null) {
            dependenciesBuilder.add(identifier);
          }
        }
      }
    }

    return dependenciesBuilder.build();
  }

  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
    if ((file instanceof GroovyFile || file instanceof KtFile) &&
        (file.getName().endsWith(EXT_GRADLE) || file.getName().endsWith(EXT_GRADLE_KTS))) {
      return AndroidFacet.getInstance(file) != null;
    }
    return false;
  }

  /**
   * Adds the given dependency to the project.
   *
   * @param project
   * @param buildModel
   * @param coordinateString
   */
  private static void addDependency(final @NotNull Project project,
                                    final @NotNull ProjectBuildModel projectModel,
                                    final @NotNull GradleBuildModel buildModel,
                                    @NotNull String coordinateString) {
    GradleCoordinate coordinate = GradleCoordinate.parseCoordinateString(coordinateString);
    if (coordinate == null || coordinate.getArtifactId() == null) {
      return;
    }
    final ArtifactDependencySpec newDependency =
      ArtifactDependencySpec.create(coordinate.getArtifactId(), coordinate.getGroupId(), coordinate.getRevision());

    WriteCommandAction.runWriteCommandAction(project, () -> {
      DependenciesHelper helper = new DependenciesHelper(projectModel);
      String compactNotation = newDependency.compactNotation();
      helper.addDependency(CommonConfigurationNames.IMPLEMENTATION, compactNotation, buildModel);
      projectModel.applyChanges();
    });
  }

  @Override
  public void invoke(@NotNull final Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    Module module = getModule(project, file);
    final ProjectBuildModel projectModel = ProjectBuildModel.get(project);
    final GradleBuildModel buildModel = projectModel.getModuleBuildModel(module);
    if (buildModel == null) {
      return;
    }

    ImmutableCollection<String> dependencies = ProgressManager
      .getInstance()
      .runProcessWithProgressSynchronously(
        () -> findAllDependencies(buildModel),
        "Finding Dependencies",
        true,
        project
      );

    if (dependencies.isEmpty()) {
      return;
    }

    final JList list = new JBList(dependencies);
    JBPopup popup = new PopupChooserBuilder(list).setItemChosenCallback(() -> {
      for (Object selectedValue : list.getSelectedValues()) {
        if (selectedValue == null) {
          return;
        }
        addDependency(project, projectModel, buildModel, (String)selectedValue);
      }
    }).createPopup();
    popup.showInBestPositionFor(editor);
  }
}
