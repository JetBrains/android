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
package com.android.tools.idea.common.scene.draw;

import com.android.tools.idea.common.scene.SceneContext;
import org.jetbrains.annotations.NotNull;

import java.awt.*;

/**
 * Paint interface for draw commands
 * This interface also implies a constructor that takes a String
 * Which can expand the serialization of the of the command
 */
public interface DrawCommand extends Comparable {
  int CONNECTION_LEVEL = 10 ;
  int COMPONENT_LEVEL = 20;
  int COMPONENT_SELECTED_LEVEL = 30;
  int CONNECTION_SELECTED_LEVEL = 40 ;
  int TARGET_LEVEL = 50;
  int CONNECTION_HOVER_LEVEL = 60;
  int CONNECTION_DELETE_LEVEL = 70;
  int TARGET_OVER_LEVEL = 80;
  int TOP_LEVEL = 100;
  int CLIP_LEVEL =  0;
  int UNCLIP_LEVEL =  1000;
  int POST_CLIP_LEVEL =  1010;
  int getLevel(); // things are drawn 0 first
  void paint(Graphics2D g, SceneContext sceneContext);
  String serialize();

  @Override
  default int compareTo(@NotNull Object o) {
    return Integer.compare(getLevel(), ((DrawCommand)o).getLevel());
  }
}
