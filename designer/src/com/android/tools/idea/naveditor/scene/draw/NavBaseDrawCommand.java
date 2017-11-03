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

import com.android.tools.idea.common.scene.draw.DrawCommand;
import com.google.common.base.Joiner;
import org.jetbrains.annotations.NotNull;

/**
 * {@link NavBaseDrawCommand} Base class for navigation related draw commands.
 */
public abstract class NavBaseDrawCommand implements DrawCommand {
  protected static final int DRAW_ACTION = COMPONENT_LEVEL + 1;
  protected static final int DRAW_SCREEN_LABEL = DRAW_ACTION + 1;
  protected static final int DRAW_NAV_SCREEN = DRAW_SCREEN_LABEL + 1;
  protected static final int DRAW_ACTION_HANDLE = DRAW_NAV_SCREEN + 1;
  protected static final int DRAW_ACTION_HANDLE_DRAG = DRAW_ACTION_HANDLE + 1;

  @Override
  @NotNull
  public String serialize() {
    return Joiner.on(',').join(this.getClass().getSimpleName(), this.getLevel(), this.getProperties());
  }

  @NotNull
  protected abstract Object[] getProperties();
}
