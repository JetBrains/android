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
package org.jetbrains.android.actions;

import com.android.ide.common.resources.configuration.FolderConfiguration;
import com.android.resources.ResourceFolderType;
import com.android.resources.ResourceType;
import com.android.tools.idea.res.ResourceNameValidator;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.function.Function;

/**
 * Extension point to decide what UI is appropriate to present for the Create New Android Resources flow.
 *
 * Different build systems may have have a different way of modeling resources, and therefore selecting the res/ directory
 * where a new resource should be placed. E.g., some build systems may have a single resource directory per {@link Module}
 * and {@link com.android.builder.model.SourceProvider}, while other build systems may not. This extension point allows
 * a build system to customize the UI.
 *
 * The typical workflow is:
 * (1) A user action triggers the need to create a new resource (e.g., quick fix)
 * (2) The IDE finds an applicable handler to get the UI element (e.g., a dialog)
 * (3) The IDE shows the dialog and the user interacts with the dialog.
 * (4) Once the dialog is validated and closed, the new PsiElements representing the new resources are created and returned
 * for further processing (e.g., the IDE navigates to the new file).
 *
 * This is step 2 of the workflow, and the calling environment handles the other steps.
 *
 * There should only be one handler for each type of project (E.g., Gradle handler for Gradle-built projects), otherwise the
 * first applicable handler will be taken with no real guarantee on the ordering of handlers.
 */
public interface NewResourceCreationHandler {

  ExtensionPointName<NewResourceCreationHandler> EP_NAME =
    ExtensionPointName.create("org.jetbrains.android.actions.newResourceCreationHandler");

  @NotNull
  static NewResourceCreationHandler getInstance(@NotNull Project project) {
    for (NewResourceCreationHandler extension : EP_NAME.getExtensions()) {
      if (extension.isApplicable(project)) {
        return extension;
      }
    }
    return DefaultNewResourceCreationHandler.SINGLETON;
  }

  /**
   * Determine if the handler instance applies to the given project.
   *
   * @param project a project.
   * @return true if handled
   */
  boolean isApplicable(@NotNull Project project);

  /**
   * Return the UI to handle creating a new resource directory (e.g., menu-en).
   *
   * @param project          the project for the new resource
   * @param module           the module that's in context when invoked.  Null if unknown.
   * @param resType          the ResourceFolderType of the new resource, which may restrict the name of the new directory.
   *                         Null if unknown from invoking context.
   * @param resDirectory     the base res/ directory for the sub directory. NotNull if this has been decided from
   *                         the context already, and null otherwise.
   * @param dataContext      any context from the invocation (e.g., context from right-click on project panel). Null if no context.
   * @param validatorFactory creates validator that's appropriate to the context, after the user fills in required fields.
   * @return a dialog
   */
  @NotNull
  CreateResourceDirectoryDialogBase createNewResourceDirectoryDialog(
    @NotNull Project project,
    @Nullable Module module,
    @Nullable ResourceFolderType resType,
    @Nullable PsiDirectory resDirectory,
    @Nullable DataContext dataContext,
    @NotNull CreateResourceDirectoryDialogBase.ValidatorFactory validatorFactory);

  /**
   * Return the UI to handle creating a new resource file (e.g., activity_main.xml, within layout-large).
   *
   * @param facet               the facet that is in scope of the invoking context
   * @param actions             create resource of type subactions (each subaction holds knowledge of possible root elements, etc.)
   * @param folderType          pre-determined resource folder type. Null if the user should choose the type.
   * @param filename            pre-determined name for the new file. May be known and restricted due from quick-fix,
   *                            or may simply be a suggestion. Null if there if not predefined or no suggestion.
   * @param rootElement         pre-determined or suggestion for root element. Null if none.
   * @param folderConfiguration pre-determined folder configuration. Null if none.
   * @param chooseFileName      true if the user should still be able to choose the filename (even given a suggested filename).
   * @param chooseModule        true if the user should choose the module
   * @param resDirectory        pre-determined base res/ directory. Null if none.
   * @param dataContext         any context from the invocation (e.g., context from right-click on project panel). Null if no context.
   * @param validatorFactory    creates a validator that's appropriate to the context, after the user fills in required fields.
   * @return a dialog
   */
  @NotNull
  CreateResourceFileDialogBase createNewResourceFileDialog(
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
    @NotNull CreateResourceFileDialogBase.ValidatorFactory validatorFactory);

  /**
   * Return the UI to handle creating a new resource value (e.g., a new color, or a new string).
   * The returned panel may be embedded in other UI. E.g., choosing a value for the new color
   * (with a color pallet) may be different than choosing a value for a new string,
   * and the embedder can be customized supply different surrounding UI.
   *
   * @param module               the module that is in scope of the invoking context
   * @param resourceType         the type of the new resource
   * @param folderType           the folder type of the new resource
   * @param resourceName         any pre-determined resource name or suggestion for the new resource. Null if none.
   * @param resourceValue        any pre-determined resource value for the new resource. Null if none.
   * @param chooseName           true if the user should choose the resource name. False if resourceName is fixed (e.g., for quick fix)
   * @param chooseValue          true if the user should choose the resource value. False if already chosen by some other mechanism.
   * @param chooseFilename       true if the user should choose the filename. False is already pre-determined and fixed.
   * @param defaultFile          the XML file to place this new resource. Null if no suggestion.
   * @param contextFile          file that is in scope of this invocation context.
   * @param nameValidatorFactory creates a validator for the resource name, given the scope
   *                             (e.g., prevents clashing with resources that are already defined in the scope)
   * @return a panel
   */
  CreateXmlResourcePanel createNewResourceValuePanel(
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
    @NotNull final Function<Module, ResourceNameValidator> nameValidatorFactory);
}
