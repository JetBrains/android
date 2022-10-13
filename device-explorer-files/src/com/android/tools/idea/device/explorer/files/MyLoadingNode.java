/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.device.explorer.files;

import com.android.tools.idea.file.explorer.toolwindow.fs.DeviceFileEntry;
import com.intellij.ui.LoadingNode;
import org.jetbrains.annotations.NotNull;

/**
 * A {@link LoadingNode} that supports download ticks for animations
 */
public class MyLoadingNode extends LoadingNode {
  @NotNull private final DeviceFileEntry myParentEntry;
  private int myTick;

  public MyLoadingNode(@NotNull DeviceFileEntry entry) {
    myParentEntry = entry;
  }

  @Override
  public String toString() {
    return myParentEntry.getName() + " - loading";
  }

  @Override
  public boolean isLeaf() {
    return true;
  }

  public int getTick() {
    return myTick;
  }

  public void incTick() {
    myTick++;
  }
}
