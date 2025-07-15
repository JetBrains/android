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
package com.android.tools.idea.uibuilder.handlers.constraint

import com.android.tools.idea.common.model.NlComponent

/**
 * Represents one of the constraints of the given [component]. This class is used to for example
 * indicate which constraint is being highlighted on hover or selected. The components are
 * considered Primary while the constraints are considered Secondary (as they link primary
 * components).
 */
data class SecondarySelector(val component: NlComponent, val constraint: Constraint) {
  enum class Constraint {
    LEFT,
    RIGHT,
    TOP,
    BOTTOM,
    BASELINE,
  }
}
