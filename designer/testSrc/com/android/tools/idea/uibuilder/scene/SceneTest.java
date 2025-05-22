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
package com.android.tools.idea.uibuilder.scene;

import com.android.tools.idea.common.scene.Scene;
import com.android.tools.idea.common.scene.SceneManager;
import com.android.tools.idea.common.scene.SceneMouseInteraction;
import com.android.tools.idea.rendering.RenderTestUtil;
import com.android.tools.idea.common.SyncNlModel;
import com.android.tools.idea.common.fixtures.ModelBuilder;
import com.android.tools.idea.uibuilder.ApiLayoutTestCase;
import com.android.tools.idea.uibuilder.fixtures.ScreenFixture;
import com.android.tools.idea.uibuilder.handlers.constraint.ConstraintLayoutHandler;
import com.intellij.openapi.util.Disposer;

/**
 * Base class for Scene tests
 */
public abstract class SceneTest extends ApiLayoutTestCase {

  protected SyncNlModel myModel;
  protected Scene myScene;
  protected SceneManager mySceneManager;
  protected ScreenFixture myScreen;
  protected SceneMouseInteraction myInteraction;

  public SceneTest() {
    super(true);
  }

  public SceneTest(boolean provideManifest) {
    super(provideManifest);
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    RenderTestUtil.beforeRenderTestCase();
    myModel = createModel().build();
    myScreen = new ScreenFixture(myModel);
    myScreen.withScale(1);
    ConstraintLayoutHandler.forceDefaultVisualProperties();
    buildScene();
  }

  protected void buildScene() {
    mySceneManager = myModel.getSurface().getSceneManager(myModel);
    myScene = myModel.getSurface().getScene();
    myScene.setAnimated(false);
    mySceneManager.update();
    myInteraction = new SceneMouseInteraction(myScene);
  }

  @Override
  protected void tearDown() throws Exception {
    try {
      Disposer.dispose(myModel);
    } catch (Throwable t) {
      t.printStackTrace();
    }
    try {
      RenderTestUtil.afterRenderTestCase();
      myModel = null;
      myScene = null;
      mySceneManager = null;
      myScreen = null;
      myInteraction = null;
    } finally {
      super.tearDown();
    }
  }

  abstract public ModelBuilder createModel();
}
