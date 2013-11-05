/*
 * Copyright (C) 2013 The Android Open Source Project
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

package com.android.tools.idea.ddms.actions;

import com.android.ddmlib.Client;
import com.android.tools.idea.ddms.DeviceContext;
import icons.AndroidIcons;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.annotations.NotNull;

public class GcAction extends AbstractClientAction {
  public GcAction(DeviceContext context) {
    super(context,
          AndroidBundle.message("android.ddms.actions.initiate.gc"),
          AndroidBundle.message("android.ddms.actions.initiate.gc.description"),
          AndroidIcons.Ddms.Gc); // Alternate: AllIcons.Actions.GC
  }

  @Override
  protected void performAction(@NotNull Client c) {
    c.executeGarbageCollector();
  }
}
