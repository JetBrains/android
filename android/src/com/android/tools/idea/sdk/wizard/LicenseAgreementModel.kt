/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.tools.idea.sdk.wizard

import com.android.repository.api.License
import com.android.tools.idea.observable.ObservableValue
import com.android.tools.idea.observable.core.OptionalValueProperty
import com.android.tools.idea.wizard.model.WizardModel
import com.intellij.openapi.diagnostic.logger
import java.nio.file.Path
import java.util.Optional

private val log get() = logger<LicenseAgreementModel>()

/**
 * [WizardModel] that stores all the licenses related to the packages the user is about to install
 * and marks them as accepted after the packages are installed so that the user only accepts each license once.
 */
class LicenseAgreementModel: WizardModel {
  val licenses =  hashSetOf<License>()
  val sdkRoot: ObservableValue<Optional<Path>>

  constructor(sdkLocation: Path?) {
    sdkRoot = OptionalValueProperty()
    if (sdkLocation != null) {
      sdkRoot.setValue(sdkLocation)
    }
    else {
      sdkRoot.clear()
    }
  }

  constructor(sdkLocation: ObservableValue<Optional<Path>>) {
    sdkRoot = sdkLocation
  }

  override fun handleFinished() = acceptLicenses()

  fun acceptLicenses() {
    val root = sdkRoot.get()
    if (root.isEmpty) {
      log.error("The wizard could not find the SDK repository folder and will not complete. Please report this error.")
      return
    }

    licenses.forEach {
      it.setAccepted(root.get())
    }
  }
}
