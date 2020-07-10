/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.npw

import com.android.tools.adtui.device.FormFactor

// TODO(qumeric): Probably two classes should be merged
fun FormFactor.toTemplateFormFactor(): com.android.tools.idea.wizard.template.FormFactor = when(this) {
  FormFactor.MOBILE -> com.android.tools.idea.wizard.template.FormFactor.Mobile
  FormFactor.WEAR -> com.android.tools.idea.wizard.template.FormFactor.Wear
  FormFactor.TV -> com.android.tools.idea.wizard.template.FormFactor.Tv
  FormFactor.AUTOMOTIVE -> com.android.tools.idea.wizard.template.FormFactor.Automotive
  FormFactor.THINGS -> com.android.tools.idea.wizard.template.FormFactor.Things
}

fun com.android.tools.idea.wizard.template.FormFactor.toWizardFormFactor() = when(this) {
  com.android.tools.idea.wizard.template.FormFactor.Mobile -> FormFactor.MOBILE
  com.android.tools.idea.wizard.template.FormFactor.Wear -> FormFactor.WEAR
  com.android.tools.idea.wizard.template.FormFactor.Tv -> FormFactor.TV
  com.android.tools.idea.wizard.template.FormFactor.Automotive -> FormFactor.AUTOMOTIVE
  com.android.tools.idea.wizard.template.FormFactor.Things -> FormFactor.THINGS
  com.android.tools.idea.wizard.template.FormFactor.Generic -> FormFactor.MOBILE
}

