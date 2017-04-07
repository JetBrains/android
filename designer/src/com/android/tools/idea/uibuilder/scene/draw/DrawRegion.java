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
package com.android.tools.idea.uibuilder.scene.draw;

import com.android.tools.idea.uibuilder.scene.SceneContext;
import org.jetbrains.annotations.NotNull;

import java.awt.*;

/**
 * base class for regions based on rectangles
 */
public class DrawRegion extends Rectangle implements DrawCommand {

  @Override
  public String serialize() {
    return this.getClass().getSimpleName()+"," + x + "," + y + "," + width + "," + height;
  }

  public DrawRegion() {
  }

  @Override
  public int getLevel() {
    return TARGET_LEVEL;
  }

  public DrawRegion(String s) {
    String[] sp = s.split(",");
    parse(sp, 0);
  }

  protected int parse(String[] sp, int c) {
    x = Integer.parseInt(sp[c++]);
    y = Integer.parseInt(sp[c++]);
    width = Integer.parseInt(sp[c++]);
    height = Integer.parseInt(sp[c++]);
    return c;
  }

  public DrawRegion(int x, int y, int width, int height) {
    super(x, y, width, height);
  }

  @Override
  public void paint(Graphics2D g, SceneContext sceneContext) {
    g.drawRect(x, y, width, height);
  }

  @Override
  public int compareTo(@NotNull Object o) {
    return Integer.compare(getLevel(), ((DrawCommand)o).getLevel());
  }

}

