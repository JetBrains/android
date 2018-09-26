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
import com.android.tools.idea.common.SyncNlModel;
import com.android.tools.idea.common.model.NlComponent;
import com.android.tools.idea.rendering.RenderService;
import com.android.tools.idea.rendering.RenderSettings;
import com.android.tools.idea.uibuilder.api.ViewEditor;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandEvent;
import com.intellij.openapi.command.CommandListener;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.util.concurrency.EdtExecutorService;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import org.jetbrains.annotations.NotNull;

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

  @Override
  public CompletableFuture<Void> requestRender() {
    CompletableFuture<Void> result = new CompletableFuture<>();
    runAfterCommandIfNecessary(() -> {
      render(getTriggerFromChangeType(getModel().getLastChangeType()));
      result.complete(null);
    });
    return result;
  }

  @Override
  void doRequestLayoutAndRender(boolean animate) {
    runAfterCommandIfNecessary(() -> render(getTriggerFromChangeType(getModel().getLastChangeType())));
  }

  @Override
  protected void requestModelUpdate() {
    runAfterCommandIfNecessary(this::updateModel);
  }

  private static void runAfterCommandIfNecessary(Runnable runnable) {
    if (ApplicationManager.getApplication().isWriteAccessAllowed()) {
      CommandProcessor.getInstance().addCommandListener(new CommandListener() {
        @Override
        public void commandFinished(CommandEvent event) {
          runnable.run();
          CommandProcessor.getInstance().removeCommandListener(this);
        }
      });
    }
    else {
      runnable.run();
    }
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
}
