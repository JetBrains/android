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
package com.android.tools.idea.sdk.wizard;

import com.android.annotations.Nullable;
import com.android.tools.idea.sdk.SdkState;
import com.android.tools.idea.wizard.TemplateWizardState;
import com.android.utils.Pair;

import java.util.List;

public class SmwState extends TemplateWizardState {
  public static final String SDK_STATE = "SMW_SDK_STATE";
  public static final String SELECTED_ACTIONS = "SMW_SELECTED_ACTIONS";

  public SmwState(@Nullable SdkState sdkState) {
    put(SDK_STATE, sdkState);
  }

  @Nullable
  public SdkState getSdkState() {
    return (SdkState) get(SDK_STATE);
  }

  public void setSelectedActions(@Nullable List<Pair<SmwSelectionAction, Object>> selectedActions) {
    put(SELECTED_ACTIONS, selectedActions);
  }

  @Nullable
  public List<Pair<SmwSelectionAction, Object>> getSelectedActions() {
    //noinspection unchecked
    return (List<Pair<SmwSelectionAction, Object>>) get(SELECTED_ACTIONS);
  }
}
