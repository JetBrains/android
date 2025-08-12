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
package com.android.tools.adtui.instructions;

import com.intellij.util.ui.JBUI;
import java.awt.Point;
import org.jetbrains.annotations.NotNull;

/**
 * Instruction to create a new row; this has the effect of moving the cursor back to the left.
 */
public final class NewRowInstruction extends RenderInstruction {
  public static final int DEFAULT_ROW_MARGIN = JBUI.scale(5);

  private final int myVerticalMargin;

  public NewRowInstruction(int verticalMargin) {
    myVerticalMargin = verticalMargin;
  }

  @Override
  public void moveCursor(@NotNull InstructionsRenderer renderer, @NotNull Point cursor) {
    cursor.y += renderer.getRowHeight() + myVerticalMargin;
    cursor.x = renderer.getStartX(cursor.y);
  }
}
