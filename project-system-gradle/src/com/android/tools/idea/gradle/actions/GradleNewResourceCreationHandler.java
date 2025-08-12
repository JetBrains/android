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
package com.android.tools.idea.gradle.actions;

import com.android.ide.common.resources.configuration.FolderConfiguration;
import com.android.resources.ResourceFolderType;
import com.android.resources.ResourceType;
import com.android.tools.idea.projectsystem.ProjectSystemUtil;
import com.android.tools.idea.projectsystem.gradle.GradleProjectSystem;
import com.android.tools.idea.res.IdeResourceNameValidator;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import java.util.Collection;
import java.util.function.Function;
import org.jetbrains.android.actions.CreateResourceDirectoryDialog;
import org.jetbrains.android.actions.CreateResourceDirectoryDialogBase;
import org.jetbrains.android.actions.CreateResourceFileDialog;
import org.jetbrains.android.actions.CreateResourceFileDialogBase;
import org.jetbrains.android.actions.CreateTypedResourceFileAction;
import org.jetbrains.android.actions.CreateXmlResourcePanel;
import org.jetbrains.android.actions.CreateXmlResourcePanelImpl;
import org.jetbrains.android.actions.NewResourceCreationHandler;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Decides which create resource dialogs to use for Gradle projects.
 */
public class GradleNewResourceCreationHandler implements NewResourceCreationHandler {
  @Override
  public boolean isApplicable(@NotNull Project project) {
    return ProjectSystemUtil.getProjectSystem(project) instanceof GradleProjectSystem;
  }

  @NotNull
  @Override
  public CreateResourceDirectoryDialogBase createNewResourceDirectoryDialog(
    @NotNull Project project,
    @Nullable Module module,
    @Nullable ResourceFolderType resType,
    @Nullable PsiDirectory resDirectory,
    @Nullable DataContext dataContext,
    @NotNull CreateResourceDirectoryDialogBase.ValidatorFactory validatorFactory) {
    return new CreateResourceDirectoryDialog(project, module, resType, resDirectory, dataContext,
                                             validatorFactory);
  }

  @NotNull
  @Override
  public CreateResourceFileDialogBase createNewResourceFileDialog(
    @NotNull AndroidFacet facet,
    @NotNull Collection<CreateTypedResourceFileAction> actions,
    @Nullable ResourceFolderType folderType,
    @Nullable String filename,
    @Nullable String rootElement,
    @Nullable FolderConfiguration folderConfiguration,
    boolean chooseFileName,
    boolean chooseModule,
    @Nullable PsiDirectory resDirectory,
    @Nullable DataContext dataContext,
    @NotNull CreateResourceFileDialogBase.ValidatorFactory validatorFactory) {
    return new CreateResourceFileDialog(facet, actions, folderType, filename, rootElement, folderConfiguration,
                                        chooseFileName, chooseModule, resDirectory, validatorFactory);
  }

  @Override
  public CreateXmlResourcePanel createNewResourceValuePanel(
    @NotNull Module module,
    @NotNull ResourceType resourceType,
    @NotNull ResourceFolderType folderType,
    @Nullable String resourceName,
    @Nullable String resourceValue,
    boolean chooseName,
    boolean chooseValue,
    boolean chooseFilename,
    @Nullable VirtualFile defaultFile,
    @Nullable VirtualFile contextFile,
    @NotNull Function<Module, IdeResourceNameValidator> nameValidatorFactory) {
    return new CreateXmlResourcePanelImpl(module, resourceType, folderType, resourceName, resourceValue,
                                          chooseName, chooseValue, chooseFilename, defaultFile, contextFile, nameValidatorFactory);
  }
}
