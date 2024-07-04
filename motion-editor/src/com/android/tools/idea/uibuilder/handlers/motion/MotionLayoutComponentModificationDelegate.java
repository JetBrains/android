/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.tools.idea.uibuilder.handlers.motion;

import com.android.SdkConstants;
import com.android.tools.idea.common.model.NlComponentModificationDelegate;
import com.android.tools.idea.uibuilder.handlers.constraint.ComponentModification;
import com.android.utils.Pair;
import java.util.HashMap;
import org.jetbrains.annotations.NotNull;

/**
 * Intercepts component modifications to redirect them to the correct place
 * (layout, constraintset in a MotionScene)
 */
public class MotionLayoutComponentModificationDelegate implements NlComponentModificationDelegate {

  @Override
  public void initializeModification(@NotNull ComponentModification modification) {
    MotionUtils.fillComponentModification(modification);
  }

  @Override
  public void applyModification(@NotNull ComponentModification modification) {
    MotionUtils.apply(modification);
  }

  @Override
  public void commitModification(@NotNull ComponentModification modification) {
    MotionUtils.commit(modification);
  }

  @Override
  public void setAttribute(HashMap<Pair<String, String>, String> attributes, String namespace, String name, String value) {
    attributes.put(Pair.of(SdkConstants.SHERPA_URI, name), value);
  }

  @Override
  public String getAttribute(HashMap<Pair<String, String>, String> attributes, String namespace, String attribute) {
    return attributes.get(Pair.of(SdkConstants.SHERPA_URI, attribute));
  }
}
