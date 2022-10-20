// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.android.sdk;

import com.android.sdklib.AndroidVersion;
import com.android.sdklib.IAndroidTarget;
import com.android.tools.idea.io.FilePaths;
import com.android.tools.idea.sdk.AndroidSdks;
import com.android.tools.idea.sdk.IdeSdks;
import com.android.tools.idea.sdk.wizard.SdkQuickfixUtils;
import com.intellij.CommonBundle;
import com.intellij.openapi.projectRoots.*;
import com.intellij.openapi.projectRoots.impl.JavaDependentSdkType;
import com.intellij.openapi.roots.AnnotationOrderRootType;
import com.intellij.openapi.roots.JavadocOrderRootType;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.ui.Messages;
import icons.StudioIcons;
import java.util.ArrayList;
import org.jdom.Element;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.io.File;
import java.util.Arrays;
import java.util.List;

import static com.android.tools.idea.sdk.SdkPaths.validateAndroidSdk;
import static com.intellij.openapi.util.text.StringUtil.isEmpty;
import static org.jetbrains.android.sdk.AndroidSdkData.getSdkData;
import static org.jetbrains.android.sdk.AndroidSdkUtils.getTargetPresentableName;

public class AndroidSdkType extends JavaDependentSdkType implements JavaSdkType {
  @NonNls public static final String SDK_NAME = "Android SDK";
  @NonNls public static final String DEFAULT_EXTERNAL_DOCUMENTATION_PATH = "developer.android.com/reference/";
  @NonNls public static final String DEFAULT_EXTERNAL_DOCUMENTATION_URL = "http://" + DEFAULT_EXTERNAL_DOCUMENTATION_PATH;

  public AndroidSdkType() {
    super(SDK_NAME);
  }

  @Override
  @Nullable
  public String getBinPath(@NotNull Sdk sdk) {
    Sdk internalJavaSdk = getInternalJavaSdk(sdk);
    return internalJavaSdk == null ? null : JavaSdk.getInstance().getBinPath(internalJavaSdk);
  }

  @Override
  @Nullable
  public String getToolsPath(@NotNull Sdk sdk) {
    Sdk jdk = getInternalJavaSdk(sdk);
    if (jdk != null && jdk.getVersionString() != null) {
      return JavaSdk.getInstance().getToolsPath(jdk);
    }
    return null;
  }

  @Override
  @Nullable
  public String getVMExecutablePath(@NotNull Sdk sdk) {
    Sdk internalJavaSdk = getInternalJavaSdk(sdk);
    return internalJavaSdk == null ? null : JavaSdk.getInstance().getVMExecutablePath(internalJavaSdk);
  }

  @Override
  @Nullable
  public String suggestHomePath() {
    return null;
  }

  @Override
  public boolean isValidSdkHome(@NotNull String path) {
    if (isEmpty(path)) {
      return false;
    }
    File sdkPath = FilePaths.stringToFile(path);
    return validateAndroidSdk(sdkPath, false).success;
  }

  @Override
  public String getVersionString(@NotNull Sdk sdk) {
    Sdk internalJavaSdk = getInternalJavaSdk(sdk);
    return internalJavaSdk != null ? internalJavaSdk.getVersionString() : null;
  }

  @Override
  @NotNull
  public String suggestSdkName(@Nullable String currentSdkName, @NotNull String sdkHome) {
    return SDK_NAME;
  }

