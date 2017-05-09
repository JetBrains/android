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
package com.android.tools.idea.tests.gui.framework.fixture.designer.naveditor;

import com.android.tools.idea.naveditor.surface.NavDesignSurface;
import com.android.tools.idea.tests.gui.framework.fixture.designer.DesignSurfaceFixture;
import com.android.tools.idea.tests.gui.framework.fixture.designer.NlComponentFixture;
import com.android.tools.idea.uibuilder.model.NlModel;
import com.android.tools.idea.uibuilder.surface.SceneView;
import org.fest.swing.core.Robot;
import org.fest.swing.timing.Wait;
import org.jetbrains.annotations.NotNull;

public class NavDesignSurfaceFixture extends DesignSurfaceFixture<NavDesignSurfaceFixture, NavDesignSurface> {
  public NavDesignSurfaceFixture(@NotNull Robot robot,
                                 @NotNull NavDesignSurface designSurface) {
    super(NavDesignSurfaceFixture.class, robot, designSurface);
  }

  /**
   * Searches for the given destination in the nav graph.
   *
   * @param id the destination id
   */
  @NotNull
  public NlComponentFixture findDestination(@NotNull final String id) {
    waitForRenderToFinish(Wait.seconds(5));

    SceneView view = target().getCurrentSceneView();
    assert view != null;

    final NlModel model = view.getModel();

    return createComponentFixture(model.find(id));
  }
}
