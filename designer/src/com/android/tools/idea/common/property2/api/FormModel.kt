/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.common.property2.api

/**
 * Model for a ui displaying properties.
 *
 * The property system may support multiple views of the properties.
 * Such as: a property inspector and a properties table.
 * The interface specifies common states and operations that are accessible
 * through a [PropertyEditorModel]. This interface should be implemented
 * by internal property classes but should never be implemented by
 * external implementations.
 */
interface FormModel {
  /**
   * If true the value in an editor should show the resolved value of a property.
   */
  var showResolvedValues: Boolean

  /**
   * Move the focus to the next logical editor in the model.
   *
   * An editor may request to move to the next editor.
   * Usually this is done after the control has successfully committed a new
   * value after a <enter> key is pressed.
   * This navigation is provided in addition to the normal <tab> order which
   * may navigate to the next control for the same property.
   */
  fun moveToNextLineEditor(line: InspectorLineModel): Boolean

  /**
   * Notify all editors about value changes in 1 or more properties.
   */
  fun propertyValuesChanged()
}
