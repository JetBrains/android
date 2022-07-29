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
package com.android.tools.idea.gradle.project.sync;

import static com.android.SdkConstants.FN_GRADLE_PROPERTIES;
import static com.android.SdkConstants.FN_SETTINGS_GRADLE;
import static com.android.SdkConstants.FN_SETTINGS_GRADLE_KTS;
import static com.android.tools.idea.Projects.getBaseDirPath;
import static com.android.tools.idea.gradle.util.GradleUtil.getGradleBuildFile;
import static com.intellij.openapi.vfs.VfsUtil.findFileByIoFile;

import com.android.annotations.concurrency.GuardedBy;
import com.android.tools.idea.concurrency.AndroidIoManager;
import com.android.tools.idea.gradle.project.model.NdkModuleModel;
import com.android.tools.idea.gradle.project.upgrade.AssistantInvoker;
import com.android.tools.idea.gradle.util.GradleWrapper;
import com.android.tools.idea.res.AndroidFileChangeListener;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Lists;
import com.intellij.ide.impl.ProjectUtilKt;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileEditorManagerEvent;
import com.intellij.openapi.fileEditor.FileEditorManagerListener;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectUtil;
import com.intellij.openapi.startup.StartupActivity;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiTreeChangeAdapter;
import com.intellij.psi.PsiTreeChangeEvent;
import com.intellij.psi.PsiTreeChangeListener;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.ui.EditorNotifications;
import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.function.Consumer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrCodeBlock;

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

  @NotNull private final FileEditorManagerListener myFileEditorListener;

  public static class UpdateHashesStartupActivity implements StartupActivity.DumbAware {
    @Override
    public void runActivity(@NotNull Project project) {
      // Populate build file hashes on project startup.
      getInstance(project).scheduleUpdateFileHashes();
    }
  }

  @NotNull
  public static GradleFiles getInstance(@NotNull Project project) {
    return project.getService(GradleFiles.class);
  }

  private GradleFiles(@NotNull Project project) {
    myProject = project;

    GradleFileChangeListener fileChangeListener = new GradleFileChangeListener(this);
    myFileEditorListener = new FileEditorManagerListener() {
      @Override
      public void selectionChanged(@NotNull FileEditorManagerEvent event) {
        maybeAddOrRemovePsiTreeListener(event.getNewFile(), fileChangeListener);
      }
    };

    if (myProject.isDefault()) return;

    // Add a listener to see when gradle files are being edited.
    myProject.getMessageBus().connect().subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, myFileEditorListener);
  }

  private void maybeAddOrRemovePsiTreeListener(@Nullable VirtualFile file, @NotNull PsiTreeChangeListener fileChangeListener) {
    if (file == null || !file.isValid()) {
      return;
    }

    PsiFile psiFile = PsiManager.getInstance(myProject).findFile(file);
    if (psiFile == null) {
      return;
    }

    // Always remove first before possibly adding to prevent the case that the listener could be added twice.
    PsiManager.getInstance(myProject).removePsiTreeChangeListener(fileChangeListener);

    if (isGradleFile(psiFile) || isExternalBuildFile(psiFile)) {
      PsiManager.getInstance(myProject).addPsiTreeChangeListener(fileChangeListener);
    }
  }

  @NotNull
  @VisibleForTesting
  FileEditorManagerListener getFileEditorListener() {
    return myFileEditorListener;
  }

  @VisibleForTesting
  boolean hasHashForFile(@NotNull VirtualFile file) {
    synchronized (myLock) {
      return myFileHashes.containsKey(file);
    }
  }

  public void removeChangedFiles() {
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

  private static void putHashForFile(@NotNull Map<VirtualFile, Integer> map, @NotNull VirtualFile file) {
    Integer hash = computeHash(file);
    if (hash != null) {
      map.put(file, hash);
    }
  }

  private void storeHashesForFiles(@NotNull Map<VirtualFile, Integer> files) {
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
  private static Integer computeHash(@NotNull VirtualFile file) {
    if (!file.isValid()) return null;
    Document document = FileDocumentManager.getInstance().getDocument(file);
    return document == null ? null : document.getText().hashCode();
  }

  private boolean areHashesEqual(@NotNull VirtualFile file) {
    Integer oldHash = getStoredHashForFile(file);
    return oldHash != null && oldHash.equals(computeHash(file));
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

  private void updateFileHashes() {
    Project project = myProject;
    if (project.isDisposed()) {
      return;
    }

    ExecutorService executorService = AndroidIoManager.getInstance().getBackgroundDiskIoExecutor();
    ProgressManager progressManager = ProgressManager.getInstance();
    ProgressIndicator progressIndicator = progressManager.getProgressIndicator();
    Application application = ApplicationManager.getApplication();

    // Local map to minimize time holding myLock
    Map<VirtualFile, Integer> fileHashes = new HashMap<>();

    Runnable computeWrapperHashRunnable = () -> {
      GradleWrapper gradleWrapper = GradleWrapper.find(project);
      if (gradleWrapper != null) {
        File propertiesFilePath = gradleWrapper.getPropertiesFilePath();
        if (propertiesFilePath.isFile()) {
          VirtualFile propertiesFile = gradleWrapper.getPropertiesFile();
          if (propertiesFile != null) {
            application.runReadAction(() -> putHashForFile(fileHashes, propertiesFile));
          }
        }
      }
    };
    Future wrapperHashFuture = executorService.submit(
      () -> progressManager.executeProcessUnderProgress(computeWrapperHashRunnable, progressIndicator)
    );
    try {
      wrapperHashFuture.get();
    } catch (InterruptedException | ExecutionException e) {
      /* ignored */
    }

    // Clean external build files before they are repopulated.
    removeExternalBuildFiles();
    List<VirtualFile> externalBuildFiles = new ArrayList<>();

    List<Module> modules = Lists.newArrayList(ModuleManager.getInstance(project).getModules());

    Consumer<Module> computeHashes = module -> {
      VirtualFile buildFile = getGradleBuildFile(module);
      if (buildFile != null) {
        ProgressManager.checkCanceled();
        File path = VfsUtilCore.virtualToIoFile(buildFile);
        if (path.isFile()) {
          application.runReadAction(() -> putHashForFile(fileHashes, buildFile));
        }
      }
      NdkModuleModel ndkModuleModel = NdkModuleModel.get(module);
      if (ndkModuleModel != null) {
        for (File externalBuildFile : ndkModuleModel.getBuildFiles()) {
          ProgressManager.checkCanceled();
          if (externalBuildFile.isFile()) {
            // TODO find a better way to find a VirtualFile without refreshing the file system. It is expensive.
            VirtualFile virtualFile = findFileByIoFile(externalBuildFile, true);
            externalBuildFiles.add(virtualFile);
            if (virtualFile != null) {
              application.runReadAction(() -> putHashForFile(fileHashes, virtualFile));
            }
          }
        }
      }
    };

    modules.stream()
      .map(module ->
             executorService.submit(
               () -> progressManager.executeProcessUnderProgress(() -> computeHashes.accept(module), progressIndicator)
             )
      )
      .forEach(future -> {
        try {
          future.get();
        }
        catch (InterruptedException | ExecutionException e) {
          // ignored, the hashes won't be updated. This will cause areGradleFilesModified to return true.
        }
      });

    storeExternalBuildFiles(externalBuildFiles);

    String[] fileNames = {FN_SETTINGS_GRADLE, FN_SETTINGS_GRADLE_KTS, FN_GRADLE_PROPERTIES};
    File rootFolderPath = getBaseDirPath(myProject);
    VirtualFile rootFolder = ProjectUtil.guessProjectDir(myProject);
    Runnable projectWideFilesRunnable = () -> {
      for (String fileName : fileNames) {
        ProgressManager.checkCanceled();
        File filePath = new File(rootFolderPath, fileName);
        if (filePath.isFile()) {
          VirtualFile virtualFile = rootFolder.findChild(fileName);
          if (virtualFile != null && virtualFile.exists() && !virtualFile.isDirectory()) {
            application.runReadAction(() -> putHashForFile(fileHashes, virtualFile));
          }
        }
      }
      ProgressManager.checkCanceled();
      File gradlePath = new File(rootFolderPath, "gradle");
      if (gradlePath.isDirectory()) {
        File[] gradleFiles = gradlePath.listFiles((dir, name) -> name.endsWith(".versions.toml"));
        for (File tomlFile : gradleFiles) {
          ProgressManager.checkCanceled();
          if (tomlFile.isFile()) {
            VirtualFile virtualFile = VfsUtil.findFileByIoFile(tomlFile, false);
            if (virtualFile != null && virtualFile.exists() && !virtualFile.isDirectory()) {
              application.runReadAction(() -> putHashForFile(fileHashes, virtualFile));
            }
          }
        }
      }
    };
    if (rootFolder != null) {
      Future projectWideFilesFuture = executorService.submit(
        () -> progressManager.executeProcessUnderProgress(projectWideFilesRunnable, progressIndicator)
      );
      try {
        projectWideFilesFuture.get();
      } catch (InterruptedException | ExecutionException e) {
        /* ignored */
      }
    }
    storeHashesForFiles(fileHashes);
  }

  /**
   * Schedules an update to the currently stored hashes for each of the gradle build files.
   */
  private void scheduleUpdateFileHashes() {
    if (ApplicationManager.getApplication().isUnitTestMode()) {
      // If we are running in tests, we might be invoked directly from a WriteCommand, which would mean that our attempts to
      // do background ReadActions and wait for them will deadlock.  Schedule us for later on the EDT but without the write lock, for
      // consistent order of operations.
      ApplicationManager.getApplication().invokeLater(this::updateFileHashes);
    } else {
      // If we are not running in tests, schedule ourselves on a background thread so that we don't accidentally freeze the UI if our
      // disk IO is slow.
      ProjectUtilKt.executeOnPooledIoThread(myProject, this::updateFileHashes);
    }
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
    return ApplicationManager.getApplication().runReadAction((Computable<Boolean>)() -> !checkHashesOfChangedFiles());
  }

  public boolean areExternalBuildFilesModified() {
    return ApplicationManager.getApplication().runReadAction((Computable<Boolean>)() -> {
      synchronized (myLock) {
        return !filterHashes(myChangedExternalFiles);
      }
    });
  }

  public boolean isGradleFile(@NotNull PsiFile psiFile) {
    return AndroidFileChangeListener.isGradleFile(psiFile);
  }

  public boolean isExternalBuildFile(@NotNull PsiFile psiFile) {
    synchronized (myLock) {
      return myExternalBuildFiles.contains(psiFile.getVirtualFile());
    }
  }

  public void resetChangedFilesState() {
    scheduleUpdateFileHashes();
    removeChangedFiles();
  }

  void maybeProcessSyncStarted() {
    if (!myProject.isInitialized()) {
      return;
    }
    resetChangedFilesState();
  }

  /**
   * Listens for changes to the PsiTree of gradle build files. If a tree changes in any
   * meaningful way then relevant file is recorded. A change is meaningful under the following
   * conditions:
   * <p>
   * 1) Only whitespace has been added and deleted
   * 2) The whitespace doesn't affect the structure of the files psi tree
   * <p>
   * For example, adding spaces to the end of a line is not a meaningful change, but adding a new
   * line in between a line i.e "apply plugin: 'java'" -> "apply plugin: \n'java'" will be meaningful.
   * <p>
   * Note: We need to use both sets of before (beforeChildAddition, etc) and after methods (childAdded, etc)
   * on the listener. This is because, for some reason, the events we care about on some files are sometimes
   * only triggered with the children set in the after method and sometimes no after method is triggered
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
      processEvent(event, event.getChild());
    }

    @Override
    public void beforeChildRemoval(@NotNull PsiTreeChangeEvent event) {
      processEvent(event, event.getChild());
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
      processEvent(event, event.getChild());
    }

    @Override
    public void childRemoved(@NotNull PsiTreeChangeEvent event) {
      processEvent(event, event.getChild());
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

      // This code may be run before the project is initialized, and we need the project to be initialized to get the PsiManager.
      if (!myGradleFiles.myProject.isInitialized() || !PsiManager.getInstance(myGradleFiles.myProject).isInProject(psiFile)) {
        return;
      }

      boolean foundChange = false;
      for (PsiElement element : elements) {
        if (element == null || element instanceof PsiWhiteSpace || element instanceof PsiComment) {
          continue;
        }

        if (element.getNode().getElementType().equals(GroovyTokenTypes.mNLS)) {
          if (element.getParent() == null) {
            continue;
          }
          if (element.getParent() instanceof GrCodeBlock || element.getParent() instanceof PsiFile) {
            continue;
          }
        }

        foundChange = true;
        break;
      }

      if (foundChange) {
        myGradleFiles.addChangedFile(psiFile.getVirtualFile(), isExternalBuildFile);
        EditorNotifications.getInstance(psiFile.getProject()).updateNotifications(psiFile.getVirtualFile());
        myGradleFiles.myProject.getService(AssistantInvoker.class).expireProjectUpgradeNotifications(myGradleFiles.myProject);
      }
    }
  }
}
