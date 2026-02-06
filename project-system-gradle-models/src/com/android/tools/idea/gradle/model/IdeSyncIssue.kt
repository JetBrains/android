/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.gradle.model

/**
 * Class representing a sync issue. The goal is to make these issues not fail the sync but instead report them at the end of a successful
 * sync.
 */
interface IdeSyncIssue {
  /** Returns the severity of the issue. */
  val severity: Int

  /** Returns the type of the issue. */
  val type: Int

  /**
   * Returns the data of the issue.
   *
   * This is a machine-readable string used by the IDE for known issue types.
   */
  val data: String?

  /**
   * Returns the a user-readable message for the issue.
   *
   * This is used by IDEs that do not recognize the issue type (ie older IDE released before the type was added to the plugin).
   */
  val message: String

  /**
   * Returns the a user-readable nulti-line message for the issue.
   *
   * This is an optional extension of [message]
   */
  val multiLineMessage: List<String>?

  companion object {
    const val SEVERITY_WARNING = 1
    const val SEVERITY_ERROR = 2

    /** Generic error with no data payload, and no expected quick fix in IDE. */
    const val TYPE_GENERIC = 0

    /** Data is expiration data. */
    const val TYPE_PLUGIN_OBSOLETE = 1

    /** Data is dependency coordinate. */
    const val TYPE_UNRESOLVED_DEPENDENCY = 2

    /** Data is dependency coordinate. */
    const val TYPE_DEPENDENCY_IS_APK = 3

    /** Data is dependency coordinate. */
    const val TYPE_DEPENDENCY_IS_APKLIB = 4

    /** Data is local file. */
    const val TYPE_NON_JAR_LOCAL_DEP = 5

    /** Data is dependency coordinate/path. */
    const val TYPE_NON_JAR_PACKAGE_DEP = 6

    /** Data is dependency coordinate/path. */
    const val TYPE_NON_JAR_PROVIDED_DEP = 7

    /** Data is dependency coordinate/path. */
    const val TYPE_JAR_DEPEND_ON_AAR = 8

    /** Mismatch dependency version between tested and test app. Data is dep coordinate without the version (groupId:artifactId) */
    const val TYPE_MISMATCH_DEP = 9

    /** Data is dependency coordinate. */
    const val TYPE_OPTIONAL_LIB_NOT_FOUND = 10

    /** Data is variant name. */
    const val TYPE_JACK_IS_NOT_SUPPORTED = 11

    /** Data is the min version of Gradle. */
    const val TYPE_GRADLE_TOO_OLD = 12

    /** Data is the required min build tools version, parsable by Revision. */
    const val TYPE_BUILD_TOOLS_TOO_LOW = 13

    /** Found dependency that's the maven published android.jar. Data is the maven artifact coordinates. */
    const val TYPE_DEPENDENCY_MAVEN_ANDROID = 14

    /** Found dependency that is known to be inside android.jar. Data is maven artifact coordinates. */
    const val TYPE_DEPENDENCY_INTERNAL_CONFLICT = 15

    /** Errors configuring NativeConfigValues for individual individual variants */
    const val TYPE_EXTERNAL_NATIVE_BUILD_CONFIGURATION = 16

    /**
     * Errors configuring NativeConfigValues. There was a process exception. Data contains STDERR which should be interpreted by Android
     * Studio.
     */
    const val TYPE_EXTERNAL_NATIVE_BUILD_PROCESS_EXCEPTION = 17

    /** Cannot use Java 8 Language features without Jack. */
    const val TYPE_JACK_REQUIRED_FOR_JAVA_8_LANGUAGE_FEATURES = 18

    /** A wearApp configuration was resolved and found more than one apk. Data is the configuration name. */
    const val TYPE_DEPENDENCY_WEAR_APK_TOO_MANY = 19

    /** A wearApp configuration was resolved and found an apk even though unbundled mode is on. */
    const val TYPE_DEPENDENCY_WEAR_APK_WITH_UNBUNDLED = 20

    /** Data is dependency coordinate/path. */
    @Deprecated("") const val TYPE_JAR_DEPEND_ON_ATOM = 21

    /** Data is dependency coordinate/path. */
    @Deprecated("") const val TYPE_AAR_DEPEND_ON_ATOM = 22

    /** Data is dependency coordinate/path. */
    @Deprecated("") const val TYPE_ATOM_DEPENDENCY_PROVIDED = 23

    /**
     * Indicates that a required SDK package was not installed. The data field contains the sdklib package ID of the missing package that
     * the user should install.
     */
    const val TYPE_MISSING_SDK_PACKAGE = 24

    /** Indicates that the plugin requires a newer version of studio. Minimum version is passed in the data. */
    const val TYPE_STUDIO_TOO_OLD = 25

    /** Indicates that the module contains flavors but that no dimensions have been named. data is empty. */
    const val TYPE_UNNAMED_FLAVOR_DIMENSION = 26

    /** An incompatible plugin is used. */
    const val TYPE_INCOMPATIBLE_PLUGIN = 27

    /**
     * Indicates that the project uses a deprecated DSL. The data paylod is dslElement::removeTarget where removal target is the version of
     * the plugin where the dsl element is targeted to be removed.
     */
    const val TYPE_DEPRECATED_DSL = 28

    /**
     * Indicates that the project uses a deprecated configuration.
     *
     * This type is now replaced with TYPE_USING_DEPRECATED_CONFIGURATION (see http://issuetracker.google.com/138278313).
     */
    @Deprecated("") const val TYPE_DEPRECATED_CONFIGURATION = 29

    /**
     * Indicates that the project uses a deprecated DSL, the Data payload is a URL giving context to the user on how to remove the
     * deprecated element or value.
     *
     * This type is now replaced with TYPE_USING_DEPRECATED_DSL_VALUE (see http://issuetracker.google.com/138278313).
     */
    @Deprecated("") const val TYPE_DEPRECATED_DSL_VALUE = 29

    /** Indicates that the project contains the min sdk in the android manifest file. */
    const val TYPE_MIN_SDK_VERSION_IN_MANIFEST = 30

    /** Indicates that the project contains the target sdk in the android manifest file. */
    const val TYPE_TARGET_SDK_VERSION_IN_MANIFEST = 31

    /** Indicated that an experimental gradle project option is used. */
    const val TYPE_UNSUPPORTED_PROJECT_OPTION_USE = 32

    /** Indicates that building the configuration rules for this project requires parsing the manifest file. */
    const val TYPE_MANIFEST_PARSED_DURING_CONFIGURATION = 33

    /**
     * Indicates that the version of a third-party Gradle plugin (not the Android Gradle plugin) is not supported and needs to be updated.
     */
    const val TYPE_THIRD_PARTY_GRADLE_PLUGIN_TOO_OLD = 34

    /**
     * Indicates that the signing configuration is declared in the dynamic-feature gradle file. This should only be declared in the
     * application module, as dynamic-features use the base module's signing configuration, and this will be ignored.
     */
    const val TYPE_SIGNING_CONFIG_DECLARED_IN_DYNAMIC_FEATURE = 35

    /**
     * Indicates that the SDK is missing or invalid, this can either be set in the ANDROID_SDK_ROOT environment variable or the projects
     * local.properties files.
     */
    const val TYPE_SDK_NOT_SET = 36

    /** Indicates that the user has specified multiple default build types */
    const val TYPE_AMBIGUOUS_BUILD_TYPE_DEFAULT = 37

    /** Indicates that the user has specified multiple default product flavors */
    const val TYPE_AMBIGUOUS_PRODUCT_FLAVOR_DEFAULT = 38

    /** Indicates that the compileSdkVersion is missing */
    const val TYPE_COMPILE_SDK_VERSION_NOT_SET = 39

    /** Indicates that the `android.useAndroidX` property must be enabled but is currently disabled. */
    const val TYPE_ANDROID_X_PROPERTY_NOT_ENABLED = 40

    /** Indicates that the project uses a deprecated configuration. */
    const val TYPE_USING_DEPRECATED_CONFIGURATION = 41

    /**
     * Indicates that the project uses a deprecated DSL, the Data payload is a URL giving context to the user on how to remove the
     * deprecated element or value.
     */
    const val TYPE_USING_DEPRECATED_DSL_VALUE = 42

    /** The user or a plugin has tried to mutate a DSL value after it has been locked. */
    const val TYPE_EDIT_LOCKED_DSL_VALUE = 43

    /** Indicates that a manifest is missing */
    const val TYPE_MISSING_ANDROID_MANIFEST = 44

    /** JCenter Maven is deprecated and it should not be used in build scripts. */
    const val TYPE_JCENTER_IS_DEPRECATED = 45

    /** Java version used by AGP is too low. */
    const val TYPE_AGP_USED_JAVA_VERSION_TOO_LOW = 46

    /**
     * Indicates that the project uses a compile SDK version that's newer than the highest version that the build system has been tested
     * with.
     *
     * The data is the maximum supported compile SDK version for this version of the Android Gradle Plugin.
     */
    const val TYPE_COMPILE_SDK_VERSION_TOO_HIGH = 47

    /** compileSdk is lower than required for an AGP build. */
    const val TYPE_COMPILE_SDK_VERSION_TOO_LOW = 48

    /** Trying to read/write to a variant api property that will not be used as the feature using it is disabled. */
    const val TYPE_ACCESSING_DISABLED_FEATURE_VARIANT_API = 49

    /** Using the variant API to set the application ID to a dynamic value */
    const val TYPE_APPLICATION_ID_MUST_NOT_BE_DYNAMIC = 50

    /** Using a removed API (which is still present to allow for sync to succeed). */
    const val TYPE_REMOVED_API = 51

    /** Using an empty flavor dimension. */
    const val TYPE_EMPTY_FLAVOR_DIMENSION = 52

    /** A sync issue type for exceptions that were converted to a sync issue. */
    const val TYPE_EXCEPTION = 53

    /** Indicates that the namespace is missing */
    const val TYPE_NAMESPACE_NOT_SET = 54

    // Inconsistent usage of build feature setting with regards to other DSL Settings or plugins
    // applied
    const val TYPE_INCONSISTENT_BUILD_FEATURE_SETTING = 55

    /**
     * Missing Compose Compiler Gradle plugin when compose is enabled and Kotlin version is 2.0 or higher. Data is the Kotlin version used.
     */
    const val TYPE_MISSING_COMPOSE_COMPILER_GRADLE_PLUGIN = 56

    /** Disabling the library constraints results in better sync performance, this will be surfaced to the user via this issue. */
    const val TYPE_LIBRARY_CONSTRAINTS_SHOULD_BE_DISABLED = 57

    /** Project uses RenderScript which is not supported on Riscv. */
    const val TYPE_RENDERSCRIPT_NOT_SUPPORTED_ON_RISCV = 58

    /** Using kotlin.sourceSets DSL to add Kotlin sources is not allowed with built-in Kotlin. */
    const val TYPE_KOTLIN_SOURCE_SET_NOT_ALLOWED = 59

    /** Found non-apk file in a configuration that should only contain apks. */
    const val TYPE_NON_APK_RUNTIME_DEP = 60

    /** Unable to find connectedCheck task name for a component. */
    const val TYPE_CONNECTED_CHECK_TASK_NOT_FOUND = 61

    /** Failed to parse XML in manifest. */
    const val TYPE_MANIFEST_PARSE_FAILED = 62

    /** User is using me.tatarka.retrolambda which is deprecated. */
    const val TYPE_RETROLAMBDA_USED = 63

    /** User is using FlatDirectoryArtifactRepository which is discouraged. */
    const val TYPE_FLAT_DIR_REPOSITORY_USED = 64

    /** Access to deprecated legacy model requires compatibility mode. */
    const val TYPE_ACCESSING_DEPRECATED_LEGACY_MODEL = 65

    /** Property cannot be set on a mergedFlavor directly. */
    const val TYPE_CANNOT_SET_ON_MERGED_FLAVOR = 66

    /** Invalid value for compileSdkPreview or compileSdkMinor. */
    const val TYPE_COMPILE_SDK_PREVIEW_INVALID = 67

    /** Invalid value for namespace. */
    const val TYPE_NAMESPACE_INVALID = 68

    /** Invalid Proguard file provided. */
    const val TYPE_PROGUARD_FILE_INVALID = 69

    /** targetSdk is set in testOptions for non-library module. */
    const val TYPE_TEST_OPTIONS_TARGET_SDK_INVALID = 70

    /** KGP is loaded in a different classloader than AGP. */
    const val TYPE_KGP_LOADED_IN_DIFFERENT_CLASSLOADER = 71

    /** Selected execution profile does not exist or none selected. */
    const val TYPE_UNSUPPORTED_EXECUTION_PROFILE = 72

    /** Invalid value for lint version override. */
    const val TYPE_LINT_VERSION_OVERRIDE_INVALID = 73

    /** Namespace and testNamespace have the same value. */
    const val TYPE_NAMESPACE_CONFLICT = 74

    /** Invalid value for version code. */
    const val TYPE_INVALID_VERSION_CODE = 75

    /** Pure splits are not supported. */
    const val TYPE_PURE_SPLITS_NOT_SUPPORTED = 76

    /** Minify is enabled in dynamic feature module. */
    const val TYPE_MINIFY_ENABLED_IN_DYNAMIC_FEATURE = 77

    /** ABI filters are declared in dynamic feature module. */
    const val TYPE_ABI_FILTERS_IN_DYNAMIC_FEATURE = 78

    /** Invalid publishing configuration. */
    const val TYPE_INVALID_PUBLISHING_CONFIG = 79

    /** Resource shrinking is not supported for this component type. */
    const val TYPE_RESOURCE_SHRINK_NOT_SUPPORTED = 80

    /** resValue is being replaced. */
    const val TYPE_RES_VALUE_REPLACED = 81

    /** BuildConfig is used when the feature is disabled. */
    const val TYPE_BUILD_CONFIG_USED_WHEN_DISABLED = 82

    /** resource values are used when the feature is disabled. */
    const val TYPE_RES_VALUES_USED_WHEN_DISABLED = 83

    /** Unrecognized SourceSet name. */
    const val TYPE_UNRECOGNIZED_SOURCE_SET = 84

    /** applicationIdSuffix is ignored because applicationId is null. */
    const val TYPE_APPLICATION_ID_SUFFIX_IGNORED = 85

    /** applicationId is set in library project. */
    const val TYPE_APPLICATION_ID_NOT_ALLOWED_IN_LIBRARY = 86

    /** applicationIdSuffix is set in library project. */
    const val TYPE_APPLICATION_ID_SUFFIX_NOT_ALLOWED_IN_LIBRARY = 87

    /** Conflicting ABI configuration. */
    const val TYPE_CONFLICTING_ABI_CONFIG = 88

    /** Cannot build selected target ABI. */
    const val TYPE_CANNOT_BUILD_SELECTED_TARGET_ABI = 89

    /** BuildType is both debuggable and has minifyEnabled set to true. */
    const val TYPE_DEBUGGABLE_AND_MINIFIED_ENABLED = 90

    /** minSdkVersion is greater than targetSdkVersion. */
    const val TYPE_MIN_SDK_VERSION_GREATER_THAN_TARGET_SDK = 91

    /** Test suite ignored because variant does not support it. */
    const val TYPE_TEST_SUITE_IGNORED = 92

    /** Java 9+ compilation requires compileSdkVersion 30 or above. */
    const val TYPE_JAVA9_COMPILATION_REQUIRES_COMPILE_SDK30 = 93

    /** '--release' option for JavaCompile is not supported. */
    const val TYPE_JAVA_COMPILE_RELEASE_OPTION_NOT_SUPPORTED = 94

    /** Java compiler has deprecated or removed support for source/target version. */
    const val TYPE_JAVA_COMPILE_DEPRECATED_SOURCE_TARGET = 95

    /** Problems found when resolving SDK location. */
    const val TYPE_SDK_RESOLUTION_WARNING = 96

    /** Unable to find matching projects for Asset Packs. */
    const val TYPE_ASSET_PACK_PROJECT_NOT_FOUND = 97

    /** R8 version mismatch. */
    const val TYPE_R8_VERSION_MISMATCH = 98

    /** R8 gradual API flag required. */
    const val TYPE_R8_GRADUAL_API_FLAG_REQUIRED = 99

    /** Unable to find matching projects for Dynamic Features. */
    const val TYPE_DYNAMIC_FEATURE_PROJECT_NOT_FOUND = 100

    /** Fused Library Plugin is using Publication Only Mode. */
    const val TYPE_FUSED_LIBRARY_PUBLICATION_ONLY_MODE = 101

    /** Invalid asset pack bundle configuration. */
    const val TYPE_ASSET_PACK_BUNDLE_INVALID_CONFIG = 102

    /** Test suite support is experimental. */
    const val TYPE_TEST_SUITE_SUPPORT_EXPERIMENTAL = 103

    /** lint.targetSdk is smaller than android.targetSdk. */
    const val TYPE_LINT_TARGET_SDK_LESS_THAN_ANDROID_TARGET_SDK = 104

    /** Access to deprecated legacy API requires compatibility mode. */
    const val TYPE_ACCESS_TO_DEPRECATED_LEGACY_API_REQUIRES_COMPATIBILITY_MODE = 105

    /** Unknown SourceKind value. */
    const val TYPE_UNKNOWN_SOURCE_KIND = 106

    /** variant.getApplicationId() is not supported by dynamic-feature plugins. */
    const val TYPE_GET_APPLICATION_ID_NOT_SUPPORTED_IN_DYNAMIC_FEATURE = 107

    /** aidl support is disabled via buildFeatures. */
    const val TYPE_AIDL_DISABLED_VIA_BUILD_FEATURES = 108

    /** renderscript support is disabled via buildFeatures. */
    const val TYPE_RENDERSCRIPT_DISABLED_VIA_BUILD_FEATURES = 109

    /** android.dataBinding.addKtx has no effect. */
    const val TYPE_DATABINDING_KTX_NO_EFFECT = 110

    /** Data Binding annotation processor version mismatch. */
    const val TYPE_DATABINDING_ANNOTATION_PROCESSOR_VERSION_MISMATCH = 111

    /** Multidex library is not needed. */
    const val TYPE_MULTIDEX_NOT_NEEDED = 112

    /** Relative path is not supported in outputFileName. */
    const val TYPE_RELATIVE_PATH_NOT_SUPPORTED_IN_OUTPUT_FILE_NAME = 113

    /** Core library desugaring requires D8 or R8. */
    const val TYPE_CORE_LIBRARY_DESUGARING_REQUIRES_D8_OR_R8 = 114

    /** Core library desugaring requires multidex. */
    const val TYPE_CORE_LIBRARY_DESUGARING_REQUIRES_MULTIDEX = 115

    /** Default Proguard file should not be specified in non-base module. */
    const val TYPE_DEFAULT_PROGUARD_FILE_IN_NON_BASE_MODULE = 116

    /** Default Proguard file should not be used as consumer configuration file. */
    const val TYPE_DEFAULT_PROGUARD_FILE_AS_CONSUMER_FILE = 117

    /** Resource shrinking requires code shrinking to be turned on. */
    const val TYPE_RESOURCE_SHRINK_REQUIRES_CODE_SHRINK = 118

    /** Multiple identical calls to Android Components API is not supported. */
    const val TYPE_MULTIPLE_IDENTICAL_CALLS_TO_ANDROID_COMPONENTS_API = 119

    /** Proguard android-optimize.txt is disallowed. */
    const val TYPE_PROGUARD_ANDROID_OPTIMIZE_TXT_DISALLOWED = 120

    /** Unknown Proguard file. */
    const val TYPE_UNKNOWN_PROGUARD_FILE = 121

    /** Variant can only have debuggable or profileable enabled. */
    const val TYPE_DEBUGGABLE_AND_PROFILEABLE_ENABLED = 122

    /** androidTest test suite not defined for this variant. */
    const val TYPE_ANDROID_TEST_NOT_DEFINED = 123

    /** Native multidex is always used for dynamic features. */
    const val TYPE_DYNAMIC_FEATURE_MULTIDEX_SET_IN_DSL = 124

    // NOTE: When adding a new type here, increment the index by 1. This index may not be consistent
    // with the corresponding value in studio_stats.proto (e.g., it could be lower by 1), because of
    // an indexing issue in the past (see http://issuetracker.google.com/138278313).
  }
}
