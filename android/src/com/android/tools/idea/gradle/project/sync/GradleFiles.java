/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.gradle.project.sync;

import com.android.annotations.VisibleForTesting;
import com.android.annotations.concurrency.GuardedBy;
import com.android.tools.idea.gradle.project.model.NdkModuleModel;
import com.android.tools.idea.gradle.util.GradleWrapper;
import com.google.common.collect.Lists;
import com.intellij.concurrency.JobLauncher;
import com.intellij.lang.properties.PropertiesFileType;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.GroovyFileType;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;

import java.io.File;
import java.util.*;

import static com.android.SdkConstants.*;
import static com.android.tools.idea.Projects.getBaseDirPath;
import static com.android.tools.idea.gradle.util.GradleUtil.getGradleBuildFile;
import static com.intellij.openapi.vfs.VfsUtil.findFileByIoFile;

public class GradleFiles {
  @NotNull private final Project myProject;

  @NotNull private final Object myLock = new Object();

  @GuardedBy("myLock")
  @NotNull
  private final Set<VirtualFile> myChangedFiles = new HashSet<>();

  @GuardedBy("myLock")
  @NotNull
  private final Set<VirtualFile> myChangedExternalFiles = new HashSet<>();

  @GuardedBy("myLock")
  @NotNull
  private final Map<VirtualFile, Integer> myFileHashes = new HashMap<>();

  @GuardedBy("myLock")
  @NotNull
  private final Set<VirtualFile> myExternalBuildFiles = new HashSet<>();

  @NotNull private final SyncListener mySyncListener = new SyncListener();

  @NotNull
  public static GradleFiles getInstance(@NotNull Project project) {
    return ServiceManager.getService(project, GradleFiles.class);
  }

  private GradleFiles(@NotNull Project project) {
    myProject = project;
    PsiManager.getInstance(myProject).addPsiTreeChangeListener(new GradleFileChangeListener(this));
    GradleSyncState.subscribe(myProject, mySyncListener);
    // Populate build file hashes on creation.
    if (myProject.isInitialized()) {
      updateFileHashes();
    }
    else {
      StartupManager.getInstance(myProject).registerPostStartupActivity(this::updateFileHashes);
    }
  }

  @VisibleForTesting
  @NotNull
  GradleSyncListener getSyncListener() {
    //noinspection ReturnOfInnerClass
    return mySyncListener;
  }

  private void removeChangedFiles() {
    synchronized (myLock) {
      myChangedFiles.clear();
      myChangedExternalFiles.clear();
    }
  }

  private void addChangedFile(@NotNull VirtualFile file, boolean isExternal) {
    synchronized (myLock) {
      if (isExternal) {
        myChangedExternalFiles.add(file);
      }
      else {
        myChangedFiles.add(file);
      }
    }
  }

  private void putHashForFile(@NotNull Map<VirtualFile, Integer> map, @NotNull VirtualFile file) {
    Integer hash = computeHash(file);
    if (hash != null) {
      map.put(file, hash);
    }
  }

  private void storeHashsForFiles(@NotNull Map<VirtualFile, Integer> files) {
    synchronized (myLock) {
      myFileHashes.clear();
      myFileHashes.putAll(files);
    }
  }

  /**
   * Gets the hash value for a given file from the map and stores the value as the first element
   * in hashValue. If this method returns false then the file was not in the map and the value
   * in hashValue should be ignored.
   */
  @Nullable
  private Integer getStoredHashForFile(@NotNull VirtualFile file) {
    synchronized (myLock) {
      return myFileHashes.get(file);
    }
  }

  private boolean containsChangedFile(@NotNull VirtualFile file) {
    synchronized (myLock) {
      return myChangedFiles.contains(file) || myChangedExternalFiles.contains(file);
    }
  }

  private void removeExternalBuildFiles() {
    synchronized (myLock) {
      myExternalBuildFiles.clear();
    }
  }

  private void storeExternalBuildFiles(@NotNull Collection<VirtualFile> externalBuildFiles) {
    synchronized (myLock) {
      myExternalBuildFiles.addAll(externalBuildFiles);
    }
  }

  /**
   * Computes a hash for a given {@code VirtualFile} by using the string obtained from its {@code PsiFile},
   * and stores it as the first element in hashValue. If this method returns false the hash was not computed
   * and its value should not be used.
   */
  @Nullable
  private Integer computeHash(@NotNull VirtualFile file) {
    Computable<PsiFile> computable = () -> PsiManager.getInstance(myProject).findFile(file);
    PsiFile psiFile = ApplicationManager.getApplication().runReadAction(computable);
    if (psiFile != null && psiFile.isValid()) {
      return psiFile.getText().hashCode();
    }
    return null;
  }

  private boolean areHashesEqual(@NotNull VirtualFile file) {
    Integer oldHash = getStoredHashForFile(file);

    return oldHash != null ? oldHash.equals(computeHash(file)) : false;
  }

