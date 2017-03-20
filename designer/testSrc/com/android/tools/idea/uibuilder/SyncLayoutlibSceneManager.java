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
package com.android.tools.idea.uibuilder;

import com.android.tools.idea.uibuilder.model.NlModel;
import com.android.tools.idea.uibuilder.scene.LayoutlibSceneManager;
import com.android.tools.idea.uibuilder.surface.DesignSurface;
import com.android.tools.idea.uibuilder.surface.SceneView;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandAdapter;
import com.intellij.openapi.command.CommandEvent;
import com.intellij.openapi.command.CommandProcessor;
import org.jetbrains.annotations.NotNull;

/**
 * {@link LayoutlibSceneManager} used for tests that performs all operations synchronously.
 */
public class SyncLayoutlibSceneManager extends LayoutlibSceneManager {
  public SyncLayoutlibSceneManager(@NotNull SyncNlModel model) {
    super(model, model.getSurface());
  }

  @Override
  public void requestRender() {
    runAfterCommandIfNecessary(() -> getModel().render());
  }

  @Override
  protected void requestModelUpdate() {
    runAfterCommandIfNecessary(() -> getModel().updateModel());
  }

  private static void runAfterCommandIfNecessary(Runnable runnable) {
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


}
