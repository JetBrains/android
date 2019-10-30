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
package com.android.tools.profilers.customevent;

import com.android.tools.adtui.LegendComponent;
import com.android.tools.adtui.model.legend.LegendComponentModel;

/**
 * CustomEventTrackLegendComponent is necessary to override isShowing() to true for a track's legend to be visible. This is due to the
 * track's peer always being null, which means isShowing() will always return false for a track's LegendComponent otherwise.
 */
public class CustomEventTrackLegendComponent extends LegendComponent {

  /**
   * Constructor for this class is not public so that this legend component is only used for CustomEventTrack UI.
   */
  CustomEventTrackLegendComponent(LegendComponentModel model) {
    super(model);
  }

  @Override
  public boolean isShowing() {
    return true;
  }
}