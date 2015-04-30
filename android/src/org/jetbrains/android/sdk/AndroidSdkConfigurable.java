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

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.projectRoots.*;
import com.intellij.openapi.util.io.FileUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

import static org.jetbrains.android.sdk.AndroidSdkUtils.getAndroidSdkAdditionalData;
import static org.jetbrains.android.sdk.AndroidSdkUtils.isAndroidSdk;

/**
 * @author Eugene.Kudelevsky
 */
public class AndroidSdkConfigurable implements AdditionalDataConfigurable {
  private final AndroidSdkConfigurableForm myForm;

  private Sdk mySdk;
  private final SdkModel.Listener myListener;
  private final SdkModel mySdkModel;

  public AndroidSdkConfigurable(@NotNull SdkModel sdkModel, @NotNull SdkModificator sdkModificator) {
    mySdkModel = sdkModel;
    myForm = new AndroidSdkConfigurableForm(sdkModel, sdkModificator);
    myListener = new SdkModel.Listener() {
      @Override
      public void sdkAdded(Sdk sdk) {
        if (sdk.getSdkType().equals(JavaSdk.getInstance())) {
          myForm.addJavaSdk(sdk);
        }
      }

      @Override
      public void beforeSdkRemove(Sdk sdk) {
        if (sdk.getSdkType().equals(JavaSdk.getInstance())) {
          myForm.removeJavaSdk(sdk);
        }
      }

      @Override
      public void sdkChanged(Sdk sdk, String previousName) {
        if (sdk.getSdkType().equals(JavaSdk.getInstance())) {
          myForm.updateJdks(sdk, previousName);
        }
      }

      @Override
      public void sdkHomeSelected(final Sdk sdk, final String newSdkHome) {
        if (sdk != null && isAndroidSdk(sdk)) {
          myForm.internalJdkUpdate(sdk);
        }
      }
    };
    mySdkModel.addListener(myListener);
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
    final AndroidSdkAdditionalData data = getAndroidSdkAdditionalData(mySdk);
    Sdk javaSdk = data != null ? data.getJavaSdk() : null;
    final String javaSdkHomePath = javaSdk != null ? javaSdk.getHomePath() : null;
    final Sdk selectedSdk = myForm.getSelectedSdk();
    final String selectedSdkHomePath = selectedSdk != null ? selectedSdk.getHomePath() : null;
    return !FileUtil.pathsEqual(javaSdkHomePath, selectedSdkHomePath);
  }

  @Override
  public void apply() throws ConfigurationException {
    final Sdk javaSdk = myForm.getSelectedSdk();
    AndroidSdkAdditionalData newData = new AndroidSdkAdditionalData(mySdk, javaSdk);
    newData.setBuildTarget(myForm.getSelectedBuildTarget());
    final SdkModificator modificator = mySdk.getSdkModificator();
    modificator.setVersionString(javaSdk != null ? javaSdk.getVersionString() : null);
    modificator.setSdkAdditionalData(newData);
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      @Override
      public void run() {
        modificator.commitChanges();
      }
    });
  }

  @Override
  public void reset() {
    if (mySdk == null) {
      return;
    }
    AndroidSdkAdditionalData data = AndroidSdkUtils.getAndroidSdkAdditionalData(mySdk);
    if (data == null) {
      return;
    }
    AndroidPlatform platform = data.getAndroidPlatform();
    myForm.init(data.getJavaSdk(), mySdk, platform != null ? data.getBuildTarget(platform.getSdkData()) : null);
  }

  @Override
  public void disposeUIResources() {
    mySdkModel.removeListener(myListener);
  }
}
