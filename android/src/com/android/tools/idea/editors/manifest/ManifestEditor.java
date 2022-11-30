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
package com.android.tools.idea.editors.manifest;

import static com.android.tools.idea.projectsystem.ProjectSystemSyncUtil.PROJECT_SYSTEM_SYNC_TOPIC;

import com.android.SdkConstants;
import com.android.tools.idea.model.MergedManifestManager;
import com.android.tools.idea.model.MergedManifestSnapshot;
import com.android.utils.concurrency.AsyncSupplier;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorState;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiTreeChangeAdapter;
import com.intellij.psi.PsiTreeChangeEvent;
import com.intellij.psi.PsiTreeChangeListener;
import com.intellij.ui.EditorNotifications;
import com.intellij.util.concurrency.EdtExecutorService;
import java.awt.BorderLayout;
import java.beans.PropertyChangeListener;
import java.util.concurrent.ExecutionException;
import javax.swing.JComponent;
import javax.swing.JPanel;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ManifestEditor extends UserDataHolderBase implements FileEditor {
  private volatile boolean showingStaleManifest;
  private volatile boolean failedToComputeFreshManifest;

  private final AndroidFacet myFacet;
  private JPanel myLazyContainer;
  private ManifestPanel myManifestPanel;
  private final VirtualFile mySelectedFile;
  private boolean mySelected;
  private final PsiTreeChangeListener myPsiChangeListener = new PsiTreeChangeAdapter() {
    @Override
    public void propertyChanged(@NotNull PsiTreeChangeEvent event) {
      psiChange(event);
    }

    @Override
    public void childAdded(@NotNull PsiTreeChangeEvent event) {
      psiChange(event);
    }

    @Override
    public void childRemoved(@NotNull PsiTreeChangeEvent event) {
      psiChange(event);
    }

    @Override
    public void childReplaced(@NotNull PsiTreeChangeEvent event) {
      psiChange(event);
    }

    @Override
    public void childMoved(@NotNull PsiTreeChangeEvent event) {
      psiChange(event);
    }

    @Override
    public void childrenChanged(@NotNull PsiTreeChangeEvent event) {
      psiChange(event);
    }
  };
  ManifestEditor(@NotNull AndroidFacet facet, @NotNull VirtualFile manifestFile) {
    myFacet = facet;
    mySelectedFile = manifestFile;
    myLazyContainer = new JPanel(new BorderLayout());
  }

  private void psiChange(PsiTreeChangeEvent event) {
    PsiFile file = event.getFile();
    // Assume that all android manifest files have the same filename
    if (file != null && SdkConstants.FN_ANDROID_MANIFEST_XML.equals(file.getName())) {
      // This method may still hold a writelock, and we are doing a UI update.
      // Avoid deadlocks, break out of writelock psi/thread for UI update.
      ApplicationManager.getApplication().invokeLater(this::reload);
    }
  }

  private void reload() {
    if (!mySelected) {
      return;
    }
    AsyncSupplier<MergedManifestSnapshot> supplier = MergedManifestManager.getMergedManifestSupplier(myFacet.getModule());
    ListenableFuture<MergedManifestSnapshot> mergedManifest = supplier.get();
    if (mergedManifest.isDone()) {
      try {
        showFreshManifest(mergedManifest.get());
      }
      catch (ExecutionException|InterruptedException e) {
        Logger.getInstance(ManifestEditor.class)
          .warn("Error computing fresh merged manifest for module " + myFacet.getModule().getName(), e);
        showLoadingError();
      }
      return;
    }
    MergedManifestSnapshot cachedManifest = supplier.getNow();
    if (cachedManifest != null) {
      // If we've already computed the merged manifest before and the snapshot's just stale,
      // we can show that to the user while we compute a fresh one in the background.
      showStaleManifest(cachedManifest);
    }
    else {
      // Otherwise, the best we can do is to throw up a loading spinner.
      myManifestPanel.startLoading();
    }
    Futures.addCallback(mergedManifest, new FutureCallback<MergedManifestSnapshot>() {
      @Override
      public void onSuccess(MergedManifestSnapshot result) {
        showFreshManifest(result);
      }

      @Override
      public void onFailure(@Nullable Throwable t) {
        Logger.getInstance(ManifestEditor.class)
          .warn("Error computing fresh merged manifest for module " + myFacet.getModule().getName(), t);
        showLoadingError();
      }
    }, EdtExecutorService.getInstance());
  }

  private void showStaleManifest(MergedManifestSnapshot manifest) {
    showingStaleManifest = true;
    myManifestPanel.showManifest(manifest, mySelectedFile, false);
    EditorNotifications.getInstance(myFacet.getModule().getProject()).updateNotifications(mySelectedFile);
  }

  private void showFreshManifest(MergedManifestSnapshot manifest) {
    if (showingStaleManifest || failedToComputeFreshManifest) {
      showingStaleManifest = false;
      failedToComputeFreshManifest = false;
      EditorNotifications.getInstance(myFacet.getModule().getProject()).updateNotifications(mySelectedFile);
    }
    myManifestPanel.showManifest(manifest, mySelectedFile, true);
  }

  private void showLoadingError() {
    failedToComputeFreshManifest = true;
    if (showingStaleManifest) {
      EditorNotifications.getInstance(myFacet.getModule().getProject()).updateNotifications(mySelectedFile);
    }
    else {
      myManifestPanel.showLoadingError();
    }
  }

  public boolean isShowingStaleManifest() {
    return showingStaleManifest;
  }

  public boolean failedToComputeFreshManifest() {
    return failedToComputeFreshManifest;
  }

  @NotNull
  @Override
  public JComponent getComponent() {
    return myLazyContainer;
  }

  @NotNull
  @Override
  public VirtualFile getFile() {
    return mySelectedFile;
  }

  @Nullable
  @Override
  public JComponent getPreferredFocusedComponent() {
    return null;
  }

  @NotNull
  @Override
  public String getName() {
    return "Merged Manifest";
  }

  @Override
  public void setState(@NotNull FileEditorState state) {
  }

  @Override
  public boolean isModified() {
    return false;
  }

  @Override
  public boolean isValid() {
    return true;
  }

  @Override
  public void selectNotify() {
    mySelected = true;

    final Project project = myFacet.getModule().getProject();
    if (myManifestPanel == null) {
      myManifestPanel = new ManifestPanel(myFacet, this);
      myLazyContainer.add(myManifestPanel);
      // Parts of the merged manifest come from the project's build model, so we want to know
      // if that changes so we can get the latest values.
      project.getMessageBus().connect(this).subscribe(PROJECT_SYSTEM_SYNC_TOPIC, result -> {
        if (result.isSuccessful()) {
          reload();
        }
      });
    }

    PsiManager.getInstance(project).addPsiTreeChangeListener(myPsiChangeListener);
    reload();
  }

  @Override
  public void deselectNotify() {
    mySelected = false;
    final Project thisProject = myFacet.getModule().getProject();
    PsiManager.getInstance(thisProject).removePsiTreeChangeListener(myPsiChangeListener);
  }

  @Override
  public void addPropertyChangeListener(@NotNull PropertyChangeListener listener) {
  }

  @Override
  public void removePropertyChangeListener(@NotNull PropertyChangeListener listener) {
  }

  @Override
  public void dispose() {
  }
}
