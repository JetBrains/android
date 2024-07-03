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
package com.android.tools.idea.uibuilder.handlers.motion.editor.createDialogs;

import com.android.tools.idea.uibuilder.handlers.motion.editor.adapters.MotionSceneAttrs;

/**
 * This is the dialog that pops up when you create a KeyTimeCycle
 */
public class CreateKeyTimeCycle extends CreateKeyCycle {
  {
    KEY_TAG = MotionSceneAttrs.Tags.KEY_TIME_CYCLE;
  }

  public CreateKeyTimeCycle() {
    super("CREATE KEY TIME CYCLE");
  }

  @Override
  public String getName() {
    return MotionSceneAttrs.Tags.KEY_TIME_CYCLE;
  }

}
