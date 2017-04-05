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
package com.android.tools.idea.naveditor.scene.layout;

import com.android.tools.idea.uibuilder.scene.SceneComponent;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayDeque;
import java.util.Deque;

import static com.android.tools.idea.naveditor.scene.NavSceneManager.TAG_FRAGMENT;

/**
 * A dumb way of laying out screens in the navigation editor.
 *
 * TODO: implement a better way
 */
public class DummyAlgorithm implements NavSceneLayoutAlgorithm {
  @Override
  public void layout(@NotNull SceneComponent root) {
    Deque<SceneComponent> toBeProcessed = new ArrayDeque<>();
    toBeProcessed.add(root);
    int xOffset = 50;
    int yOffset = 50;
    while (!toBeProcessed.isEmpty()) {
      SceneComponent component = toBeProcessed.removeLast();
      toBeProcessed.addAll(component.getChildren());
      if (component.getNlComponent().getTagName().equals(TAG_FRAGMENT)) {
        component.setPosition(xOffset, yOffset);
        xOffset += 130;
        if (xOffset + 100 > root.getDrawWidth()) {
          yOffset += 130;
          xOffset = 50;
        }
      }
    }
  }
}