  @Override
  public boolean setupSdkPaths(@NotNull Sdk sdk, @NotNull SdkModel sdkModel) {
    final List<String> javaSdks = new ArrayList<>();
    final Sdk[] sdks = sdkModel.getSdks();
    for (Sdk jdk : sdks) {
      if (IdeSdks.getInstance().isJdkCompatible(jdk)) {
        javaSdks.add(jdk.getName());
      }
    }

    if (javaSdks.isEmpty()) {
      Messages.showErrorDialog(AndroidBundle.message("no.jdk.for.android.found.error"), "No Java SDK Found");
      return false;
    }

    MessageBuildingSdkLog log = new MessageBuildingSdkLog();
    AndroidSdkData sdkData = getSdkData(sdk);

    if (sdkData == null) {
      String errorMessage = !log.getErrorMessage().isEmpty() ? log.getErrorMessage() : AndroidBundle.message("cannot.parse.sdk.error");
      Messages.showErrorDialog(errorMessage, "SDK Parsing Error");
      return false;
    }

    IAndroidTarget[] targets = sdkData.getTargets();

    if (targets.length == 0) {
      if (Messages.showOkCancelDialog(AndroidBundle.message("no.android.targets.error"), CommonBundle.getErrorTitle(),
                                      "Open SDK Manager", Messages.getCancelButton(), Messages.getErrorIcon()) == Messages.OK) {
        SdkQuickfixUtils.showAndroidSdkManager();
      }
      return false;
    }

    String[] targetNames = new String[targets.length];

    String newestPlatform = null;
    AndroidVersion version = null;

    for (int i = 0; i < targets.length; i++) {
      IAndroidTarget target = targets[i];
      String targetName = getTargetPresentableName(target);
      targetNames[i] = targetName;
      if (target.isPlatform() && (version == null || target.getVersion().compareTo(version) > 0)) {
        newestPlatform = targetName;
        version = target.getVersion();
      }
    }

    AndroidNewSdkDialog dialog = new AndroidNewSdkDialog(null, javaSdks, javaSdks.get(0), Arrays.asList(targetNames),
                                                         newestPlatform != null ? newestPlatform : targetNames[0]);
    if (!dialog.showAndGet()) {
      return false;
    }
    String name = javaSdks.get(dialog.getSelectedJavaSdkIndex());
    Sdk jdk = sdkModel.findSdk(name);
    IAndroidTarget target = targets[dialog.getSelectedTargetIndex()];
    String sdkName = AndroidSdks.getInstance().chooseNameForNewLibrary(target);
    AndroidSdks.getInstance().setUpSdk(sdk, target, sdkName, Arrays.asList(sdks), jdk);

    return true;
  }

  @Override
  @NotNull
  public AdditionalDataConfigurable createAdditionalDataConfigurable(@NotNull SdkModel sdkModel, @NotNull SdkModificator sdkModificator) {
    return new AndroidSdkConfigurable(sdkModel, sdkModificator);
  }

  @Override
  public void saveAdditionalData(@NotNull SdkAdditionalData data, @NotNull Element e) {
    if (data instanceof AndroidSdkAdditionalData) {
      ((AndroidSdkAdditionalData)data).save(e);
    }
  }

  @Override
  @NotNull
  public SdkAdditionalData loadAdditionalData(@NotNull Sdk currentSdk, @NotNull Element additional) {
    return new AndroidSdkAdditionalData(currentSdk, additional);
  }

  @Override
  @NotNull
  public String getPresentableName() {
    return AndroidBundle.message("android.sdk.presentable.name");
  }

  @Override
  @NotNull
  public Icon getIcon() {
    return StudioIcons.Common.ANDROID_HEAD;
  }

  @Override
  @NotNull
  public String getDefaultDocumentationUrl(@NotNull Sdk sdk) {
    return DEFAULT_EXTERNAL_DOCUMENTATION_URL;
  }

  @Override
  public boolean isRootTypeApplicable(@NotNull OrderRootType type) {
    return type == OrderRootType.CLASSES ||
           type == OrderRootType.SOURCES ||
           type == JavadocOrderRootType.getInstance() ||
           type == AnnotationOrderRootType.getInstance();
  }

  @Nullable
  private static Sdk getInternalJavaSdk(@NotNull Sdk sdk) {
    AndroidSdkAdditionalData data = AndroidSdks.getInstance().getAndroidSdkAdditionalData(sdk);
    return data != null ? data.getJavaSdk() : null;
  }

  public static AndroidSdkType getInstance() {
    return SdkType.findInstance(AndroidSdkType.class);
  }

}
