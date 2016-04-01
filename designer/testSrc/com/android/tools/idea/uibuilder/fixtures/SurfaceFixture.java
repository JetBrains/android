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

import org.jetbrains.annotations.NotNull;
import com.android.tools.idea.uibuilder.LayoutTestUtilities;
import com.android.tools.idea.uibuilder.model.NlModel;
import com.android.tools.idea.uibuilder.surface.DesignSurface;

public class SurfaceFixture {
  private DesignSurface mySurface;

  public DesignSurface getSurface() {
    if (mySurface == null) {
      mySurface = LayoutTestUtilities.createSurface();
    }
    return mySurface;
  }

  public ScreenFixture screen(@NotNull NlModel model) {
    return new ScreenFixture(this, model);
  }
}
