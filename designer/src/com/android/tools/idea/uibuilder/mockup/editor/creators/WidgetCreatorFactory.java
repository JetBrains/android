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

import com.android.SdkConstants;
import com.android.tools.idea.uibuilder.mockup.Mockup;
import com.android.tools.idea.common.model.NlModel;
import com.android.tools.idea.uibuilder.surface.ScreenView;
import org.jetbrains.annotations.NotNull;

import java.awt.*;

/**
 * Handles the creation of the widgets and layouts from a mockup using the
 * android class name for the view.
 */
public final class WidgetCreatorFactory {


  /**
   * Creates a new WidgetCreator for the given View tagName.
   * If no {@link WidgetCreator} exists for the given tagName, returns a {@link SimpleViewCreator}
   * that will create a simple View tag
   *
   * @param tagName   The tag of the view to create
   * @param mockup    The mockup to extract information from
   * @param model     The model to add the new component to
   * @param screenView The current ScreenView where the Mockup is used
   * @param selection The bounds of the selection on the {@link com.android.tools.idea.uibuilder.mockup.editor.MockupEditor}
   * @return A {@link WidgetCreator} to create the view with tagName or default to a simple View
   */
  @NotNull
  public static WidgetCreator create(@NotNull String tagName,
                                     @NotNull Mockup mockup,
                                     @NotNull NlModel model,
                                     @NotNull ScreenView screenView,
                                     @NotNull Rectangle selection) {
    @NotNull final WidgetCreator creator;
    switch (tagName) {
      case SdkConstants.VIEW_INCLUDE:
        creator = new IncludeTagCreator(mockup, model, screenView, selection);
        break;
      case SdkConstants.IMAGE_VIEW:
        creator = new ImageViewCreator(mockup, model, screenView, selection);
        break;
      case SdkConstants.FLOATING_ACTION_BUTTON:
        creator = new FloatingActionButtonCreator(mockup, model, screenView, selection);
        break;
      case SdkConstants.TEXT_VIEW:
        creator = new TextViewCreator(mockup, model, screenView, selection);
        break;
      case SdkConstants.ATTR_DRAWABLE:
        creator = new ExtractIconCreator(mockup, model, screenView, selection);
        break;
      case SdkConstants.RECYCLER_VIEW:
        creator = new RecyclerViewCreator(mockup, model, screenView, selection);
        break;
      default:
        creator = new AutoCompleteViewCreator(mockup, model, screenView, selection);
    }
    return creator;
  }
}
