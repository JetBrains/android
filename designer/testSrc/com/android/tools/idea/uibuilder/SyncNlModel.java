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
package com.android.tools.idea.uibuilder;

import com.android.annotations.VisibleForTesting;
import com.android.tools.idea.configurations.Configuration;
import com.android.tools.idea.rendering.RenderTask;
import com.android.tools.idea.uibuilder.model.NlModel;
import com.android.tools.idea.uibuilder.surface.NlDesignSurface;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandAdapter;
import com.intellij.openapi.command.CommandEvent;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.psi.xml.XmlFile;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * {@link NlModel} that runs all the operations synchronously for testing
 */
public class SyncNlModel extends NlModel {

  Configuration myConfiguration; // for testing purposes

  @NotNull
  public static NlModel create(@NotNull NlDesignSurface surface,
                               @Nullable Disposable parent,
                               @NotNull AndroidFacet facet,
                               @NotNull XmlFile file) {
    return new SyncNlModel(surface, parent, facet, file);
  }

  private SyncNlModel(@NotNull NlDesignSurface surface,
                     @Nullable Disposable parent,
                     @NotNull AndroidFacet facet, @NotNull XmlFile file) {
    super(surface, parent, facet, file);
  }

  @Override
  protected void setupRenderTask(@Nullable RenderTask task) {
    super.setupRenderTask(task);

    if (task != null) {
      task.disableSecurityManager();
    }
  }

  @Override
  public void requestRender() {
    runAfterCommandIfNecessary(this::render);
  }

  @Override
  protected void requestModelUpdate() {
    runAfterCommandIfNecessary(this::updateModel);
  }

  private void runAfterCommandIfNecessary(Runnable runnable) {
    if (ApplicationManager.getApplication().isWriteAccessAllowed()) {
      CommandProcessor.getInstance().addCommandListener(new CommandAdapter() {
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

  @VisibleForTesting
  public void setConfiguration(Configuration configuration) {
    myConfiguration =  configuration;
  }

  @Override
  public Configuration getConfiguration() {
    if (myConfiguration != null) {
      return myConfiguration;
    }
    return super.getConfiguration();
  }
}
