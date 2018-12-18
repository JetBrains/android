/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.uibuilder.scene;

import static junit.framework.TestCase.fail;

import com.android.ide.common.rendering.api.ResourceNamespace;
import com.android.ide.common.rendering.api.ResourceReference;
import com.android.ide.common.rendering.api.ResourceValue;
import com.android.ide.common.rendering.api.StyleItemResourceValueImpl;
import com.android.tools.idea.common.SyncNlModel;
import com.android.tools.idea.common.model.NlComponent;
import com.android.tools.idea.rendering.RenderResult;
import com.android.tools.idea.rendering.RenderService;
import com.android.tools.idea.rendering.RenderSettings;
import com.android.tools.idea.uibuilder.api.ViewEditor;
import com.google.wireless.android.sdk.stats.LayoutEditorRenderResult;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandEvent;
import com.intellij.openapi.command.CommandListener;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.util.Ref;
import com.intellij.util.concurrency.EdtExecutorService;
import com.intellij.util.ui.UIUtil;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinTask;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * {@link LayoutlibSceneManager} used for tests that performs all operations synchronously.
 */
public class SyncLayoutlibSceneManager extends LayoutlibSceneManager {
  private final Map<Object, Map<ResourceReference, ResourceValue>> myDefaultProperties;
  private ViewEditor myCustomViewEditor;

  public SyncLayoutlibSceneManager(@NotNull SyncNlModel model) {
    super(model, model.getSurface(), RenderSettings.getProjectSettings(model.getProject()), EdtExecutorService.getInstance());
    myDefaultProperties = new HashMap<>();
  }

  @NotNull
  @Override
  protected CompletableFuture<RenderResult> render(@Nullable LayoutEditorRenderResult.Trigger trigger) {
    return runAfterCommandIfNecessary(() -> CompletableFuture.completedFuture(super.render(trigger).join()));
  }

  @NotNull
  @Override
  public CompletableFuture<Void> requestRender() {
    return runAfterCommandIfNecessary(() -> CompletableFuture.completedFuture(super.requestRender().join()));
  }

  @Override
  protected CompletableFuture<Void> updateModel() {
    return runAfterCommandIfNecessary(() -> CompletableFuture.completedFuture(super.updateModel().join()));
  }

  @Override
  protected void requestModelUpdate() {
    updateModel();
  }

  @Override
  protected void notifyListenersModelLayoutComplete(boolean animate) {
    ForkJoinTask<?> task = ForkJoinPool.commonPool().submit(() -> super.notifyListenersModelLayoutComplete(animate));
    // Since we are the sync version of LayoutlibSceneManager, we add a wait in the UI thread for the asynchronous listeners
    // to finish.
    ApplicationManager.getApplication().invokeLater(() -> task.quietlyJoin());
  }

  @Override
  protected void notifyListenersModelUpdateComplete() {
    ForkJoinTask<?> task = ForkJoinPool.commonPool().submit(() -> super.notifyListenersModelUpdateComplete());
    // Since we are the sync version of LayoutlibSceneManager, we add a wait in the UI thread for the asynchronous listeners
    // to finish.
    ApplicationManager.getApplication().invokeLater(() -> task.quietlyJoin());
  }

  private static <T> T runAfterCommandIfNecessary(Callable<T> runnable) {
    Ref<T> result = new Ref<>();

    if (ApplicationManager.getApplication().isWriteAccessAllowed()) {
      CommandProcessor.getInstance().addCommandListener(new CommandListener() {
        @Override
        public void commandFinished(CommandEvent event) {
          try {
            result.set(runnable.call());
          }
          catch (Exception e) {
            fail(e.getMessage());
          }
          CommandProcessor.getInstance().removeCommandListener(this);
          // Dispatch any events created by the runnable execution
          UIUtil.dispatchAllInvocationEvents();
        }
      });

      return result.get();
    }

    try {
      T runnableResult = runnable.call();
      // Dispatch any events created by the runnable execution
      UIUtil.dispatchAllInvocationEvents();
      return runnableResult;
    }
    catch (Exception e) {
      fail(e.getMessage());
    }
    throw new IllegalStateException("Not reachable");
  }


  @Override
  @NotNull
  protected RenderService.RenderTaskBuilder setupRenderTaskBuilder(@NotNull RenderService.RenderTaskBuilder taskBuilder) {
    return super.setupRenderTaskBuilder(taskBuilder).disableSecurityManager();
  }

  @Override
  @NotNull
  public Map<Object, Map<ResourceReference, ResourceValue>> getDefaultProperties() {
    return myDefaultProperties;
  }

  public void putDefaultPropertyValue(@NotNull NlComponent component,
                                      @NotNull ResourceNamespace namespace,
                                      @NotNull String attributeName,
                                      @NotNull String value) {
    Map<ResourceReference, ResourceValue> map = myDefaultProperties.get(component.getSnapshot());
    if (map == null) {
      map = new HashMap<>();
      myDefaultProperties.put(component.getSnapshot(), map);
    }
    ResourceReference reference = ResourceReference.attr(namespace, attributeName);
    ResourceValue resourceValue = new StyleItemResourceValueImpl(namespace, attributeName, value, null);
    map.put(reference, resourceValue);
  }

  public void setCustomViewEditor(@NotNull ViewEditor editor) {
    myCustomViewEditor = editor;
  }

  @NotNull
  @Override
  public ViewEditor getViewEditor() {
    return myCustomViewEditor != null ? myCustomViewEditor : super.getViewEditor();
  }

  public void fireRenderCompleted() {
    fireRenderListeners();
  }
}
