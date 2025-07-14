/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.assistant;

import com.android.tools.idea.assistant.datamodel.DefaultActionState;
import com.android.tools.idea.assistant.view.StatefulButton;
import java.awt.Color;
import javax.swing.Icon;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Defines an interface for ActionStates (example {@link DefaultActionState} used by Actions
 * within Assistant framework. Allows for control of subset of UIs on {@link StatefulButton}
 * and it's message.
 */
public interface AssistActionState {
  boolean isButtonVisible();

  boolean isButtonEnabled();

  boolean isMessageVisible();

  @Nullable
  Icon getIcon();

  @NotNull
  Color getForeground();
}
