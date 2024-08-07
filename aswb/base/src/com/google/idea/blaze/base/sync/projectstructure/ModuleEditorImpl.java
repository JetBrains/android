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
package com.google.idea.blaze.base.sync.projectstructure;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.idea.blaze.base.io.FileOperationProvider;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.base.settings.BlazeImportSettings;
import com.google.idea.blaze.base.sync.BlazeSyncPlugin;
import com.google.idea.blaze.base.sync.data.BlazeDataStorage;
import com.google.idea.blaze.common.PrintOutput;
import com.intellij.ide.highlighter.ModuleFileType;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider;
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProviderImpl;
import com.intellij.openapi.module.ModifiableModuleModel;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.module.ModuleType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.CompilerModuleExtension;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.impl.ModifiableModelCommitter;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;

/** Module editor implementation. */
public class ModuleEditorImpl implements BlazeSyncPlugin.ModuleEditor {
  private static final Logger logger = Logger.getInstance(ModuleEditorImpl.class.getName());
  private static final String EXTERNAL_SYSTEM_ID_KEY = "external.system.id";
  private static final String EXTERNAL_SYSTEM_ID_VALUE = "Blaze";

  private final Project project;
  private final IdeModifiableModelsProvider modelsProvider;
  private final ModifiableModuleModel moduleModel;
  private final File imlDirectory;
  @VisibleForTesting public Map<String, ModifiableRootModel> modules = Maps.newHashMap();

  public ModuleEditorImpl(Project project, BlazeImportSettings importSettings) {
    this.project = project;
    modelsProvider = new IdeModifiableModelsProviderImpl(project);
    this.moduleModel = modelsProvider.getModifiableModuleModel();

    this.imlDirectory = getImlDirectory(importSettings);
    if (!FileOperationProvider.getInstance().exists(imlDirectory)) {
      if (!imlDirectory.mkdirs()) {
        logger.error("Could not make directory: " + imlDirectory.getPath());
      }
    }
  }

  @Override
  public Module createModule(String moduleName, ModuleType<?> moduleType) {
    Module module = moduleModel.findModuleByName(moduleName);
    if (module == null) {
      File imlFile = new File(imlDirectory, moduleName + ModuleFileType.DOT_DEFAULT_EXTENSION);
      removeImlFile(imlFile);
      module = moduleModel.newModule(imlFile.getPath(), moduleType.getId());
      module.setOption(EXTERNAL_SYSTEM_ID_KEY, EXTERNAL_SYSTEM_ID_VALUE);
    }
    // setOption(..) can be slow on existing modules when the new project model is used. From
    // 2021.2.1 on, setOption(..) will first check whether the option already has the passed value
    // before continuing with the remaining operations
    // (see https://youtrack.jetbrains.com/issue/IDEA-274168#focus=Comments-27-5086318.0-0). Until
    // then, we explicitly do the check ourselves. This should help to avoid the slowness as most of
    // the existing modules should already be configured to have the right type.
    // #api211 Verify that 2021.2 contains the necessary fix. If so, remove the additional check.
    String currentTypeId = module.getOptionValue(Module.ELEMENT_TYPE);
    if (!moduleType.getId().equals(currentTypeId)) {
      module.setOption(Module.ELEMENT_TYPE, moduleType.getId());
    }

    ModifiableRootModel modifiableModel = modelsProvider.getModifiableRootModel(module);
    modules.put(module.getName(), modifiableModel);
    modifiableModel.clear();
    modifiableModel.inheritSdk();
    CompilerModuleExtension compilerSettings =
        modifiableModel.getModuleExtension(CompilerModuleExtension.class);
    if (compilerSettings != null) {
      compilerSettings.inheritCompilerOutputPath(false);
    }

    return module;
  }

  @Override
  public ModifiableRootModel editModule(Module module) {
    return modules.get(module.getName());
  }

  @Override
  @Nullable
  public Module findModule(String moduleName) {
    return moduleModel.findModuleByName(moduleName);
  }

  public void commitWithGc(BlazeContext context) {
    List<Module> orphanModules = Lists.newArrayList();
    for (Module module : ModuleManager.getInstance(project).getModules()) {
      if (!modules.containsKey(module.getName())) {
        orphanModules.add(module);
      }
    }
    if (orphanModules.size() > 0) {
      context.output(
          PrintOutput.log(String.format("Removing %d dead modules", orphanModules.size())));
      for (Module module : orphanModules) {
        if (module.isDisposed()) {
          continue;
        }
        moduleModel.disposeModule(module);
        File imlFile = new File(module.getModuleFilePath());
        removeImlFile(imlFile);
      }
    }

    context.output(PrintOutput.log(String.format("Workspace has %s modules", modules.size())));

    commit();
  }

  @Override
  public void commit() {
    ModifiableModelCommitter.multiCommit(modules.values(), moduleModel);
  }

  private File getImlDirectory(BlazeImportSettings importSettings) {
    return new File(BlazeDataStorage.getProjectDataDir(importSettings), "modules");
  }

  // Delete using the virtual file to ensure that IntelliJ properly updates its index.
  // Otherwise, it is possible for IntelliJ to read the
  // old IML file from its index and behave unpredictably
  // (like failing to save the new IML files to disk).
  private static void removeImlFile(final File imlFile) {
    final VirtualFile imlVirtualFile = VfsUtil.findFileByIoFile(imlFile, true);
    if (imlVirtualFile != null && imlVirtualFile.exists()) {
      ApplicationManager.getApplication()
          .runWriteAction(
              new Runnable() {
                @Override
                public void run() {
                  try {
                    imlVirtualFile.delete(this);
                  } catch (IOException e) {
                    logger.warn(
                        String.format(
                            "Could not delete file: %s, will try to continue anyway.",
                            imlVirtualFile.getPath()),
                        e);
                  }
                }
              });
    }
  }
}
