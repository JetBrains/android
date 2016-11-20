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
package com.android.tools.adtui;

import com.android.tools.adtui.model.Range;
import com.android.tools.adtui.model.RangedListModel;

public class RangedList implements Animatable {

  private final RangedListModel myModel;
  private final Range myRange;

  public RangedList(Range range, RangedListModel model) {
    myModel = model;
    myRange = range;
  }

  @Override
  public void animate(float frameLength) {
    myModel.update(myRange);
  }
}
