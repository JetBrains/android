/*
 * Copyright (C) 2025 The Android Open Source Project
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
package com.android.tools.idea.glassespairing

import com.android.tools.idea.glassespairing.api.PhoneGlassesPair
import com.android.tools.idea.glassespairing.api.WizardAction


private const val GLASSES_PAIRING_NOTIFICATION_GROUP_ID = "Glasses Pairing"

interface GlassesPairingNotificationManager {
  fun showReconnectMessageBalloon(
    phoneGlassesPair: PhoneGlassesPair,
    wizardAction: WizardAction?,
  )

  fun showConnectionDroppedBalloon(
    offlineName: String,
    phoneGlassesPair: PhoneGlassesPair,
    wizardAction: WizardAction?,
  )

  fun dismissNotifications(phoneGlassesPair: PhoneGlassesPair)

  companion object {
    private val instance = GlassesPairingNotificationManagerImpl()

    fun getInstance(): GlassesPairingNotificationManager = instance
  }
}


class GlassesPairingNotificationManagerImpl : GlassesPairingNotificationManager {
  override fun showReconnectMessageBalloon(phoneGlassesPair: PhoneGlassesPair,
                                           wizardAction: WizardAction?) {}

  override fun showConnectionDroppedBalloon(offlineName: String,
                                            phoneGlassesPair: PhoneGlassesPair,
                                            wizardAction: WizardAction?) {}

  override fun dismissNotifications(phoneGlassesPair: PhoneGlassesPair) {}
}