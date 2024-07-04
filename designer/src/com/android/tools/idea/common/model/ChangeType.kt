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
package com.android.tools.idea.common.model

/** Type of the change applied to [NlModel]. */
enum class ChangeType {
  RESOURCE_EDIT,
  EDIT,
  RESOURCE_CHANGED,
  ADD_COMPONENTS,
  DELETE,
  DND_COMMIT,
  DND_END,
  DROP,
  RESIZE_END,
  RESIZE_COMMIT,
  UPDATE_HIERARCHY,
  BUILD,
  CONFIGURATION_CHANGE,

  /**
   * When the model is not active, it will batch all the notifications and send this one on
   * reactivation.
   */
  MODEL_ACTIVATION,
}
