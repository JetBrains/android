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

import com.android.tools.idea.common.model.NlModel;
import com.android.tools.idea.common.surface.SceneView;
import com.android.tools.idea.testing.IdeComponents;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.mock.MockApplication;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.testFramework.LightIdeaTestCase;
import com.intellij.testFramework.UsefulTestCase;
import org.jetbrains.android.AndroidTestCase;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mockito;

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
    NlModel model = mock(NlModel.class);
    SceneView primary = SceneMode.BOTH.createPrimarySceneView(surface, model);
    SceneView secondary = SceneMode.BOTH.createSecondarySceneView(surface, model);
    assertThat(primary, instanceOf(ScreenView.class));
    assertThat(secondary, instanceOf(BlueprintView.class));
  }
}