  /**
   * Checks whether or not any of the files in myChangedFiles and myChangedExternalFiles are actually
   * modified by comparing their hashes. Returns true if all files in myChangedFiles and
   * myChangedExternalFiles had the same hashes, false otherwise.
   */
  private boolean checkHashesOfChangedFiles() {
    synchronized (myLock) {
      return filterHashes(myChangedFiles) && filterHashes(myChangedExternalFiles);
    }
  }

  /**
   * Filters the files given removing any that have a hash matching the last one stored. Returns true if
   * the filtered collection is empty, false otherwise.
   */
  private boolean filterHashes(@NotNull Collection<VirtualFile> files) {
    boolean status = true;
    Set<VirtualFile> toRemove = new HashSet<>();
    for (VirtualFile file : files) {
      if (!areHashesEqual(file)) {
        status = false;
      }
      else {
        toRemove.add(file);
      }
    }
    files.removeAll(toRemove);
    return status;
  }

  /**
   * Updates the currently stored hashes for each of the gradle build files.
   */
  private void updateFileHashes() {
    // Local map to minimize time holding myLock
    Map<VirtualFile, Integer> fileHashes = new HashMap<>();
    GradleWrapper gradleWrapper = GradleWrapper.find(myProject);
    if (gradleWrapper != null) {
      File propertiesFilePath = gradleWrapper.getPropertiesFilePath();
      if (propertiesFilePath.isFile()) {
        VirtualFile propertiesFile = gradleWrapper.getPropertiesFile();
        if (propertiesFile != null) {
          putHashForFile(fileHashes, propertiesFile);
        }
      }
    }

    // Clean external build files before they are repopulated.
    removeExternalBuildFiles();
    List<VirtualFile> externalBuildFiles = new ArrayList<>();

    List<Module> modules = Lists.newArrayList(ModuleManager.getInstance(myProject).getModules());
    JobLauncher jobLauncher = JobLauncher.getInstance();
    jobLauncher.invokeConcurrentlyUnderProgress(modules, null, true /* fail fast */, (module) -> {
      VirtualFile buildFile = getGradleBuildFile(module);
      if (buildFile != null) {
        putHashForFile(fileHashes, buildFile);
      }
      NdkModuleModel ndkModuleModel = NdkModuleModel.get(module);
      if (ndkModuleModel != null) {
        for (File externalBuildFile : ndkModuleModel.getAndroidProject().getBuildFiles()) {
          // TODO find a better way to find a VirtualFile without refreshing the file systerm. It is expensive.
          VirtualFile virtualFile = findFileByIoFile(externalBuildFile, true);
          externalBuildFiles.add(virtualFile);
          if (virtualFile != null) {
            putHashForFile(fileHashes, virtualFile);
          }
        }
      }
      return true;
    });

    storeExternalBuildFiles(externalBuildFiles);

    String[] fileNames = {FN_SETTINGS_GRADLE, FN_GRADLE_PROPERTIES};
    File rootFolderPath = getBaseDirPath(myProject);
    for (String fileName : fileNames) {
      File filePath = new File(rootFolderPath, fileName);
      if (filePath.isFile()) {
        VirtualFile rootFolder = myProject.getBaseDir();
        assert rootFolder != null;
        VirtualFile virtualFile = rootFolder.findChild(fileName);
        if (virtualFile != null && virtualFile.exists() && !virtualFile.isDirectory()) {
          putHashForFile(fileHashes, virtualFile);
        }
      }
    }

    storeHashsForFiles(fileHashes);
  }

  /**
   * Indicates whether a project sync with Gradle is needed if the following files:
   * <ul>
   * <li>gradle.properties</li>
   * <li>build.gradle</li>
   * <li>settings.gradle</li>
   * <li>external build files (e.g. cmake files)</li>
   * </ul>
   * were modified since last sync.
   *
   * @return {@code true} if any of the Gradle files changed, {@code false} otherwise.
   */
  public boolean areGradleFilesModified() {
    // Checks if any file in myChangedFiles actually has changes.
    return !checkHashesOfChangedFiles();
  }

  public boolean areExternalBuildFilesModified() {
    synchronized (myLock) {
      return !filterHashes(myChangedExternalFiles);
    }
  }

  public boolean isGradleFile(@NotNull PsiFile psiFile) {
    if (psiFile.getFileType() == GroovyFileType.GROOVY_FILE_TYPE) {
      VirtualFile file = psiFile.getVirtualFile();
      if (file != null && EXT_GRADLE.equals(file.getExtension())) {
        return true;
      }
    }
    if (psiFile.getFileType() == PropertiesFileType.INSTANCE) {
      VirtualFile file = psiFile.getVirtualFile();
      if (file != null && (FN_GRADLE_PROPERTIES.equals(file.getName()) || FN_GRADLE_WRAPPER_PROPERTIES.equals(file.getName()))) {
        return true;
      }
    }

    return false;
  }

