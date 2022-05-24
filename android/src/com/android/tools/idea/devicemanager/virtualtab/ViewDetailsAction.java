/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.idea.devicemanager.virtualtab;

import com.android.tools.idea.avdmanager.AvdUiAction;
import com.intellij.util.ui.EmptyIcon;
import java.awt.event.ActionEvent;
import org.jetbrains.annotations.NotNull;

public final class ViewDetailsAction extends AvdUiAction {
  public ViewDetailsAction(@NotNull AvdInfoProvider provider) {
    super(provider, "View details", "", EmptyIcon.ICON_16);
  }

  @Override
  public boolean isEnabled() {
    return true;
  }

  @Override
  public void actionPerformed(@NotNull ActionEvent event) {
    ((VirtualDeviceTable)myAvdInfoProvider.getAvdProviderComponent()).getPanel().viewDetails();
  }
}
