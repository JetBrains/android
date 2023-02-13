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
package com.android.tools.idea.preview.mvvm

import com.android.tools.idea.projectsystem.BuildListener

/**
 * Preview ViewModel interface in the MVVM pattern. Intended to be accessed by the Model
 * (PreviewRepresentation).
 */
interface PreviewViewModel : BuildListener {

  fun checkForNativeCrash(runnable: Runnable): Boolean

  fun refreshStarted()

  fun refreshFinished()

  fun beforePreviewsRefreshed()

  fun afterPreviewsRefreshed()

  fun setHasPreviews(hasPreviews: Boolean)

  fun refreshCompleted(isCancelled: Boolean, durationNanos: Long)

  fun onEnterSmartMode()

  fun activate()
}
