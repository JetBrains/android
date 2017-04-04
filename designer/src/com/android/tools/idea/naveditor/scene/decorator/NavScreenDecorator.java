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
package com.android.tools.idea.naveditor.scene.decorator;

import com.android.tools.idea.naveditor.scene.draw.DrawNavScreen;
import com.android.tools.idea.uibuilder.scene.SceneComponent;
import com.android.tools.idea.uibuilder.scene.SceneContext;
import com.android.tools.idea.uibuilder.scene.decorator.SceneDecorator;
import com.android.tools.idea.uibuilder.scene.draw.DisplayList;
import org.jetbrains.annotations.NotNull;

/**
 * {@link SceneDecorator} responsible for creating draw commands for one screen/fragment/destination in the navigation editor.
 */
public class NavScreenDecorator extends SceneDecorator {

  @Override
  protected void addContent(@NotNull DisplayList list, long time, @NotNull SceneContext sceneContext, @NotNull SceneComponent component) {
    super.addContent(list, time, sceneContext, component);
    list.add(new DrawNavScreen(sceneContext.getSwingX(component.getDrawX()),
                               sceneContext.getSwingY(component.getDrawY()),
                               sceneContext.getSwingDimension(component.getDrawWidth()),
                               sceneContext.getSwingDimension(component.getDrawHeight()),
                               (String)component.getId(), component.isSelected()));
  }
}
