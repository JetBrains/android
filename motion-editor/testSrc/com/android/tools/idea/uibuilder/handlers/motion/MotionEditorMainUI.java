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
package com.android.tools.idea.uibuilder.handlers.motion;

import com.android.tools.idea.uibuilder.handlers.motion.adapters.BaseMotionEditorTest;
import com.android.tools.idea.uibuilder.handlers.motion.editor.adapters.MTag;
import com.android.tools.idea.uibuilder.handlers.motion.editor.ui.MeModel;
import com.android.tools.idea.uibuilder.handlers.motion.editor.ui.MotionEditor;

public class MotionEditorMainUI extends BaseMotionEditorTest {
  private MTag mLayout = getLayout();
  private MTag mScene = getScene();

  public void testMainUICreation() {
    MotionEditor motionSceneUi = new MotionEditor();
    MeModel model = new MeModel(mScene, mLayout, "layout", "scene");
    motionSceneUi.setMTag(mScene, mLayout, "layout", "scene", null);
    motionSceneUi.setMTag(model);
  }
}
