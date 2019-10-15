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
package com.android.tools.idea.templates

object TemplateAttributes {
  const val ATTR_IS_NEW_MODULE = "isNewModule"
  const val ATTR_IS_NEW_PROJECT = "isNewProject"
  const val ATTR_IS_LIBRARY_MODULE = "isLibraryProject"
  const val ATTR_IS_LOW_MEMORY = "isLowMemory"
  const val ATTR_ANDROIDX_SUPPORT = "addAndroidXSupport"

  const val ATTR_TARGET_API = "targetApi"
  const val ATTR_TARGET_API_STRING = "targetApiString"
  const val ATTR_MIN_BUILD_API = "minBuildApi"
  const val ATTR_MIN_API = "minApi"
  const val ATTR_MIN_API_LEVEL = "minApiLevel"
  const val ATTR_BUILD_API = "buildApi"
  const val ATTR_BUILD_API_STRING = "buildApiString"
  const val ATTR_BUILD_API_REVISION = "buildApiRevision"
  const val ATTR_BUILD_TOOLS_VERSION = "buildToolsVersion"
  const val ATTR_GRADLE_PLUGIN_VERSION = "gradlePluginVersion"
  const val ATTR_GRADLE_VERSION = "gradleVersion"
  const val ATTR_EXPLICIT_BUILD_TOOLS_VERSION = "explicitBuildToolsVersion"
  const val ATTR_LANGUAGE = "language" // Java vs Kotlin
  const val ATTR_JAVA_VERSION = "javaVersion"
  const val ATTR_KOTLIN_VERSION = "kotlinVersion"
  const val ATTR_KOTLIN_EAP_REPO = "includeKotlinEapRepo"

  const val ATTR_MODULE_NAME = "projectName"
  const val ATTR_MODULE_SIMPLE_NAME = "projectSimpleName" // Same as ATTR_MODULE_NAME but no spaces or other symbols
  const val ATTR_PACKAGE_NAME = "packageName"
  const val ATTR_APPLICATION_PACKAGE = "applicationPackage"
  const val ATTR_SOURCE_PROVIDER_NAME = "sourceProviderName"

  const val ATTR_TOP_OUT = "topOut"
  const val ATTR_PROJECT_OUT = "projectOut" // Module (Project in gradle language) location
  const val ATTR_SRC_OUT = "srcOut"
  const val ATTR_RES_OUT = "resOut"
  const val ATTR_MANIFEST_OUT = "manifestOut"
  const val ATTR_TEST_OUT = "testOut"
  const val ATTR_SRC_DIR = "srcDir"
  const val ATTR_RES_DIR = "resDir"
  const val ATTR_MANIFEST_DIR = "manifestDir"
  const val ATTR_TEST_DIR = "testDir"
  const val ATTR_AIDL_DIR = "aidlDir"
  const val ATTR_AIDL_OUT = "aidlOut"
  const val ATTR_SDK_DIR = "sdkDir"

  const val ATTR_DEBUG_KEYSTORE_SHA1 = "debugKeystoreSha1"

  const val ATTR_INCLUDE_FORM_FACTOR = "included"

  const val ATTR_CPP_FLAGS = "cppFlags"
  const val ATTR_CPP_SUPPORT = "includeCppSupport"

  const val ATTR_IS_DYNAMIC_FEATURE = "isDynamicFeature"
  const val ATTR_DYNAMIC_FEATURE_INSTALL_TIME_DELIVERY = "dynamicFeatureInstallTimeDelivery"
  const val ATTR_DYNAMIC_FEATURE_INSTALL_TIME_WITH_CONDITIONS_DELIVERY = "dynamicFeatureInstallTimeWithConditionsDelivery"
  const val ATTR_DYNAMIC_FEATURE_ON_DEMAND_DELIVERY = "dynamicFeatureOnDemandDelivery"
  const val ATTR_DYNAMIC_FEATURE_DEVICE_FEATURE_LIST = "dynamicFeatureDeviceFeatureList"
  const val ATTR_DYNAMIC_FEATURE_TITLE = "dynamicFeatureTitle"
  const val ATTR_DYNAMIC_FEATURE_ON_DEMAND = "dynamicFeatureOnDemand"
  const val ATTR_DYNAMIC_FEATURE_FUSING = "dynamicFeatureFusing"
  const val ATTR_DYNAMIC_IS_INSTANT_MODULE = "isInstantModule"

  const val ATTR_BASE_FEATURE_NAME = "baseFeatureName"
  const val ATTR_BASE_FEATURE_DIR = "baseFeatureDir"
  const val ATTR_BASE_FEATURE_RES_DIR = "baseFeatureResDir"

  const val ATTR_IS_LAUNCHER = "isLauncher"
  const val ATTR_APP_TITLE = "appTitle"
  const val ATTR_COMPANY_DOMAIN = "companyDomain"
  const val ATTR_CLASS_NAME = "className"

  const val ATTR_THEME_EXISTS = "themeExists"
  const val ATTR_HAS_APPLICATION_THEME = "hasApplicationTheme"
  const val ATTR_APP_THEME = "applicationTheme"
  const val ATTR_APP_THEME_NAME = "name"
  const val ATTR_APP_THEME_EXISTS = "exists"
  const val ATTR_APP_THEME_NO_ACTION_BAR = "NoActionBar"
  const val ATTR_APP_THEME_APP_BAR_OVERLAY = "AppBarOverlay"
  const val ATTR_APP_THEME_POPUP_OVERLAY = "PopupOverlay"
}
