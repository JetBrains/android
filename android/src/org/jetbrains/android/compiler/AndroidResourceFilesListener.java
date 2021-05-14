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
package org.jetbrains.android.compiler;

import static org.jetbrains.android.util.AndroidUtils.findSourceRoot;

import com.android.tools.idea.lang.aidl.AidlFileType;
import com.android.tools.idea.lang.rs.AndroidRenderscriptFileType;
import com.android.tools.idea.res.AndroidFileChangeListener;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileTypes.FileTypeRegistry;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.newvfs.BulkFileListener;
import com.intellij.openapi.vfs.newvfs.events.VFileEvent;
import com.intellij.util.containers.MultiMap;
import com.intellij.util.ui.update.MergingUpdateQueue;
import com.intellij.util.ui.update.Update;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jetbrains.android.dom.manifest.Manifest;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.facet.AndroidRootUtil;
import org.jetbrains.android.util.AndroidUtils;
import org.jetbrains.annotations.NotNull;

public class AndroidResourceFilesListener implements Disposable, BulkFileListener {
  private static final Key<String> CACHED_PACKAGE_KEY = Key.create("ANDROID_RESOURCE_LISTENER_CACHED_PACKAGE");

  private final MergingUpdateQueue myQueue;
  private final Project myProject;

  public AndroidResourceFilesListener(@NotNull Project project) {
    myProject = project;
    myQueue = new MergingUpdateQueue("AndroidResourcesCompilationQueue", 300, true, null, this, null, false);
    ApplicationManager.getApplication().getMessageBus().connect(this).subscribe(VirtualFileManager.VFS_CHANGES, this);
  }

  @Override
  public void after(@NotNull List<? extends @NotNull VFileEvent> events) {
    Set<VirtualFile> filesToProcess = getFilesToProcess(events);

    if (!filesToProcess.isEmpty()) {
      myQueue.queue(new MyUpdate(filesToProcess));
    }
  }

  @NotNull
  private static Set<VirtualFile> getFilesToProcess(@NotNull List<? extends VFileEvent> events) {
    Set<VirtualFile> result = new HashSet<>();

    for (VFileEvent event : events) {
      VirtualFile file = event.getFile();

      if (file != null && AndroidFileChangeListener.isRelevantFile(file)) {
        result.add(file);
      }
    }
    return result;
  }

  @Override
  public void dispose() {
  }

  private class MyUpdate extends Update {
    private final Set<VirtualFile> myFiles;

    MyUpdate(@NotNull Set<VirtualFile> files) {
      super(files);
      myFiles = files;
    }

    @Override
    public void run() {
      if (ApplicationManager.getApplication().isUnitTestMode()) {
        return;
      }

      MultiMap<Module, AndroidAutogeneratorMode> map =
          ApplicationManager.getApplication().runReadAction(
              (Computable<MultiMap<Module, AndroidAutogeneratorMode>>)() -> computeCompilersToRunAndInvalidateLocalAttributesMap());

      if (map.isEmpty()) {
        return;
      }

      for (Map.Entry<Module, Collection<AndroidAutogeneratorMode>> entry : map.entrySet()) {
        Module module = entry.getKey();
        AndroidFacet facet = AndroidFacet.getInstance(module);
        if (facet == null) {
          continue;
        }

        if (!ModuleSourceAutogenerating.requiresAutoSourceGeneration(facet)) {
          continue;
        }

        ModuleSourceAutogenerating sourceAutogenerator = ModuleSourceAutogenerating.getInstance(facet);
        assert sourceAutogenerator != null;

        for (AndroidAutogeneratorMode mode : entry.getValue()) {
          sourceAutogenerator.scheduleSourceRegenerating(mode);
        }
      }
    }

    @NotNull
    private MultiMap<Module, AndroidAutogeneratorMode> computeCompilersToRunAndInvalidateLocalAttributesMap() {
      if (myProject.isDisposed()) {
        return MultiMap.empty();
      }
      MultiMap<Module, AndroidAutogeneratorMode> result = MultiMap.create();

      for (VirtualFile file : myFiles) {
        Module module = ModuleUtilCore.findModuleForFile(file, myProject);

        if (module == null || module.isDisposed()) {
          continue;
        }
        AndroidFacet facet = AndroidFacet.getInstance(module);

        if (facet == null) {
          continue;
        }

        List<AndroidAutogeneratorMode> modes = computeCompilersToRunAndInvalidateLocalAttributesMap(facet, file);
        if (!modes.isEmpty()) {
          result.putValues(module, modes);
        }
      }
      return result;
    }

    @SuppressWarnings("deprecation")
    @NotNull
    private List<AndroidAutogeneratorMode> computeCompilersToRunAndInvalidateLocalAttributesMap(AndroidFacet facet, VirtualFile file) {
      VirtualFile parent = file.getParent();

      if (parent == null) {
        return Collections.emptyList();
      }
      Module module = facet.getModule();
      VirtualFile manifestFile = AndroidRootUtil.getPrimaryManifestFile(facet);
      List<AndroidAutogeneratorMode> modes = new ArrayList<>();

      if (Comparing.equal(manifestFile, file)) {
        Manifest manifest = Manifest.getMainManifest(facet);
        String aPackage = manifest != null ? manifest.getPackage().getValue() : null;
        String cachedPackage = facet.getUserData(CACHED_PACKAGE_KEY);

        if (cachedPackage != null && !cachedPackage.equals(aPackage)) {
          String aptGenDirPath = AndroidRootUtil.getAptGenSourceRootPath(facet);
          AndroidCompileUtil.removeDuplicatingClasses(module, cachedPackage, AndroidUtils.R_CLASS_NAME, null, aptGenDirPath);
        }
        facet.putUserData(CACHED_PACKAGE_KEY, aPackage);
        modes.add(AndroidAutogeneratorMode.BUILDCONFIG);
      }
      else if (FileTypeRegistry.getInstance().isFileOfType(file, AidlFileType.INSTANCE)) {
        VirtualFile sourceRoot = findSourceRoot(module, file);
        if (sourceRoot != null && !Comparing.equal(AndroidRootUtil.getAidlGenDir(facet), sourceRoot)) {
          modes.add(AndroidAutogeneratorMode.AIDL);
        }
      }
      else if (file.getFileType() == AndroidRenderscriptFileType.INSTANCE) {
        final VirtualFile sourceRoot = findSourceRoot(module, file);
        if (sourceRoot != null && !Comparing.equal(AndroidRootUtil.getRenderscriptGenDir(facet), sourceRoot)) {
          modes.add(AndroidAutogeneratorMode.RENDERSCRIPT);
        }
      }
      return modes;
    }

    @Override
    public boolean canEat(Update update) {
      return update instanceof MyUpdate && myFiles.containsAll(((MyUpdate)update).myFiles);
    }
  }
}
