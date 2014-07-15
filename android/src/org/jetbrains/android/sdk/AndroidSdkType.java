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

import com.android.SdkConstants;
import com.android.sdklib.AndroidVersion;
import com.android.sdklib.IAndroidTarget;
import com.android.tools.idea.sdk.Jdks;
import com.intellij.CommonBundle;
import com.intellij.openapi.projectRoots.*;
import com.intellij.openapi.projectRoots.impl.JavaDependentSdkType;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.io.FileUtil;
import icons.AndroidIcons;
import org.jdom.Element;
import org.jetbrains.android.actions.RunAndroidSdkManagerAction;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * @author Eugene.Kudelevsky
 */
public class AndroidSdkType extends JavaDependentSdkType implements JavaSdkType {

  @NonNls public static final String SDK_NAME = "Android SDK";
  @NonNls public static final String DEFAULT_EXTERNAL_DOCUMENTATION_URL = "http://developer.android.com/reference/";

  public AndroidSdkType() {
    super(SDK_NAME);
  }

  @Nullable
  @Override
  public String getBinPath(@NotNull Sdk sdk) {
    final Sdk internalJavaSdk = getInternalJavaSdk(sdk);
    return internalJavaSdk == null ? null : JavaSdk.getInstance().getBinPath(internalJavaSdk);
  }

  @Override
  @Nullable
  public String getToolsPath(@NotNull Sdk sdk) {
    final Sdk jdk = getInternalJavaSdk(sdk);
    if (jdk != null && jdk.getVersionString() != null) {
      return JavaSdk.getInstance().getToolsPath(jdk);
    }
    return null;
  }

  @Override
  @Nullable
  public String getVMExecutablePath(@NotNull Sdk sdk) {
    final Sdk internalJavaSdk = getInternalJavaSdk(sdk);
    return internalJavaSdk == null ? null : JavaSdk.getInstance().getVMExecutablePath(internalJavaSdk);
  }

  @Override
  public String suggestHomePath() {
    return null;
  }

  @Override
  public boolean isValidSdkHome(String path) {
    return validateAndroidSdk(path).getFirst();
  }

  /**
   * Indicates whether the given path belongs to a valid Android SDK.
   *
   * @param path the given path.
   * @return a pair where the first value is {@code true} if the path belongs to a valid Android SDK. If pair's first value is
   * {@code false}, the second value will be a non-null explaining why the path is not valid.
   */
  @NotNull
  public static Pair<Boolean, String> validateAndroidSdk(@Nullable String path) {
    if (path == null) {
      return Pair.create(Boolean.FALSE, "");
    }

    path = FileUtil.toSystemDependentName(path);
    final File f = new File(path);
    if (!f.exists() || !f.isDirectory()) {
      return Pair.create(Boolean.FALSE, "SDK does not exist");
    }

    final File platformsDir = new File(f, SdkConstants.FD_PLATFORMS);
    if (!platformsDir.exists() || !platformsDir.isDirectory()) {
      return Pair.create(Boolean.FALSE, "SDK does not contain any platforms");
    }

    //noinspection ConstantConditions
    return Pair.create(Boolean.TRUE, null);
  }

  @Override
  public String getVersionString(@NotNull Sdk sdk) {
    final Sdk internalJavaSdk = getInternalJavaSdk(sdk);
    return internalJavaSdk != null ? internalJavaSdk.getVersionString() : null;
  }

  @Nullable
  @Override
  public String suggestSdkName(String currentSdkName, String sdkHome) {
    return SDK_NAME;
  }

