/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.tools.idea.uibuilder.fixtures;

import com.android.tools.idea.uibuilder.LayoutTestUtilities;
import com.android.tools.idea.uibuilder.model.NlModel;
import com.android.tools.idea.uibuilder.surface.DesignSurface;
import org.jetbrains.annotations.NotNull;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.List;

public class SurfaceFixture {
  private DesignSurface mySurface;
  private final List<ScreenFixture> myScreenFixtures = new ArrayList<>();

  public DesignSurface getSurface() {
    if (mySurface == null) {
      mySurface = LayoutTestUtilities.createSurface();
    }
    return mySurface;
  }

  public void tearDown() {
    Mockito.reset(mySurface);
    for (ScreenFixture screenFixture : myScreenFixtures) {
      screenFixture.tearDown();
    }
    myScreenFixtures.clear();
  }

  public ScreenFixture screen(@NotNull NlModel model) {
    ScreenFixture screenFixture = new ScreenFixture(this, model);
    myScreenFixtures.add(screenFixture);
    return screenFixture;
  }
}
