/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.compose.pickers.common.editingsupport

import com.android.tools.adtui.model.stdui.EditingErrorCategory

internal val ERROR_GREATER_THAN_ZERO = Pair(EditingErrorCategory.ERROR, "Should be at least 1")

internal val ERROR_NOT_INTEGER = Pair(EditingErrorCategory.ERROR, "Not an Integer")
internal val ERROR_NOT_FLOAT = Pair(EditingErrorCategory.ERROR, "Not a Float")
internal val ERROR_NOT_BOOLEAN = Pair(EditingErrorCategory.ERROR, "Not a Boolean")

internal val WARN_FORMAT = Pair(EditingErrorCategory.WARNING, "Use the proper suffix (f) e.g: 1.0f")
internal val WARN_GREATER_THAN_ZERO = Pair(EditingErrorCategory.WARNING, "Should be at least 1")
internal val WARN_DECIMALS = Pair(EditingErrorCategory.WARNING, "Only one decimal supported")
