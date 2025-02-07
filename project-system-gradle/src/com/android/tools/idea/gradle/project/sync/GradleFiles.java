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

import com.android.annotations.concurrency.GuardedBy;
import com.android.tools.idea.res.FileRelevanceKt;
import com.google.common.annotations.VisibleForTesting;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.FileEditorManagerEvent;
import com.intellij.openapi.fileEditor.FileEditorManagerListener;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiTreeChangeListener;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.GlobalSearchScopes;
import com.intellij.util.concurrency.AppExecutorUtil;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.function.Consumer;
import kotlin.jvm.functions.Function1;
import kotlin.Unit;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class GradleFiles implements Disposable.Default {
  @NotNull final Project myProject;

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

  @NotNull final GlobalSearchScope myScope;

  @NotNull private final GradleFilesUpdater myUpdater;

  @NotNull
  public static GradleFiles getInstance(@NotNull Project project) {
    return project.getService(GradleFiles.class);
  }

  private GradleFiles(@NotNull Project project) {
    myProject = project;

    myUpdater = GradleFilesUpdater.getInstance(project);

    VirtualFile baseDir = myProject.getBaseDir();
    if (baseDir == null) {
      myScope = GlobalSearchScope.everythingScope(myProject);
    }
    else {
      myScope = GlobalSearchScopes.directoryScope(myProject, baseDir, true);
    }

    GradleFileChangeListener fileChangeListener = new GradleFileChangeListener(this);
    myFileEditorListener = new FileEditorManagerListener() {
      @Override
      public void fileOpened(@NotNull FileEditorManager source, @NotNull VirtualFile file) {
        if (hasHashForFile(file)) {
          if (!areHashesEqual(file)) {
            addChangedFile(file, isExternalBuildFile(file));
          }
        }
      }

      @Override
      public void selectionChanged(@NotNull FileEditorManagerEvent event) {
        maybeAddOrRemovePsiTreeListener(event.getNewFile(), fileChangeListener);
      }
    };

    if (myProject.isDefault()) return;

    for (FileEditor editor : FileEditorManager.getInstance(project).getSelectedEditors()) {
      maybeAddOrRemovePsiTreeListener(editor.getFile(), fileChangeListener);
    }

    // Add a listener to see when gradle files are being edited.
    myProject.getMessageBus().connect().subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, myFileEditorListener);
  }

  private void maybeAddOrRemovePsiTreeListener(@Nullable VirtualFile file, @NotNull PsiTreeChangeListener fileChangeListener) {
    if (file == null) {
      return;
    }

    Callable<GradleFileState> fileStateCallable = () -> {
      if (!file.isValid()) return new GradleFileState(false, false);
      if (!myScope.contains(file)) return new GradleFileState(false, false);

      PsiFile psiFile = PsiManager.getInstance(myProject).findFile(file);
      if (psiFile == null) {
        return new GradleFileState(false, false);
      }

      return new GradleFileState(true, isGradleFile(psiFile) || isExternalBuildFile(psiFile));
    };
    Consumer<GradleFileState> listenerUpdater = state -> {
      if (!state.isValid) return;

      // Always remove first before possibly adding to prevent the case that the listener could be added twice.
      PsiManager.getInstance(myProject).removePsiTreeChangeListener(fileChangeListener);

      if (state.isGradleFile) {
        PsiManager.getInstance(myProject).addPsiTreeChangeListener(fileChangeListener, this);
      }
    };

    Application application = ApplicationManager.getApplication();
    if (application.isUnitTestMode() && application.isDispatchThread()) {
      try {
        listenerUpdater.accept(fileStateCallable.call());
      }
      catch (Exception ignored) { }
    }
    else {
      ReadAction.nonBlocking(fileStateCallable)
        .finishOnUiThread(ModalityState.nonModal(), listenerUpdater)
        .coalesceBy(this)
        .expireWith(this)
        .submit(AppExecutorUtil.getAppExecutorService());
    }
  }

  private record GradleFileState(
    boolean isValid,
    boolean isGradleFile
  ) {
  }

  @NotNull
  @VisibleForTesting
  public FileEditorManagerListener getFileEditorListener() {
    return myFileEditorListener;
  }

  @VisibleForTesting
  public boolean hasHashForFile(@NotNull VirtualFile file) {
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

  void addChangedFile(@NotNull VirtualFile file, boolean isExternal) {
    synchronized (myLock) {
      if (isExternal) {
        myChangedExternalFiles.add(file);
      }
      else {
        myChangedFiles.add(file);
      }
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

  boolean containsChangedFile(@NotNull VirtualFile file) {
    synchronized (myLock) {
      return myChangedFiles.contains(file) || myChangedExternalFiles.contains(file);
    }
  }

  /**
   * Computes a hash for a given {@code VirtualFile} by using the string obtained from its {@code PsiFile},
   * and stores it as the first element in hashValue. If this method returns null the hash was not computed
   * and its value should not be used.
   */
  @Nullable
  static Integer computeHash(@NotNull VirtualFile file) {
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

  Function1<? super GradleFilesUpdater.Result, Unit> updateCallback() {
    return (result) -> {
      synchronized (myLock) {
        myExternalBuildFiles.clear();
        myExternalBuildFiles.addAll(result.getExternalBuildFiles());
        myFileHashes.clear();
        myFileHashes.putAll(result.getHashes());
      }
      return Unit.INSTANCE;
    };
  }

  /**
   * Schedules an update to the currently stored hashes for each of the gradle build files.
   */
  void scheduleUpdateFileHashes() {
    myUpdater.scheduleUpdateFileHashes(updateCallback());
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
    return ReadAction.compute(() -> !checkHashesOfChangedFiles());
  }

  public boolean isGradleFile(@NotNull PsiFile psiFile) {
    return FileRelevanceKt.isGradleFile(psiFile);
  }

  public boolean isExternalBuildFile(@NotNull PsiFile psiFile) {
    synchronized (myLock) {
      return myExternalBuildFiles.contains(psiFile.getVirtualFile());
    }
  }

  public boolean isExternalBuildFile(@NotNull VirtualFile virtualFile) {
    synchronized (myLock) {
      return myExternalBuildFiles.contains(virtualFile);
    }
  }

  public void resetChangedFilesState() {
    scheduleUpdateFileHashes();
    removeChangedFiles();
  }
  public void maybeProcessSyncStarted() {
    if (!myProject.isInitialized()) {
      return;
    }
    resetChangedFilesState();
  }
}
