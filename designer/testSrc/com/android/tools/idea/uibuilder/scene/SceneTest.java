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

import com.android.tools.idea.uibuilder.LayoutTestCase;
import com.android.tools.idea.uibuilder.fixtures.ModelBuilder;
import com.android.tools.idea.uibuilder.fixtures.ScreenFixture;
import com.android.tools.idea.uibuilder.model.NlModel;
import com.intellij.openapi.util.Disposer;

/**
 * Base class for Scene tests
 */
public abstract class SceneTest extends LayoutTestCase {

  NlModel myModel;
  Scene myScene;
  ScreenFixture myScreen;
  SceneMouseInteraction myInteraction;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myModel = createModel().build();
    myScreen = surface().screen(myModel);
    myScreen.withScale(1);
    myScene = Scene.createScene(myModel, myScreen.getScreen());
    myScene.setDpiFactor(1);
    myScene.setAnimate(false);
    myScene.updateFrom(myModel);
    myInteraction = new SceneMouseInteraction(myScene);
  }

  @Override
  protected void tearDown() throws Exception {
    try {
      Disposer.dispose(myModel);
    } finally {
      super.tearDown();
    }
  }

  abstract public ModelBuilder createModel();
}
