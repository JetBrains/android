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
import com.android.tools.idea.uibuilder.LayoutTestCase;
import com.android.tools.idea.common.SyncNlModel;
import com.android.tools.idea.common.fixtures.ModelBuilder;
import com.android.tools.idea.uibuilder.fixtures.ScreenFixture;
import com.android.tools.idea.uibuilder.handlers.constraint.ConstraintLayoutHandler;
import com.intellij.openapi.util.Disposer;

import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.util.Arrays;

/**
 * Base class for Scene tests
 */
public abstract class SceneTest extends LayoutTestCase {

  protected SyncNlModel myModel;
  protected Scene myScene;
  protected SceneManager mySceneManager;
  protected ScreenFixture myScreen;
  protected SceneMouseInteraction myInteraction;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myModel = createModel().build();
    myScreen = new ScreenFixture(myModel);
    myScreen.withScale(1);
    buildScene();
    ConstraintLayoutHandler.forceDefaultVisualProperties();
  }

  protected void buildScene() {
    mySceneManager = myModel.getSurface().getSceneManager();
    myScene = myModel.getSurface().getScene();
    myScene.setShowAllConstraints(true);
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
      RenderTestUtil.waitForRenderTaskDisposeToFinish();
      myModel = null;
      myScene = null;
      mySceneManager = null;
      myScreen = null;
      myInteraction = null;
    } finally {
      super.tearDown();
    }
  }

  static String toAsciiArt(BufferedImage img, int lineWidth) {
    int iw = img.getWidth();
    int ih = img.getHeight();
    int lines = (lineWidth * ih) / iw;
    int[] buff = ((DataBufferInt) (img.getRaster().getDataBuffer())).getData();

    // simple way to get background color assuming most of the image is empty
    int []strip = new int[lines*lineWidth];
    for (int i = 0; i < strip.length; i++) {
      int iy = ((i/lineWidth) * ih) / lines;
      int ix = ((i%lineWidth) * iw) / lineWidth;
      strip[i] = buff[ix+iy*iw];
    }

    Arrays.sort(strip);
    int baseColor = strip[strip.length/2];
    String str = "";
    for (int y = 0; y < lines; y++) {
      char[] line = new char[lineWidth];
      int iy = (y * ih) / lines;
      int iny = ((y + 1) * ih) / lines;
      for (int x = 0; x < lineWidth; x++) {
        int ix = (x * iw) / lineWidth;
        int inx = ((x + 1) * iw) / lineWidth;
        boolean background = true;
        for (int dy = iy; dy < iny; dy++) {
          for (int dx = ix; dx < inx; dx++) {
            int p = dx + dy * iw;
            if (buff[p] != baseColor) {
              background = false;
              break;
            }
            if (!background) {
              break;
            }
          }
          line[x] = background ? ' ' : '#';
        }
      }
      str += new String(line) + "\n";
    }
    return str;
  }
  abstract public ModelBuilder createModel();
}
