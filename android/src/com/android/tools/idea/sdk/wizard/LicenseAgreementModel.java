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
package com.android.tools.idea.sdk.wizard;

import com.android.repository.api.License;
import com.android.repository.io.FileOpUtils;
import com.android.tools.idea.ui.properties.core.ObservableOptional;
import com.android.tools.idea.ui.properties.core.OptionalProperty;
import com.android.tools.idea.ui.properties.core.OptionalValueProperty;
import com.android.tools.idea.wizard.model.WizardModel;
import com.google.common.collect.Sets;
import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.Set;

/**
 * {@link WizardModel} that stores all the licenses related to the packages the user is about to install
 * and marks them as accepted after the packages are installed so that the user only accepts each license once.
 */
public final class LicenseAgreementModel extends WizardModel {

  private final Set<License> myLicenses = Sets.newHashSet();

  private final OptionalProperty<File> mySdkRoot = new OptionalValueProperty<File>();

  public Set<License> getLicenses() {
    return myLicenses;
  }

  public ObservableOptional<File> sdkRoot() {
    return mySdkRoot;
  }

  private static Logger getLog() {
    return Logger.getInstance(LicenseAgreementModel.class);
  }


  public LicenseAgreementModel(@Nullable File sdkLocation) {
    if (sdkLocation != null) {
      mySdkRoot.setValue(sdkLocation);
    }
    else {
      mySdkRoot.clear();
    }
  }

  @Override
  protected void handleFinished() {
    if(!mySdkRoot.get().isPresent()){
      getLog().error("The wizard could not find the SDK repository folder and will not complete. Please report this error.");
      return;
    }

    for (License license : myLicenses) {
      license.setAccepted(mySdkRoot.getValue(), FileOpUtils.create());
    }
  }
}
