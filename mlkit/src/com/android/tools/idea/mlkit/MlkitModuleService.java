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
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleServiceManager;
import com.intellij.openapi.util.ModificationTracker;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.newvfs.BulkFileListener;
import com.intellij.openapi.vfs.newvfs.events.VFileEvent;
import com.intellij.psi.PsiClass;
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
  private final Map<MlModelMetadata, LightModelClass> myLightModelClassMap = new ConcurrentHashMap<>();

  public static MlkitModuleService getInstance(@NotNull Module module) {
    return Objects.requireNonNull(ModuleServiceManager.getService(module, MlkitModuleService.class));
  }

  public MlkitModuleService(@NotNull Module module) {
    myModule = module;
    myModelFileModificationTracker = new ModelFileModificationTracker(module);
  }

  public ModelFileModificationTracker getModelFileModificationTracker() {
    return myModelFileModificationTracker;
  }

  @Nullable
  public LightModelClass getOrCreateLightModelClass(@NotNull MlModelMetadata modelMetadata) {
    if (!MlkitUtils.isMlModelBindingBuildFeatureEnabled(myModule)) {
      return null;
    }

    return myLightModelClassMap.computeIfAbsent(modelMetadata, modelMetadata1 -> {
      LightModelClassConfig classConfig = MlModelClassGenerator.generateLightModelClass(myModule, modelMetadata1);
      return classConfig != null ? new LightModelClass(myModule, classConfig) : null;
    });
  }

  /**
   * Returns light model classes auto-generated for ML model files in this module's assets folder.
   */
  @NotNull
  public List<PsiClass> getLightModelClassList() {
    if (!MlkitUtils.isMlModelBindingBuildFeatureEnabled(myModule)) {
      return Collections.emptyList();
    }

    return CachedValuesManager.getManager(myModule.getProject()).getCachedValue(myModule, () -> {
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

      List<PsiClass> lightModelClassList = new ArrayList<>();
      for (MlModelMetadata modelMetadata : modelMetadataList) {
        LightModelClass lightModelClass = getOrCreateLightModelClass(modelMetadata);
        if (lightModelClass != null) {
          lightModelClassList.add(lightModelClass);
        }
      }
      return CachedValueProvider.Result.create(lightModelClassList, myModelFileModificationTracker);
    });
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
                getInstance(module).myLightModelClassMap.clear();
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
}
