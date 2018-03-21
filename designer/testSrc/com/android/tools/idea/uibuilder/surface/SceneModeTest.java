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
package com.android.tools.idea.uibuilder.surface;

import com.android.tools.idea.common.scene.SceneManager;
import com.android.tools.idea.common.surface.DesignSurface;
import com.android.tools.idea.common.surface.SceneView;
import com.android.tools.idea.uibuilder.scene.LayoutlibSceneManager;
import org.jetbrains.android.AndroidTestCase;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.IsInstanceOf.instanceOf;
import static org.mockito.Mockito.mock;


public class SceneModeTest extends AndroidTestCase {

  @Override
  public void setUp() throws Exception {
    super.setUp();
  }

  public void testCreateSceneView() {
    NlDesignSurface surface = mock(NlDesignSurface.class);
    LayoutlibSceneManager manager = mock(LayoutlibSceneManager.class);
    SceneView primary = SceneMode.BOTH.createPrimarySceneView(surface, manager);
    SceneView secondary = SceneMode.BOTH.createSecondarySceneView(surface, manager);
    assertThat(primary, instanceOf(ScreenView.class));
    assertThat(secondary, instanceOf(BlueprintView.class));
  }
}