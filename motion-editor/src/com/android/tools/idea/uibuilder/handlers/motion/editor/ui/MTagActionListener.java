/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.tools.idea.uibuilder.handlers.motion.editor.ui;

import com.android.tools.idea.uibuilder.handlers.motion.editor.adapters.MTag;

/**
 * A small interface to handle keyboard commands that can come from anywhere
 */
public interface MTagActionListener {
  int CONTROL_FLAG = 1;
  int SAVE_GIF = 1;
  void select(MTag selected, int flags);
  void delete(MTag[] tags, int flags);

  /**
   * This function enable save GIF process to be initiated in OverviewPanel
   *  (where the save GIF menu itme would be clicked)
   * To make it more general, it could have different behaviors based the input type
   * @param type type of action to be handled.
   */
  void performAction(int type);
}
