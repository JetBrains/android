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
import com.google.common.collect.Lists;
import com.intellij.CommonBundle;
import com.intellij.openapi.projectRoots.*;
import com.intellij.openapi.projectRoots.impl.JavaDependentSdkType;
import com.intellij.openapi.roots.AnnotationOrderRootType;
import com.intellij.openapi.roots.JavadocOrderRootType;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.ui.Messages;
import icons.AndroidIcons;
import org.jdom.Element;
import org.jetbrains.android.actions.RunAndroidSdkManagerAction;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.io.File;
import java.util.Arrays;
import java.util.List;

import static com.intellij.openapi.util.io.FileUtil.toSystemDependentName;
import static com.intellij.openapi.util.text.StringUtil.isEmpty;
import static com.intellij.openapi.util.text.StringUtil.isNotEmpty;
import static org.jetbrains.android.sdk.AndroidSdkData.getSdkData;
import static org.jetbrains.android.sdk.AndroidSdkUtils.*;

/**
 * @author Eugene.Kudelevsky
 */
public class AndroidSdkType extends JavaDependentSdkType implements JavaSdkType {
  @NonNls public static final String SDK_NAME = "Android SDK";
  @NonNls public static final String DEFAULT_EXTERNAL_DOCUMENTATION_URL = "http://developer.android.com/reference/";

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
  public boolean isValidSdkHome(@Nullable String path) {
    if (isEmpty(path)) {
      return false;
    }
    File sdkPath = new File(toSystemDependentName(path));
    return validateAndroidSdk(sdkPath, false).success;
  }

  /**
   * Indicates whether the given path belongs to a valid Android SDK.
   *
   * @param sdkPath              the given path.
   * @param includePathInMessage indicates whether the given path should be included in the result message.
   * @return the validation result.
   */
  @NotNull
  public static ValidationResult validateAndroidSdk(@Nullable File sdkPath, boolean includePathInMessage) {
    if (sdkPath == null) {
      return ValidationResult.error("");
    }

    String cause = null;
    if (!sdkPath.isDirectory()) {
      cause = "does not belong to a directory.";
    }
    else if (!sdkPath.canRead()) {
      cause = "is not readable.";
    }
    else if (!sdkPath.canWrite()) {
      cause = "is not writable.";
    }
    if (isNotEmpty(cause)) {
      String message;
      if (includePathInMessage) {
        message = String.format("The path\n'%1$s'\n%2$s", sdkPath.getPath(), cause);
      }
      else {
        message = String.format("The path %1$s", cause);
      }
      return ValidationResult.error(message);
    }

    File platformsDirPath = new File(sdkPath, SdkConstants.FD_PLATFORMS);
    if (!platformsDirPath.isDirectory()) {
      String message;
      if (includePathInMessage) {
        message = String.format("The SDK at\n'%1$s'\ndoes not contain any platforms.", sdkPath.getPath());
      }
      else {
        message = "SDK does not contain any platforms.";
      }
      return ValidationResult.error(message);
    }

    return ValidationResult.SUCCESS;
  }

  @Override
  public String getVersionString(@NotNull Sdk sdk) {
    Sdk internalJavaSdk = getInternalJavaSdk(sdk);
    return internalJavaSdk != null ? internalJavaSdk.getVersionString() : null;
  }

  @Override
  @NotNull
  public String suggestSdkName(String currentSdkName, String sdkHome) {
    return SDK_NAME;
  }

  @Override
  public boolean setupSdkPaths(@NotNull Sdk sdk, @NotNull SdkModel sdkModel) {
    final List<String> javaSdks = Lists.newArrayList();
    final Sdk[] sdks = sdkModel.getSdks();
    for (Sdk jdk : sdks) {
      if (Jdks.isApplicableJdk(jdk)) {
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
    String sdkName = chooseNameForNewLibrary(target);
    setUpSdk(sdk, sdkName, sdks, target, jdk, true);

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
    return AndroidIcons.Android;
  }

  @Override
  @NotNull
  public Icon getIconForAddAction() {
    return getIcon();
  }

  @Override
  @NotNull
  public String getDefaultDocumentationUrl(@NotNull Sdk sdk) {
    return DEFAULT_EXTERNAL_DOCUMENTATION_URL;
  }

  @Override
  public boolean isRootTypeApplicable(OrderRootType type) {
    return type == OrderRootType.CLASSES ||
           type == OrderRootType.SOURCES ||
           type == JavadocOrderRootType.getInstance() ||
           type == AnnotationOrderRootType.getInstance();
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

  public static class ValidationResult {
    @NotNull public static final ValidationResult SUCCESS = new ValidationResult(true, null);

    public final boolean success;
    @Nullable public final String message;

    @NotNull
    static ValidationResult error(@NotNull String message) {
      return new ValidationResult(false, message);
    }

    private ValidationResult(boolean success, @Nullable String message) {
      this.success = success;
      this.message = message;
    }
  }
}
