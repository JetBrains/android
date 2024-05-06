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
package org.jetbrains.android.facet;

import com.android.AndroidProjectTypes;
import com.android.SdkConstants;
import com.intellij.util.xmlb.annotations.XCollection;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.jetbrains.annotations.NonNls;

/**
 * Android-specific information saved in the IML file corresponding to an Android module.
 *
 * <p>These objects are serialized to XML by {@link org.jetbrains.android.facet.AndroidFacetConfiguration} using {@link
 * com.intellij.util.xmlb.XmlSerializer}.
 *
 * <p>Avoid using instances of this class if at all possible. This information should be provided by
 * {@link com.android.tools.idea.projectsystem.AndroidProjectSystem} and it is up to the project system used by the project to choose how
 * this information is obtained and persisted.
 */
public class AndroidFacetProperties {

  public static final String PATH_LIST_SEPARATOR_IN_FACET_CONFIGURATION = ";";

  public String SELECTED_BUILD_VARIANT = "";

  /**
   * False when the Android project is Gradle-based, true otherwise.
   *
   * @deprecated Please do not use directly and do not configure in tests.
   * See {@link com.android.tools.idea.project.AndroidProjectInfo#requiresAndroidModel} and
   * {@link com.android.tools.idea.model.AndroidModel#isRequired}.
   * <p>
   * If you are looking for a way to configure a lightweight project
   * with Android model, consider using
   * {@link com.android.tools.idea.testing.AndroidProjectRule.Companion#withAndroidModel}
   */
  @Deprecated
  public boolean ALLOW_USER_CONFIGURATION = true;

  public String GEN_FOLDER_RELATIVE_PATH_APT = "/" + SdkConstants.FD_GEN_SOURCES;
  public String GEN_FOLDER_RELATIVE_PATH_AIDL = "/" + SdkConstants.FD_GEN_SOURCES;

  public String MANIFEST_FILE_RELATIVE_PATH = "/" + SdkConstants.FN_ANDROID_MANIFEST_XML;

  public String RES_FOLDER_RELATIVE_PATH = "/" + SdkConstants.FD_RES;

  /**
   * Urls of the res folders provided by the current source providers and of the generated res folders from the main artifact
   * separated by {@link PATH_LIST_SEPARATOR_IN_FACET_CONFIGURATION} (";").
   */
  // TODO(b/141909881): Consider renaming and breaking it up into smaller properties.
  public String RES_FOLDERS_RELATIVE_PATH;

  /**
   * Urls of the res folders provided by the current test source providers and of the generated res folders from the androidTest artifact
   * separated by {@link PATH_LIST_SEPARATOR_IN_FACET_CONFIGURATION} (";").
   */
  // TODO(b/141909881): Consider renaming and breaking it up into smaller properties.
  public String TEST_RES_FOLDERS_RELATIVE_PATH;

  public String ASSETS_FOLDER_RELATIVE_PATH = "/" + SdkConstants.FD_ASSETS;
  public String LIBS_FOLDER_RELATIVE_PATH = "/" + SdkConstants.FD_NATIVE_LIBS;

  public String APK_PATH = "";

  public int PROJECT_TYPE = AndroidProjectTypes.PROJECT_TYPE_APP;

  public String CUSTOM_DEBUG_KEYSTORE_PATH = "";

  public boolean PACK_TEST_CODE = false;

  public boolean RUN_PROGUARD = false;

  @NonNls public static final String DIRECTORY_FOR_LOGS_NAME = "proguard_logs";
  public String PROGUARD_LOGS_FOLDER_RELATIVE_PATH = "/" + DIRECTORY_FOR_LOGS_NAME;

  public static final String SDK_HOME_MACRO = "%MODULE_SDK_HOME%";
  public static final String PROGUARD_SYSTEM_CFG_FILE_URL =
    "file://" + SDK_HOME_MACRO + "/tools/proguard/proguard-android.txt";
  @XCollection(propertyElementName = "proGuardCfgFiles", elementName = "file", valueAttributeName = "")
  public List<String> myProGuardCfgFiles = Collections.singletonList(PROGUARD_SYSTEM_CFG_FILE_URL);

  public boolean USE_CUSTOM_MANIFEST_PACKAGE = false;
  public String CUSTOM_MANIFEST_PACKAGE = "";

  @XCollection(propertyElementName = "resOverlayFolders", elementName = "path", valueAttributeName = "")
  public List<String> RES_OVERLAY_FOLDERS = new ArrayList<>();
}
