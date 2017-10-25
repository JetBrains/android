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
package com.android.tools.idea.uibuilder.handlers.transition;

import com.android.tools.idea.uibuilder.api.CustomPanel;
import com.android.tools.idea.uibuilder.handlers.constraint.ConstraintLayoutHandler;
import com.google.common.collect.ImmutableList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.List;

import static com.android.SdkConstants.ATTR_TRANSITION_POSITION;

public class TransitionLayoutHandler extends ConstraintLayoutHandler {

  class MyCustomPanel extends CustomPanel {
    public MyCustomPanel() {
      add(new JLabel("Hello, World!"));
    }
  }

  class MyCustomPanel2 extends CustomPanel {
    public MyCustomPanel2() {
      add(new JLabel("Child Hello, World!"));
    }
  }

  @Override
  @Nullable
  public CustomPanel getCustomPanel() {
    return new MyCustomPanel();
  }

  @Override
  @Nullable
  public CustomPanel getLayoutCustomPanel() {
    return new MyCustomPanel2();
  }

  @Override
  @NotNull
  public List<String> getInspectorProperties() {
    return ImmutableList.of(ATTR_TRANSITION_POSITION);
  }

}
