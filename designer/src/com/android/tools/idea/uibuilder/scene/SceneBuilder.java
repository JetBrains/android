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
import com.android.tools.idea.uibuilder.surface.SceneView;
import com.intellij.openapi.application.ApplicationManager;
import org.jetbrains.annotations.NotNull;

/**
 * A facility for creating and updating {@link Scene}s based on {@link NlModel}s.
 */
abstract public class SceneBuilder {
  private final NlModel myModel;
  final private SceneView mySceneView;
  private Scene myScene;

  public SceneBuilder(NlModel model, SceneView view) {
    myModel = model;
    mySceneView = view;
  }

  @NotNull
  public Scene build() {
    assert myScene == null;
    myScene = new Scene(mySceneView);
    return myScene;
  }

  public void update() {
    assert ApplicationManager.getApplication().isDispatchThread();
    assert myScene != null;
  }

  abstract public TemporarySceneComponent createTemporaryComponent(NlComponent component);

  @NotNull
  protected SceneView getSceneView() {
    return mySceneView;
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

}
