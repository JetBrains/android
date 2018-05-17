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

import com.android.ide.common.rendering.api.ResourceNamespace;
import com.android.ide.common.rendering.api.ResourceReference;
import com.android.ide.common.rendering.api.ResourceValue;
import com.android.ide.common.rendering.api.StyleItemResourceValueImpl;
import com.android.tools.idea.common.model.NlComponent;
import com.android.tools.idea.common.model.NlModel;
import com.android.tools.idea.common.surface.DesignSurface;
import com.android.tools.idea.common.surface.LayoutScannerConfiguration;
import com.android.tools.idea.model.MergedManifestManager;
import com.android.tools.idea.rendering.RenderResult;
import com.android.tools.idea.rendering.RenderService;
import com.google.wireless.android.sdk.stats.LayoutEditorRenderResult;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.util.concurrency.EdtExecutorService;
import com.intellij.util.ui.UIUtil;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * {@link LayoutlibSceneManager} used for tests that performs all operations synchronously.
 */
public class SyncLayoutlibSceneManager extends LayoutlibSceneManager {
  /**
   * Number of seconds to wait for the render to complete in any of the render calls.
   */
  private final static int RENDER_TIMEOUT_SECS = 60;

  private final Map<Object, Map<ResourceReference, ResourceValue>> myDefaultProperties;
  private boolean myIgnoreRenderRequests;

  public SyncLayoutlibSceneManager(@NotNull DesignSurface<LayoutlibSceneManager> surface, @NotNull NlModel model) {
    super(
      model,
      surface,
      EdtExecutorService.getInstance(),
      d -> Runnable::run,
      new LayoutlibSceneManagerHierarchyProvider(),
      null,
      LayoutScannerConfiguration.getDISABLED(),
      RealTimeSessionClock::new);
    myDefaultProperties = new HashMap<>();
  }

  public boolean getIgnoreRenderRequests() {
    return myIgnoreRenderRequests;
  }

  public void setIgnoreRenderRequests(boolean ignoreRenderRequests) {
    myIgnoreRenderRequests = ignoreRenderRequests;
  }

  private <T> CompletableFuture<T> waitForFutureWithoutBlockingUiThread(CompletableFuture<T> future) {
    if (ApplicationManager.getApplication().isDispatchThread()) {
      // If this is happening in the UI thread, keep dispatching the events in the UI thread while we are waiting
      PlatformTestUtil.waitForFuture(future, TimeUnit.SECONDS.toMillis(RENDER_TIMEOUT_SECS));
    }

    CompletableFuture<T> result = CompletableFuture.completedFuture(future.orTimeout(RENDER_TIMEOUT_SECS, TimeUnit.SECONDS).join());

    // After running render calls, there might be pending actions to run on the UI thread, dispatch those to ensure that after this call, everything
    // is done.
    ApplicationManager.getApplication().invokeAndWait(PlatformTestUtil::dispatchAllEventsInIdeEventQueue);
    return result;
  }

  @NotNull
  @Override
  protected CompletableFuture<RenderResult> renderAsync(@Nullable LayoutEditorRenderResult.Trigger trigger) {
    if (myIgnoreRenderRequests) {
      return CompletableFuture.completedFuture(null);
    }
    return waitForFutureWithoutBlockingUiThread(super.renderAsync(trigger));
  }

  @NotNull
  @Override
  final public CompletableFuture<Void> requestRenderAsync() {
    if (myIgnoreRenderRequests) {
      return CompletableFuture.completedFuture(null);
    }
    CompletableFuture<Void> result = waitForFutureWithoutBlockingUiThread(super.requestRenderAsync());
    return result;
  }

  @Override
  protected @NotNull CompletableFuture<Void> requestRenderAsync(LayoutEditorRenderResult.Trigger trigger) {
    if (myIgnoreRenderRequests) {
      return CompletableFuture.completedFuture(null);
    }
    return waitForFutureWithoutBlockingUiThread(super.requestRenderAsync(trigger));
  }

  @NotNull
  @Override
  public CompletableFuture<Void> updateModelAsync() {
    return waitForFutureWithoutBlockingUiThread(super.updateModelAsync());
  }

  @Override
  protected void requestModelUpdate() {
    updateModelAsync();
  }

  @Override
  @NotNull
  protected RenderService.RenderTaskBuilder setupRenderTaskBuilder(@NotNull RenderService.RenderTaskBuilder taskBuilder) {
    return super.setupRenderTaskBuilder(taskBuilder)
      .disableSecurityManager()
      // For testing, we do not need to wait for the full merged manifest
      .setMergedManifestProvider(module -> MergedManifestManager.getMergedManifestSupplier(module).getNow());
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

  public void fireRenderCompleted() {
    fireOnRenderComplete();
  }
}
