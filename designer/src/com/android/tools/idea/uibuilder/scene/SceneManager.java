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
package com.android.tools.idea.uibuilder.scene;

import com.android.tools.idea.uibuilder.model.NlComponent;
import com.android.tools.idea.uibuilder.model.NlModel;
import com.android.tools.idea.uibuilder.surface.DesignSurface;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.Disposer;
import org.jetbrains.annotations.NotNull;

/**
 * A facility for creating and updating {@link Scene}s based on {@link NlModel}s.
 */
abstract public class SceneManager implements Disposable {
  private final NlModel myModel;
  final private DesignSurface myDesignSurface;
  private Scene myScene;

  public SceneManager(NlModel model, DesignSurface surface) {
    myModel = model;
    myDesignSurface = surface;
    Disposer.register(model, this);
  }

  @Override
  public void dispose() {
  }

  @NotNull
  public Scene build() {
    assert myScene == null;
    myScene = new Scene(myDesignSurface);
    return myScene;
  }

  public void update() {
    assert ApplicationManager.getApplication().isDispatchThread();
    assert myScene != null;
  }

  /**
   * Creates a {@link TemporarySceneComponent} in our Scene meant to be used temporarily for Drag and Drop
   */
  @NotNull
  abstract public TemporarySceneComponent createTemporaryComponent(@NotNull NlComponent component);

  @NotNull
  protected DesignSurface getDesignSurface() {
    return myDesignSurface;
  }

  @NotNull
  protected NlModel getModel() {
    return myModel;
  }

  @NotNull
  protected Scene getScene() {
    assert myScene != null;
    return myScene;
  }

  public abstract void requestRender();

  public abstract void layout(boolean animate);
}
