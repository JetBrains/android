/*
 * Copyright 2000-2011 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.android.sdk;

import com.android.sdklib.IAndroidTarget;
import com.android.tools.sdk.AndroidPlatform;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.projectRoots.*;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public class AndroidSdkConfigurable implements AdditionalDataConfigurable {
  private final AndroidSdkConfigurableForm myForm;

  private Sdk mySdk;

  public AndroidSdkConfigurable(@NotNull SdkModificator sdkModificator) {
    myForm = new AndroidSdkConfigurableForm(sdkModificator);
  }

  @Override
  public void setSdk(Sdk sdk) {
    mySdk = sdk;
  }

  @Override
  public JComponent createComponent() {
    return myForm.getContentPanel();
  }

  @Override
  public boolean isModified() {
    AndroidSdkAdditionalData data = AndroidSdkAdditionalData.from(mySdk);
    AndroidPlatform currentAndroidPlatform = data != null ? data.getAndroidPlatform() : null;
    IAndroidTarget currentAndroidTarget = currentAndroidPlatform != null ? currentAndroidPlatform.getTarget() : null;
    IAndroidTarget selectedBuildTarget = myForm.getSelectedBuildTarget();
    return selectedBuildTarget != null && !selectedBuildTarget.equals(currentAndroidTarget);
  }

  @Override
  public void apply() throws ConfigurationException {
    AndroidSdkAdditionalData newData = new AndroidSdkAdditionalData(mySdk);
    newData.setBuildTarget(myForm.getSelectedBuildTarget());
    SdkModificator modificator = mySdk.getSdkModificator();
    modificator.setSdkAdditionalData(newData);
    ApplicationManager.getApplication().runWriteAction(modificator::commitChanges);
  }

  @Override
  public void reset() {
    if (mySdk == null) {
      return;
    }
    AndroidSdkAdditionalData data = AndroidSdkAdditionalData.from(mySdk);
    if (data == null) {
      return;
    }
    AndroidPlatform platform = data.getAndroidPlatform();
    myForm.init(mySdk, platform != null ? data.getBuildTarget(platform.getSdkData()) : null);
  }
}
