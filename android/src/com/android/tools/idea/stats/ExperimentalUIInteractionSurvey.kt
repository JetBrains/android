/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.tools.idea.stats

import com.android.tools.idea.serverflags.protos.Option
import com.android.tools.idea.serverflags.protos.Survey

val EXPERIMENTAL_UI_INTERACTION_SURVEY: Survey = Survey.newBuilder().apply {
  title = "Android Studio Feedback"
  question = "Why did you disable the New UI? (Select all that apply)"
  answerCount = 8
  addOptions(Option.newBuilder().apply {
    label = "The UI feels unfamiliar"
  })
  addOptions(Option.newBuilder().apply {
    label = "I can’t access or find features I used before"
  })
  addOptions(Option.newBuilder().apply {
    label = "Icons are unclear"
  })
  addOptions(Option.newBuilder().apply {
    label = "The color theme is too high-contrast"
  })
  addOptions(Option.newBuilder().apply {
    label = "Less space for code"
  })
  addOptions(Option.newBuilder().apply {
    label = "Lack of customization options"
  })
  addOptions(Option.newBuilder().apply {
    label = "Too much space between menu items/UI elements"
  })
  addOptions(Option.newBuilder().apply {
    label = "Other"
  })
  name = "Experimental UI Satisfaction Survey"
  answerPolicy = Survey.AnswerPolicy.LAX
}.build()
