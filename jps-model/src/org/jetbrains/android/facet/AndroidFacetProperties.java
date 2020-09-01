// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.android.facet;

import com.android.AndroidProjectTypes;
import com.android.SdkConstants;
import com.intellij.util.xmlb.annotations.Attribute;
import com.intellij.util.xmlb.annotations.Tag;
import com.intellij.util.xmlb.annotations.XCollection;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
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
  @NonNls public static final String ITEM_ELEMENT = "item";
  @NonNls public static final String ARCHITECTURE_ATTRIBUTE = "architecture";
  @NonNls public static final String URL_ATTRIBUTE = "url";
  @NonNls public static final String TARGET_FILE_NAME_ATTRIBUTE = "targetFileName";

  public String SELECTED_BUILD_VARIANT = "";

  public String ASSEMBLE_TASK_NAME = "";
  public String COMPILE_JAVA_TASK_NAME = "";

  public String ASSEMBLE_TEST_TASK_NAME = "";
  public String COMPILE_JAVA_TEST_TASK_NAME = "";

  @XCollection(propertyElementName = "afterSyncTasks", elementName = "task", valueAttributeName = "")
  public Set<String> AFTER_SYNC_TASK_NAMES = new HashSet<String>();

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

  public boolean USE_CUSTOM_APK_RESOURCE_FOLDER = false;
  public String CUSTOM_APK_RESOURCE_FOLDER = "";

  public boolean USE_CUSTOM_COMPILER_MANIFEST = false;
  public String CUSTOM_COMPILER_MANIFEST = "";

  public String APK_PATH = "";

  public int PROJECT_TYPE = AndroidProjectTypes.PROJECT_TYPE_APP;

  public boolean RUN_PROCESS_RESOURCES_MAVEN_TASK = true;

  public String CUSTOM_DEBUG_KEYSTORE_PATH = "";

  public boolean PACK_TEST_CODE = false;

  public boolean RUN_PROGUARD = false;

  @NonNls public static final String DIRECTORY_FOR_LOGS_NAME = "proguard_logs";
  public String PROGUARD_LOGS_FOLDER_RELATIVE_PATH = "/" + DIRECTORY_FOR_LOGS_NAME;

  public static final String SDK_HOME_MACRO = "%MODULE_SDK_HOME%";
  public static final String PROGUARD_SYSTEM_CFG_FILE_URL =
    "file://" + SDK_HOME_MACRO + "/tools/proguard/proguard-android.txt";
  @XCollection(propertyElementName = "proGuardCfgFiles", elementName = "file", valueAttributeName = "")
  public List<String> myProGuardCfgFiles = Arrays.asList(PROGUARD_SYSTEM_CFG_FILE_URL);

  public boolean USE_CUSTOM_MANIFEST_PACKAGE = false;
  public String CUSTOM_MANIFEST_PACKAGE = "";

  public String ADDITIONAL_PACKAGING_COMMAND_LINE_PARAMETERS = "";

  public String UPDATE_PROPERTY_FILES = "";

  public boolean ENABLE_MANIFEST_MERGING = false;

  public boolean ENABLE_PRE_DEXING = true;

  public boolean COMPILE_CUSTOM_GENERATED_SOURCES = true;

  public boolean ENABLE_SOURCES_AUTOGENERATION = true;

  public boolean ENABLE_MULTI_DEX = false;
  public String MAIN_DEX_LIST = "";
  public boolean MINIMAL_MAIN_DEX = false;

  @NonNls public static final String INCLUDE_ASSETS_FROM_LIBRARIES_ELEMENT_NAME = "includeAssetsFromLibraries";
  @Tag(INCLUDE_ASSETS_FROM_LIBRARIES_ELEMENT_NAME)
  public boolean myIncludeAssetsFromLibraries = false;

  @XCollection(propertyElementName = "resOverlayFolders", elementName = "path", valueAttributeName = "")
  public List<String> RES_OVERLAY_FOLDERS = new ArrayList<>();

  @XCollection(propertyElementName = "additionalNativeLibs")
  public List<AndroidNativeLibDataEntry> myNativeLibs = new ArrayList<>();

  @XCollection(propertyElementName = "notImportedProperties", elementName = "property", valueAttributeName = "")
  public Set<AndroidImportableProperty> myNotImportedProperties = EnumSet.noneOf(AndroidImportableProperty.class);

  @Tag(ITEM_ELEMENT)
  public static class AndroidNativeLibDataEntry {
    @Attribute(ARCHITECTURE_ATTRIBUTE)
    public String myArchitecture;

    @Attribute(URL_ATTRIBUTE)
    public String myUrl;

    @Attribute(TARGET_FILE_NAME_ATTRIBUTE)
    public String myTargetFileName;
  }
}
