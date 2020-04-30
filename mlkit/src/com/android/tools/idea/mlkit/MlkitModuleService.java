/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.mlkit;

import com.android.tools.idea.flags.StudioFlags;
import com.android.tools.idea.mlkit.lightpsi.LightModelClass;
import com.android.tools.idea.projectsystem.ProjectSystemUtil;
import com.android.tools.mlkit.MlkitNames;
import com.google.common.base.Strings;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleServiceManager;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.util.ModificationTracker;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.newvfs.BulkFileListener;
import com.intellij.openapi.vfs.newvfs.events.VFileEvent;
import com.intellij.psi.PsiManager;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.util.indexing.FileBasedIndex;
import com.intellij.util.messages.MessageBusConnection;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Module level service for ML Kit plugin.
 */
public class MlkitModuleService {
  private final Module myModule;
  private final ModelFileModificationTracker myModelFileModificationTracker;
  private final Map<String, LightModelClass> myLightModelClassMap = new ConcurrentHashMap<>();
  private final LightModelClassListProvider myLightModelClassListProvider;

  public static MlkitModuleService getInstance(@NotNull Module module) {
    return Objects.requireNonNull(ModuleServiceManager.getService(module, MlkitModuleService.class));
  }

  public MlkitModuleService(@NotNull Module module) {
    myModule = module;
    myModelFileModificationTracker = new ModelFileModificationTracker(module);
    myLightModelClassListProvider = new LightModelClassListProvider(module);
  }

  public ModelFileModificationTracker getModelFileModificationTracker() {
    return myModelFileModificationTracker;
  }

  @Nullable
  public LightModelClass getOrCreateLightModelClass(@NotNull MlModelMetadata modelMetadata) {
    if (!MlkitUtils.isMlModelBindingBuildFeatureEnabled(myModule)) {
      return null;
    }

    return myLightModelClassMap.computeIfAbsent(modelMetadata.myModelFileUrl, fileUrl -> {
      String packageName = ProjectSystemUtil.getModuleSystem(myModule).getPackageName();
      if (packageName == null) {
        Logger.getInstance(MlkitModuleService.class).warn("Can not determine the package name for module: " + myModule.getName());
        return null;
      }

      VirtualFile modelFile = VirtualFileManager.getInstance().findFileByUrl(fileUrl);
      if (modelFile == null) {
        Logger.getInstance(MlkitModuleService.class).warn("Failed to find the virtual file for: " + fileUrl);
        return null;
      }

      String className = MlkitUtils.computeModelClassName(myModule, modelFile);
      if (Strings.isNullOrEmpty(className)) {
        Logger.getInstance(MlkitModuleService.class).warn("Can not determine the class name for: " + fileUrl);
        return null;
      }

      LightModelClassConfig classConfig = new LightModelClassConfig(modelMetadata, packageName + MlkitNames.PACKAGE_SUFFIX, className);
      return new LightModelClass(myModule, modelFile, classConfig);
    });
  }

  /**
   * Returns light model classes auto-generated for ML model files in this module's assets folder.
   */
  @NotNull
  public List<LightModelClass> getLightModelClassList() {
    if (!MlkitUtils.isMlModelBindingBuildFeatureEnabled(myModule) || DumbService.isDumb(myModule.getProject())) {
      return Collections.emptyList();
    }

    return CachedValuesManager.getManager(myModule.getProject()).getCachedValue(myModule, myLightModelClassListProvider);
  }

  public static class ModelFileModificationTracker implements ModificationTracker {
    private int myModificationCount;

    private ModelFileModificationTracker(@NotNull Module module) {
      if (StudioFlags.ML_MODEL_BINDING.get()) {
        MessageBusConnection connection = module.getMessageBus().connect(module);
        connection.subscribe(VirtualFileManager.VFS_CHANGES, new BulkFileListener() {
          @Override
          public void after(@NotNull List<? extends VFileEvent> events) {
            for (VFileEvent event : events) {
              VirtualFile file = event.getFile();
              if (file != null && MlkitUtils.isModelFileInMlModelsFolder(module, file)) {
                PsiManager.getInstance(module.getProject()).dropResolveCaches();
                getInstance(module).myLightModelClassMap.remove(file.getUrl());
                myModificationCount++;
                return;
              }
            }
          }
        });
      }
    }

    @Override
    public long getModificationCount() {
      return myModificationCount;
    }
  }

  private static class LightModelClassListProvider implements CachedValueProvider<List<LightModelClass>> {
    private final Module myModule;

    private LightModelClassListProvider(@NotNull Module module) {
      myModule = module;
    }

    @Nullable
    @Override
    public Result<List<LightModelClass>> compute() {
      MlkitModuleService service = getInstance(myModule);
      List<MlModelMetadata> modelMetadataList = new ArrayList<>();
      GlobalSearchScope searchScope = MlModelFilesSearchScope.inModule(myModule);
      FileBasedIndex index = FileBasedIndex.getInstance();
      index.processAllKeys(MlModelFileIndex.INDEX_ID, key -> {
        index.processValues(MlModelFileIndex.INDEX_ID, key, null, (file, value) -> {
          if (value.isValidModel()) {
            modelMetadataList.add(value);
          }
          return true;
        }, searchScope);

        return true;
      }, searchScope, null);

      List<LightModelClass> lightModelClassList = new ArrayList<>();
      for (MlModelMetadata modelMetadata : modelMetadataList) {
        LightModelClass lightModelClass = service.getOrCreateLightModelClass(modelMetadata);
        if (lightModelClass != null) {
          lightModelClassList.add(lightModelClass);
        }
      }
      return CachedValueProvider.Result.create(lightModelClassList, service.myModelFileModificationTracker);
    }
  }
}