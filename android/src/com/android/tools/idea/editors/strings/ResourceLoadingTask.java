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
package com.android.tools.idea.editors.strings;

import com.android.tools.idea.editors.strings.model.StringResourceRepository;
import com.android.tools.idea.editors.strings.table.StringResourceTableModel;
import com.android.tools.res.LocalResourceRepository;
import com.android.tools.idea.res.StudioResourceRepositoryManager;
import com.google.common.annotations.VisibleForTesting;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.concurrency.EdtExecutorService;
import java.util.concurrent.CountDownLatch;
import java.util.function.Supplier;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class ResourceLoadingTask extends Task.Backgroundable {
  @NotNull
  private final StringResourceViewPanel myPanel;

  @NotNull
  private final Supplier<? extends LocalResourceRepository<VirtualFile>> myGetModuleResources;

  @Nullable
  private StringResourceTableModel myStringResourceTableModel;

  ResourceLoadingTask(@NotNull StringResourceViewPanel panel) {
    this(panel, createSupplier(panel));
  }

  private static Supplier<LocalResourceRepository<VirtualFile>> createSupplier(@NotNull StringResourceViewPanel panel) {
    AndroidFacet facet = panel.getFacet();
    return () -> StudioResourceRepositoryManager.getModuleResources(facet);
  }

  @VisibleForTesting
  ResourceLoadingTask(@NotNull StringResourceViewPanel panel, @NotNull Supplier<? extends LocalResourceRepository<VirtualFile>> getModuleResources) {
    super(panel.getFacet().getModule().getProject(), "Loading String Resources...");

    myPanel = panel;
    myGetModuleResources = getModuleResources;
  }

  @Override
  public void run(@NotNull ProgressIndicator indicator) {
    if (Disposer.isDisposed(myPanel) || Disposer.isDisposed(myPanel.getFacet())) {
      throw new ProcessCanceledException();
    }
    indicator.setIndeterminate(true);
    LocalResourceRepository<VirtualFile> localResourceRepository;
    try {
      localResourceRepository = myGetModuleResources.get();
    }
    catch (Throwable e) {
      if (Disposer.isDisposed(myPanel) || Disposer.isDisposed(myPanel.getFacet())) {
        throw new ProcessCanceledException();
      }
      throw e;
    }
    StringResourceRepository repository = StringResourceRepository.create(localResourceRepository, getProject());
    // Creating the StringResourceRepository initiates changes to localResourceRepository that may still
    // be in-flight. Wait (as long as it takes) for them to finish before proceeding.
    CountDownLatch latch = new CountDownLatch(1);
    localResourceRepository.invokeAfterPendingUpdatesFinish(EdtExecutorService.getInstance(), latch::countDown);
    try {
      latch.await();
    }
    catch (InterruptedException e) {
      throw new ProcessCanceledException(e);
    }
    catch (Throwable e) {
      if (Disposer.isDisposed(myPanel) || Disposer.isDisposed(myPanel.getFacet())) {
        throw new ProcessCanceledException();
      }
      throw e;
    }

    if (Disposer.isDisposed(myPanel) || Disposer.isDisposed(myPanel.getFacet())) {
      throw new ProcessCanceledException();
    }

    myStringResourceTableModel = new StringResourceTableModel(repository, myPanel.getFacet().getModule().getProject());
  }

  @Override
  public void onSuccess() {
    assert myStringResourceTableModel != null;
    myPanel.getTable().setModel(myStringResourceTableModel);
    myPanel.addUpdateListenerToNewStringResourceModel();

    myPanel.stopLoading();
  }

  @Override
  public void onCancel() {
    myPanel.stopLoading();
  }

  @Override
  public void onThrowable(@NotNull Throwable error) {
    if (Disposer.isDisposed(myPanel) || Disposer.isDisposed(myPanel.getFacet()) || error instanceof ProcessCanceledException) {
      return;
    }
    super.onThrowable(error);
  }
}
