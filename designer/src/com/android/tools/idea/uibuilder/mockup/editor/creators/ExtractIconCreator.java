/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.uibuilder.mockup.editor.creators;

import com.android.tools.idea.uibuilder.mockup.Mockup;
import com.android.tools.idea.common.model.AttributesTransaction;
import com.android.tools.idea.common.model.NlComponent;
import com.android.tools.idea.common.model.NlModel;
import com.android.tools.idea.uibuilder.surface.NlDesignSurface;
import com.android.tools.idea.uibuilder.surface.ScreenView;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

/**
 * Extract a Drawable without creating any component
 */
public class ExtractIconCreator extends ImageViewCreator {

  /**
   * Create a new creator to extract a drawable without creating a new component
   *
   * @param mockup     the mockup to extract the information from
   * @param model      the model to insert the new component into
   * @param screenView The currentScreen view displayed in the {@link NlDesignSurface}.
   */
  protected ExtractIconCreator(@NotNull Mockup mockup,
                               @NotNull NlModel model,
                               @NotNull ScreenView screenView,
                               @NotNull Rectangle selection) {
    super(mockup, model, screenView, selection);
  }

  @Override
  protected void addAttributes(@NotNull AttributesTransaction transaction) {
  }

  @NotNull
  @Override
  public String getAndroidViewTag() {
    return "";
  }

  @Nullable
  @Override
  public NlComponent addToModel() {
    return null;
  }

  @Override
  public boolean hasOptionsComponent() {
    return true;
  }

  @Nullable
  @Override
  public JComponent getOptionsComponent(@NotNull DoneCallback doneCallback) {
    return super.getOptionsComponent(doneCallback);
  }
}
