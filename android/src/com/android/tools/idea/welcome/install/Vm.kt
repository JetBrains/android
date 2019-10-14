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
package com.android.tools.idea.welcome.install

import com.android.repository.io.FileOp
import com.android.tools.idea.wizard.dynamic.ScopedStateStore

abstract class Vm(
  stateStore: ScopedStateStore,
  name: String,
  description: String,
  installUpdates: Boolean,
  fileOp: FileOp,
  @JvmField val installationIntention: InstallationIntention,
  @JvmField val isCustomInstall: ScopedStateStore.Key<Boolean>
) : InstallableComponent(stateStore, name, description, installUpdates, fileOp) {
  var isInstallerSuccessfullyCompleted: Boolean = false
    protected set

  public abstract override val requiredSdkPackages: Collection<String>
}