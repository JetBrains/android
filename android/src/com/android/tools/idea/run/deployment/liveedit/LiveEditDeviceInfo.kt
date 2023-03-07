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
package com.android.tools.idea.run.deployment.liveedit

// To expose only immutable objects, we return this interface
interface LiveEditDeviceInfo {
  val status : LiveEditStatus

  val app: LiveEditApp? // This can be null if a device starts to be monitored but we don't know if the app was installed on it.
}

// LiveEdit custom class to track the state of a device. Each LiveEditService (running on a per-project basis)
// gets its own copy of LiveEditDevices which contains a set of LiveEditDevice.
//
// The app can be null if we have no information about it (we have not seen a deployment to that device yet)
internal class LiveEditDeviceInfoImpl(override var status: LiveEditStatus, override val app: LiveEditApp?): LiveEditDeviceInfo {
}
