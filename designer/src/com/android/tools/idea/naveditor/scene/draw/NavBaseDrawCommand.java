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
package com.android.tools.idea.naveditor.scene.draw;

import com.android.tools.idea.common.scene.SceneContext;
import com.android.tools.idea.common.scene.draw.DrawCommand;
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableMap;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.util.Arrays;
import java.util.Map;

/**
 * {@link NavBaseDrawCommand} Base class for navigation related draw commands.
 */
public abstract class NavBaseDrawCommand implements DrawCommand {
  private final static Map<RenderingHints.Key, Object> HQ_RENDERING_HITS = ImmutableMap.of(
    RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON,
    RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY,
    RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR
  );

  protected static final int DRAW_ACTION = COMPONENT_LEVEL + 1;
  protected static final int DRAW_SCREEN_LABEL = DRAW_ACTION + 1;
  protected static final int DRAW_ICON = DRAW_SCREEN_LABEL + 1;
  protected static final int DRAW_NAV_SCREEN = DRAW_ICON + 1;
  protected static final int DRAW_ACTION_HANDLE = DRAW_NAV_SCREEN + 1;
  protected static final int DRAW_ACTION_HANDLE_DRAG = DRAW_ACTION_HANDLE + 1;

  @Override
  @NotNull
  public String serialize() {
    return this.getClass().getSimpleName() + "," + Joiner.on(',').join(this.getProperties());
  }

  @NotNull
  protected abstract Object[] getProperties();

  @Override
  public final void paint(@NotNull Graphics2D g, @NotNull SceneContext sceneContext) {
    Graphics2D g2 = (Graphics2D)g.create();
    onPaint(g2, sceneContext);
    g2.dispose();
  }

  protected abstract void onPaint(@NotNull Graphics2D g, @NotNull SceneContext sceneContext);

  @NotNull
  protected static String rectToString(@NotNull Rectangle r) {
    return r.x + "x" + r.y + "x" + r.width + "x" + r.height;
  }

  @NotNull
  protected static Rectangle stringToRect(@NotNull String s) {
    String[] sp = s.split("x");
    int c = -1;
    Rectangle r = new Rectangle();
    r.x = Integer.parseInt(sp[++c]);
    r.y = Integer.parseInt(sp[++c]);
    r.width = Integer.parseInt(sp[++c]);
    r.height = Integer.parseInt(sp[++c]);
    return r;
  }

  @NotNull
  protected static String[] parse(@NotNull String s, int expected) {
    String[] sp = s.split(",");
    if (sp.length != expected + 1) {
      throw new IllegalArgumentException();
    }

    return Arrays.copyOfRange(sp, 1, sp.length);
  }

  protected static void setRenderingHints(@NotNull Graphics2D g) {
    g.setRenderingHints(HQ_RENDERING_HITS);
  }
}