  @Override
  public boolean setupSdkPaths(Sdk sdk, SdkModel sdkModel) {
    final List<String> javaSdks = new ArrayList<String>();
    final Sdk[] sdks = sdkModel.getSdks();
    for (Sdk jdk : sdks) {
      if (Jdks.isApplicableJdk(jdk)) {
        javaSdks.add(jdk.getName());
      }
    }

    if (javaSdks.isEmpty()){
      Messages.showErrorDialog(AndroidBundle.message("no.jdk.for.android.found.error"), "No Java SDK Found");
      return false;
    }

    MessageBuildingSdkLog log = new MessageBuildingSdkLog();
    AndroidSdkData sdkData = AndroidSdkData.getSdkData(sdk);

    if (sdkData == null) {
      String errorMessage = log.getErrorMessage().length() > 0 ? log.getErrorMessage() : AndroidBundle.message("cannot.parse.sdk.error");
      Messages.showErrorDialog(errorMessage, "SDK Parsing Error");
      return false;
    }

    IAndroidTarget[] targets = sdkData.getTargets();

    if (targets.length == 0) {
      if (Messages.showOkCancelDialog(AndroidBundle.message("no.android.targets.error"), CommonBundle.getErrorTitle(),
                                      "Open SDK Manager", Messages.CANCEL_BUTTON, Messages.getErrorIcon()) == Messages.OK) {
        RunAndroidSdkManagerAction.runSpecificSdkManager(null, sdkData.getLocation());
      }
      return false;
    }

    String[] targetNames = new String[targets.length];

    String newestPlatform = null;
    AndroidVersion version = null;

    for (int i = 0; i < targets.length; i++) {
      IAndroidTarget target = targets[i];
      String targetName = AndroidSdkUtils.getTargetPresentableName(target);
      targetNames[i] = targetName;
      if (target.isPlatform() && (version == null || target.getVersion().compareTo(version) > 0)) {
        newestPlatform = targetName;
        version = target.getVersion();
      }
    }

    final AndroidNewSdkDialog dialog =
      new AndroidNewSdkDialog(null, javaSdks, javaSdks.get(0), Arrays.asList(targetNames),
                                       newestPlatform != null ? newestPlatform : targetNames[0]);
    dialog.show();

    if (!dialog.isOK()) {
      return false;
    }
    final String name = javaSdks.get(dialog.getSelectedJavaSdkIndex());
    final Sdk jdk = sdkModel.findSdk(name);
    final IAndroidTarget target = targets[dialog.getSelectedTargetIndex()];
    final String sdkName = AndroidSdkUtils.chooseNameForNewLibrary(target);
    AndroidSdkUtils.setUpSdk(sdk, sdkName, sdks, target, jdk, true);
    return true;
  }

  @Override
  public AdditionalDataConfigurable createAdditionalDataConfigurable(SdkModel sdkModel, SdkModificator sdkModificator) {
    return new AndroidSdkConfigurable(sdkModel, sdkModificator);
  }

  @Override
  public void saveAdditionalData(@NotNull SdkAdditionalData data, @NotNull Element e) {
    if (data instanceof AndroidSdkAdditionalData) {
      ((AndroidSdkAdditionalData)data).save(e);
    }
  }

  @Override
  public SdkAdditionalData loadAdditionalData(@NotNull Sdk currentSdk, Element additional) {
    return new AndroidSdkAdditionalData(currentSdk, additional);
  }

  @Override
  public String getPresentableName() {
    return AndroidBundle.message("android.sdk.presentable.name");
  }

  @Override
  public Icon getIcon() {
    return AndroidIcons.Android;
  }

  @Override
  public Icon getIconForAddAction() {
    return getIcon();
  }

  @Nullable
  @Override
  public String getDefaultDocumentationUrl(@NotNull Sdk sdk) {
    return DEFAULT_EXTERNAL_DOCUMENTATION_URL;
  }

  @Nullable
  private static Sdk getInternalJavaSdk(Sdk sdk) {
    final SdkAdditionalData data = sdk.getSdkAdditionalData();
    if (data instanceof AndroidSdkAdditionalData) {
      return ((AndroidSdkAdditionalData)data).getJavaSdk();
    }
    return null;
  }

  public static AndroidSdkType getInstance() {
    return SdkType.findInstance(AndroidSdkType.class);
  }
}