  public boolean isExternalBuildFile(@NotNull PsiFile psiFile) {
    synchronized (myLock) {
      return myExternalBuildFiles.contains(psiFile.getVirtualFile());
    }
  }

  /**
   * Listens for GradleSync events in order to clear the files that have changed and update the
   * file hashes for each of the gradle build files.
   */
  private class SyncListener extends GradleSyncListener.Adapter {
    @Override
    public void syncStarted(@NotNull Project project) {
      maybeProcessSyncStarted(project);
    }

    private void maybeProcessSyncStarted(@NotNull Project project) {
      if (!project.isInitialized() && project.equals(myProject)) {
        return;
      }

      if (ApplicationManager.getApplication().isReadAccessAllowed()) {
        updateFileHashes();
        removeChangedFiles();
      }
      else {
        ApplicationManager.getApplication().runReadAction(() -> {
          removeChangedFiles();
          updateFileHashes();
        });
      }
    }
  }

  /**
   * Listens for changes to the PsiTree of gradle build files. If a tree changes in any
   * meaningful way then relevant file is recorded. A change is meaningful under the following
   * conditions:
   *
   * 1) Only whitespace has been added and deleted
   * 2) The whitespace doesn't affect the structure of the files psi tree
   *
   * For example, adding spaces to the end of a line is not a meaningful change, but adding a new
   * line in between a line i.e "apply plugin: 'java'" -> "apply plugin: \n'java'" will be meaningful.
   *
   * Note: We need to use both sets of before (beforeChildAddition, etc) and after methods (childAdded, etc)
   * on the listener. This is because, for some reason, the events we care about on some files are sometimes
   * only triggered with the chilren set in the after method and othertimes no after method is triggered
   * at all.
   */
  private static class GradleFileChangeListener extends PsiTreeChangeAdapter {
    @NotNull
    private final GradleFiles myGradleFiles;

    private GradleFileChangeListener(@NotNull GradleFiles gradleFiles) {
      myGradleFiles = gradleFiles;
    }

    @Override
    public void beforeChildAddition(@NotNull PsiTreeChangeEvent event) {
      // newChild is sometimes null, in this case child is normally populated instead.
      if (event.getNewChild() != null) {
        processEvent(event, event.getNewChild());
      }
      else {
        processEvent(event, event.getChild());
      }
    }

    @Override
    public void beforeChildRemoval(@NotNull PsiTreeChangeEvent event) {
      processEvent(event, event.getOldChild());
    }

    @Override
    public void beforeChildReplacement(@NotNull PsiTreeChangeEvent event) {
      processEvent(event, event.getNewChild(), event.getOldChild());
    }

    @Override
    public void beforeChildMovement(@NotNull PsiTreeChangeEvent event) {
      processEvent(event, event.getChild());
    }

    @Override
    public void beforeChildrenChange(@NotNull PsiTreeChangeEvent event) {
      processEvent(event, event.getOldChild(), event.getNewChild());
    }

    @Override
    public void childAdded(@NotNull PsiTreeChangeEvent event) {
      // newChild is sometimes null, in this case child is normally populated instead.
      if (event.getNewChild() != null) {
        processEvent(event, event.getNewChild());
      }
      else {
        processEvent(event, event.getChild());
      }
    }

    @Override
    public void childRemoved(@NotNull PsiTreeChangeEvent event) {
      processEvent(event, event.getOldChild());
    }

    @Override
    public void childReplaced(@NotNull PsiTreeChangeEvent event) {
      processEvent(event, event.getNewChild(), event.getOldChild());
    }

    @Override
    public void childMoved(@NotNull PsiTreeChangeEvent event) {
      processEvent(event, event.getChild());
    }

    @Override
    public void childrenChanged(@NotNull PsiTreeChangeEvent event) {
      processEvent(event, event.getOldChild(), event.getNewChild());
    }

    private void processEvent(@NotNull PsiTreeChangeEvent event, @NotNull PsiElement... elements) {
      PsiFile psiFile = event.getFile();
      if (psiFile == null) {
        return;
      }

      boolean isExternalBuildFile = myGradleFiles.isExternalBuildFile(psiFile);

      if (!myGradleFiles.isGradleFile(psiFile) && !isExternalBuildFile) {
        return;
      }

      if (myGradleFiles.containsChangedFile(psiFile.getVirtualFile())) {
        return;
      }

      boolean foundChange = false;
      for (PsiElement element : elements) {
        if (element == null || element instanceof PsiWhiteSpace) {
          continue;
        }

        if (element.getNode().getElementType().equals(GroovyTokenTypes.mNLS)) {
          continue;
        }

        foundChange = true;
        break;
      }

      if (foundChange) {
        myGradleFiles.addChangedFile(psiFile.getVirtualFile(), isExternalBuildFile);
      }
    }
  }
}
