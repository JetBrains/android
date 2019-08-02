/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.whatsnew.assistant;

import com.android.tools.idea.assistant.PanelFactory;
import org.jetbrains.annotations.NotNull;

/**
 * Singleton extension that creates Panels that show update status in WNA
 */
public final class UpdateStatusPanelFactory implements PanelFactory {
  public static final String FACTORY_ID = "wna.update.status";

  @Override
  @NotNull
  public Panel create() {
    return new UpdateStatusPanel();
  }

  @Override
  @NotNull
  public String getId() {
    return FACTORY_ID;
  }

  /**
   * WNA's implementation of Panel that kicks off the UpdateChecker when a panel is created,
   * to populate the panel with the list of incompatible plugins, if any
   */
  private static final class UpdateStatusPanel extends Panel {
    // TODO: Apply the rest of ag/8023957 when UpdateChecker refactor is merged
  }
}
