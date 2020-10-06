load("//tools/base/bazel:bazel.bzl", "iml_module")
load("//tools/adt/idea/studio:studio.bzl", "studio_plugin", "android_studio")

studio_plugin(
    name = "intellij.android.plugin",
    directory = "android",
    modules = {
        "adt-ui.jar": [
            "//tools/adt/idea/adt-ui-model:intellij.android.adt.ui.model",
            "//tools/adt/idea/adt-ui:intellij.android.adt.ui",
        ],
        "android-common.jar": [
            "//tools/adt/idea/android-common:intellij.android.common",
        ],
        "android-extensions-ide.jar": [
            "//tools/adt/idea/android-kotlin:intellij.android.kotlin.extensions",
        ],
        "android-kotlin.jar": [
            "//tools/adt/idea/android-kotlin:intellij.android.kotlin.idea",
            "//tools/adt/idea/android-kotlin:intellij.android.kotlin.output.parser",
        ],
        "android-profilers.jar": [
            "//tools/adt/idea/profilers-atrace:intellij.android.profilers.atrace",
            "//tools/adt/idea/profilers-ui:intellij.android.profilers.ui",
            "//tools/adt/idea/profilers:intellij.android.profilers",
            "//tools/adt/idea/transport-database:intellij.android.transportDatabase",
        ],
        "android-rt.jar": [
            "//tools/adt/idea/rt:intellij.android.rt",
        ],
        "android.jar": [
            "//tools/adt/idea/analytics:analytics",
            "//tools/adt/idea/android-layout-inspector:android-layout-inspector",
            "//tools/base/flags:studio.android.sdktools.flags",
            "//tools/adt/idea/assistant:assistant",
            "//tools/adt/idea/connection-assistant:connection-assistant",
            "//tools/adt/idea/android-adb:intellij.android.adb",
            "//tools/adt/idea/apkanalyzer:intellij.android.apkanalyzer",
            "//tools/adt/idea/app-inspection/api:intellij.android.app-inspection.api",
            "//tools/adt/idea/app-inspection/ide:intellij.android.app-inspection.ide",
            "//tools/adt/idea/app-inspection/inspector/api:intellij.android.app-inspection.inspector.api",
            "//tools/adt/idea/app-inspection/inspector/ide:intellij.android.app-inspection.inspector.ide",
            "//tools/adt/idea/app-inspection/inspectors/workmanager/ide:intellij.android.app-inspection.inspectors.workmanager.ide",
            "//tools/adt/idea/app-inspection/inspectors/workmanager/model:intellij.android.app-inspection.inspectors.workmanager.model",
            "//tools/adt/idea/app-inspection/inspectors/workmanager/view:intellij.android.app-inspection.inspectors.workmanager.view",
            "//tools/adt/idea/build-attribution:intellij.android.build-attribution",
            "//tools/adt/idea/compose-designer:intellij.android.compose-designer",
            "//tools/adt/idea/android:intellij.android.core",
            "//tools/adt/idea/dagger:intellij.android.dagger",
            "//tools/adt/idea/databinding:intellij.android.databinding",
            "//tools/adt/idea/android-debuggers:intellij.android.debuggers",
            "//tools/adt/idea/deploy:intellij.android.deploy",
            "//tools/adt/idea/designer/customview:intellij.android.designer.customview",
            "//tools/adt/idea/designer:intellij.android.designer",
            "//tools/adt/idea/emulator:intellij.android.emulator",
            "//tools/adt/idea/gradle-tooling/studio-gradle-tooling-api:intellij.android.gradle-tooling.api",
            "//tools/adt/idea/gradle-tooling/studio-gradle-tooling-impl:intellij.android.gradle-tooling.impl",
            "//tools/adt/idea/gradle-dsl:intellij.android.gradle.dsl",
            "//tools/adt/idea/android-lang-databinding:intellij.android.lang-databinding",
            "//tools/adt/idea/android-lang:intellij.android.lang",
            "//tools/adt/idea/layout-inspector:intellij.android.layout-inspector",
            "//tools/adt/idea/layout-ui:intellij.android.layout-ui",
            "//tools/adt/idea/mlkit:intellij.android.mlkit",
            "//tools/adt/idea/nav/editor:intellij.android.nav.editor",
            "//tools/adt/idea/nav/safeargs:intellij.android.nav.safeargs",
            "//tools/adt/idea/android-npw:intellij.android.newProjectWizard",
            "//tools/adt/idea/observable-ui:intellij.android.observable.ui",
            "//tools/adt/idea/observable:intellij.android.observable",
            "//tools/adt/idea/android-plugin:intellij.android.plugin",
            "//tools/adt/idea/profilers-android:intellij.android.profilersAndroid",
            "//tools/adt/idea/project-system-gradle-psd:intellij.android.projectSystem.gradle.psd",
            "//tools/adt/idea/project-system-gradle-sync:intellij.android.projectSystem.gradle.sync",
            "//tools/adt/idea/project-system-gradle:intellij.android.projectSystem.gradle",
            "//tools/adt/idea/project-system:intellij.android.projectSystem",
            "//tools/adt/idea/resources-base:intellij.android.resources-base",
            "//tools/adt/idea/room:intellij.android.room",
            "//tools/adt/idea/sdk-updates:intellij.android.sdkUpdates",
            "//tools/adt/idea/android-test-retention:intellij.android.testRetention",
            "//tools/adt/idea/android-transport:intellij.android.transport",
            "//tools/adt/idea/wizard-model:intellij.android.wizard.model",
            "//tools/adt/idea/wizard:intellij.android.wizard",
            "//tools/adt/idea/native-symbolizer:native-symbolizer",
            "//tools/adt/idea/whats-new-assistant:whats-new-assistant",
        ],
        "artwork.jar": [
            "//tools/adt/idea/artwork:intellij.android.artwork",
        ],
        "repository.jar": [
            "//tools/base/repository:studio.android.sdktools.repository",
        ],
        "build-common.jar": [
            "//tools/adt/idea/build-common:intellij.android.buildCommon",
        ],
        "data-binding.jar": [
            "//tools/data-binding:studio.baseLibrary",
            "//tools/data-binding:studio.baseLibrarySupport",
            "//tools/data-binding:studio.compiler",
            "//tools/data-binding:studio.compilerCommon",
        ],
        "game-tools.jar": [
            "//tools/vendor/google/game-tools/main:android.game-tools.main",
        ],
        "jps/android-jps-plugin.jar": [
            "//tools/adt/idea/jps-plugin:intellij.android.jps",
        ],
        "layoutlib-loader.jar": [
            "//tools/adt/idea/layoutlib-loader:intellij.android.layoutlib-loader",
        ],
        "lint-ide.jar": [
            "//tools/adt/idea/lint:intellij.lint",
        ],
        "manifest-merger.jar": [
            "//tools/base/build-system:studio.android.sdktools.manifest-merger",
        ],
        "pixelprobe.jar": [
            "//tools/base/chunkio:studio.android.sdktools.chunkio",
            "//tools/base/pixelprobe:studio.android.sdktools.pixelprobe",
        ],
        "sdk-common.jar": [
            "//tools/base/sdk-common:studio.android.sdktools.sdk-common",
        ],
        "sdk-tools.jar": [
            "//tools/base/apkparser/analyzer:studio.android.sdktools.analyzer",
            "//tools/base/annotations:studio.android.sdktools.android-annotations",
            "//tools/base/apkparser:studio.android.sdktools.binary-resources",
            "//tools/base/build-system/builder-model:studio.android.sdktools.builder-model",
            "//tools/base/build-system/builder-test-api:studio.android.sdktools.builder-test-api",
            "//tools/base/ddmlib:studio.android.sdktools.ddmlib",
            "//tools/base/deploy/deployer:studio.android.sdktools.deployer",
            "//tools/base/draw9patch:studio.android.sdktools.draw9patch",
            "//tools/base/device_validator:studio.android.sdktools.dvlib",
            "//tools/base/layoutinspector:studio.android.sdktools.layoutinspector",
            "//tools/base/lint:studio.android.sdktools.lint-api",
            "//tools/base/lint:studio.android.sdktools.lint-checks",
            "//tools/base/lint:studio.android.sdktools.lint-model",
            "//tools/base/mlkit-common:studio.android.sdktools.mlkit-common",
            "//tools/base/ninepatch:studio.android.sdktools.ninepatch",
            "//tools/base/perflib:studio.android.sdktools.perflib",
            "//tools/base/tracer:studio.android.sdktools.tracer",
            "//tools/base/zipflinger:studio.android.sdktools.zipflinger",
            "//tools/base/usb-devices:studio.usb-devices",
        ],
        "sdklib.jar": [
            "//tools/base/sdklib:studio.android.sdktools.sdklib",
        ],
        "wizard-template.jar": [
            "//tools/base/wizard/template-impl:studio.intellij.android.wizardTemplate.impl",
            "//tools/base/wizard/template-plugin:studio.intellij.android.wizardTemplate.plugin",
        ],
    },
    resources = {
        "lib": [
            "//tools/adt/idea/android/lib:unb.asm-tools_files",
            "//tools/adt/idea/android/lib:bundled-libs",
            "//tools/adt/idea/artwork:device-art-resources-bundle",  # duplicated in android.jar
            "//tools/adt/idea/android/lib:sample-data-bundle",
            "//tools/adt/idea/android/annotations:androidAnnotations",
            "//prebuilts/studio/layoutlib:layoutlib",
        ],
        "resources": [
            "//tools/base/profiler/transform:profilers-transform",
            "//tools/base/profiler/app:perfa",
            "//tools/base/profiler/app:perfa_okhttp",
            "//tools/base/tracer:trace_agent.jar",  # TODO(b/149320690): remove in 4.1 final release.
            "//tools/base/transport:transport-bundle",
            "//prebuilts/tools:simpleperf-bundle",
            "//prebuilts/tools/common/perfetto:perfetto-bundle",
            "//prebuilts/tools/common/app-inspection/androidx/sqlite:sqlite-inspection-bundle",
            "//prebuilts/tools/common/app-inspection/androidx/work:workmanager-inspection-bundle",
            "//tools/base/deploy/installer:android-installer-bundle",
            "//tools/adt/idea/android:asset-studio-bundle",
            "//prebuilts/tools/common/trace-processor-daemon:trace-processor-daemon-bundle",
        ],
    },
    tags = ["manual"],
)

studio_plugin(
    name = "com.android.tools.apk",
    directory = "android-apk",
    modules = {"android-apk.jar": ["//tools/vendor/google/android-apk:android-apk"]},
    tags = ["manual"],
)

studio_plugin(
    name = "com.android.tools.ndk",
    directory = "android-ndk",
    modules = {"android-ndk.jar": ["//tools/vendor/google/android-ndk:android-ndk"]},
    tags = ["manual"],
)

studio_plugin(
    name = "intellij.android.layoutlib",
    directory = "android-layoutlib",
    modules = {
        "android-layoutlib.jar": ["//tools/adt/idea/layoutlib:intellij.android.layoutlib"],
        "layoutlib-api.jar": ["//tools/base/layoutlib-api:studio.android.sdktools.layoutlib-api"],
    },
    tags = ["manual"],
)

studio_plugin(
    name = "com.android.layoutlib.native",
    directory = "android-layoutlib-native",
    modules = {
        "android-layoutlib-native.jar": ["//tools/adt/idea/layoutlib-native:intellij.android.layoutlib-native"],
        "layoutlib-api.jar": ["//tools/base/layoutlib-api:studio.android.sdktools.layoutlib-api"],
    },
    tags = ["manual"],
)

studio_plugin(
    name = "androidx.compose.plugins.idea",
    directory = "android-compose-ide-plugin",
    modules = {"android-compose-ide-plugin.jar": ["//tools/adt/idea/compose-ide-plugin:intellij.android.compose-ide-plugin"]},
    tags = ["manual"],
)

studio_plugin(
    name = "com.google.services.firebase",
    directory = "firebase",
    modules = {"firebase.jar": ["//tools/vendor/google/firebase:firebase"]},
    tags = ["manual"],
)

studio_plugin(
    name = "com.google.gct.testing",
    directory = "firebase-testing",
    modules = {"firebase-testing.jar": ["//tools/studio/google/cloud/testing/firebase-testing:firebase-testing"]},
    tags = ["manual"],
)

studio_plugin(
    name = "com.google.appindexing",
    directory = "google-appindexing",
    modules = {"google-appindexing.jar": ["//tools/studio/google/appindexing:google-appindexing"]},
    tags = ["manual"],
)

studio_plugin(
    name = "com.google.gct",
    directory = "google-cloud-tools-as",
    modules = {"google-cloud-tools-as.jar": ["//tools/studio/google/cloud/tools/android-studio-plugin:google-cloud-tools-as"]},
    tags = ["manual"],
)

studio_plugin(
    name = "com.google.gct.core",
    directory = "google-cloud-tools-core-as",
    modules = {"google-cloud-tools-core-as.jar": ["//tools/studio/google/cloud/tools/core-plugin:google-cloud-tools-core-as"]},
    tags = ["manual"],
)

studio_plugin(
    name = "com.google.gct.login",
    directory = "google-login-as",
    modules = {"google-login-as.jar": ["//tools/studio/google/cloud/tools/google-login-plugin:google-login-as"]},
    tags = ["manual"],
)

studio_plugin(
    name = "com.google.cluestick.studioclient",
    directory = "google-samples",
    modules = {"google-samples.jar": ["//tools/studio/google/samples:google-samples"]},
    tags = ["manual"],
)

studio_plugin(
    name = "com.android.tools.idea.smali",
    directory = "smali",
    modules = {"smali.jar": ["//tools/adt/idea/smali:intellij.android.smali"]},
    tags = ["manual"],
)

studio_plugin(
    name = "com.google.gct.test.recorder",
    directory = "test-recorder",
    modules = {"test-recorder.jar": ["//tools/studio/google/cloud/testing/test-recorder:test-recorder"]},
    tags = ["manual"],
)

studio_plugin(
    name = "com.google.urlassistant",
    directory = "url-assistant",
    modules = {"url-assistant.jar": ["//tools/vendor/google/url-assistant:url-assistant"]},
    tags = ["manual"],
)

android_studio(
    name = "android-studio",
    jre = "//prebuilts/studio/jdk:jdk-bundle",
    modules = {
        "android-base-common.jar": [
            "//tools/base/common:studio.android.sdktools.common",
        ],
        "google-analytics-library.jar": [
            "//tools/base/annotations:studio.android.sdktools.android-annotations",
            "//tools/analytics-library/shared:analytics-shared",
            "//tools/analytics-library/tracker:analytics-tracker",
            "//tools/analytics-library/publisher:analytics-publisher",
            "//tools/analytics-library/crash:analytics-crash",
        ],
    },
    platform = "//prebuilts/studio/intellij-sdk:studio-sdk.platform",
    plugins = [
        ":intellij.android.plugin",
        ":com.android.tools.apk",
        ":com.android.tools.ndk",
        ":intellij.android.layoutlib",
        ":com.android.layoutlib.native",
        ":androidx.compose.plugins.idea",
        ":com.google.services.firebase",
        ":com.google.gct.testing",
        ":com.google.appindexing",
        ":com.google.gct",
        ":com.google.gct.core",
        ":com.google.gct.login",
        ":com.google.cluestick.studioclient",
        ":com.android.tools.idea.smali",
        ":com.google.gct.test.recorder",
        ":com.google.urlassistant",
    ],
    resources = {
        "bin/lldb": [
            "//prebuilts/tools:lldb-bundle",
        ],
        "lib": [
            # For now listing all the project library needed until we get them from deps
            "//tools/adt/idea/.idea/libraries:unb.javax-inject",
            "//tools/adt/idea/.idea/libraries:unb.studio-grpc",
            "//tools/adt/idea/.idea/libraries:unb.studio-proto",
            "//tools/adt/idea/.idea/libraries:unb.dexlib2",
            "//tools/adt/idea/.idea/libraries:unb.asm-tools",
            "//tools/adt/idea/.idea/libraries:unb.kxml2",
            "//tools/adt/idea/.idea/libraries:unb.libam-instrumentation-data-proto",
            "//tools/adt/idea/.idea/libraries:unb.sqlite",
            "//tools/adt/idea/.idea/libraries:unb.aapt-proto",
            "//tools/adt/idea/.idea/libraries:unb.studio-analytics-proto",
            "//tools/adt/idea/.idea/libraries:unb.aia-proto",
            "//tools/adt/idea/.idea/libraries:unb.baksmali",
            "//tools/adt/idea/.idea/libraries:unb.zxing-core",
            "//tools/adt/idea/.idea/libraries:unb.transport-proto",
            "//tools/adt/idea/.idea/libraries:unb.emulator-proto",
            "//tools/adt/idea/.idea/libraries:unb.perfetto-proto",
            "//tools/adt/idea/.idea/libraries:unb.sqlite-inspector-proto",
            "//tools/adt/idea/.idea/libraries:unb.kotlinx-coroutines-guava",
            "//tools/adt/idea/.idea/libraries:unb.layoutinspector-proto",
            "//tools/adt/idea/.idea/libraries:unb.tightvnc",
        ],
        "license": [
            # TODO: This doesn't cover all used libraries, but it's exactly what ShowLicensesUsedAction is checking.
            "//tools/adt/idea/android/lib:licenses",
            "//tools/studio/google/appindexing/lib:licenses",
            "//tools/studio/google/cloud/testing/firebase-testing/lib:licenses",
            "//tools/studio/google/cloud/testing/test-recorder/lib:licenses",
            "//tools/studio/google/cloud/tools/android-studio-plugin/lib:licenses",
            # "//tools/studio/google/cloud/tools/core-plugin/lib:licenses", # TODO: contains dupplicated licenses with google-login-plugin
            "//tools/studio/google/cloud/tools/google-login-plugin/lib:licenses",
            "//tools/vendor/google/firebase:licenses",
            "//prebuilts/studio/layoutlib:licenses",
        ],
    },
    searchable_options = "searchable-options",
    tags = ["manual"],
    version_eap = True,
    version_full = "dev build",
    version_micro = 0,
    version_patch = 0,
)

py_test(
    name = "test_studio",
    srcs = ["tests/test_studio.py"],
    data = [
        "tests/expected_linux.txt",
        "tests/expected_mac.txt",
        "tests/expected_win.txt",
        ":android-studio.linux.zip",
        ":android-studio.mac.zip",
        ":android-studio.win.zip",
    ],
    tags = ["manual"],
)

py_test(
    name = "searchable_options_test",
    timeout = "eternal",
    srcs = [
        "tests/searchable_options_test.py",
        "tests/update_searchable_options.py",
    ],
    data = [
        ":android-studio.linux.zip",
        ":android-studio.mac.zip",
        ":android-studio.plugin.lst",
    ],
    tags = [
        "block_network",
        "manual",
        "no_windows",
    ],
)

py_binary(
    name = "update_searchable_options",
    srcs = ["tests/update_searchable_options.py"],
    data = [
        ":android-studio.linux.zip",
        ":android-studio.mac.zip",
        ":android-studio.plugin.lst",
    ],
    tags = [
        "block_network",
        "manual",
        "no_mac",
        "no_windows",
    ],
)

py_binary(
    name = "stamper",
    srcs = ["stamper.py"],
    visibility = ["//visibility:public"],
)

py_test(
    name = "tools_test",
    srcs = ["tools_test.py"],
    python_version = "PY2",  # The custom rule runs under py2
    tags = [
        "no_mac",
        "no_windows",
    ],
    visibility = ["//visibility:public"],
    deps = [":stamper"],
)

java_binary(
    name = "updater",
    main_class = "com.intellij.updater.Runner",
    tags = [
        "block_network",
        "manual",
        "no_mac",
        "no_windows",
    ],
    visibility = ["//visibility:public"],
    runtime_deps = [
        # Uses jars directly to avoid pulling in all dependencies.
        "//prebuilts/tools/common/m2/repository/com/google/code/gson/gson/2.8.6:jar",
        "//prebuilts/tools/common/m2/repository/com/google/guava/failureaccess/1.0.1:jar",
        "//prebuilts/tools/common/m2/repository/com/google/guava/guava/29.0-jre:jar",
        "//prebuilts/tools/common/m2/repository/com/google/j2objc/j2objc-annotations/1.3:jar",
        "//prebuilts/tools/common/m2/repository/com/google/protobuf/protobuf-java/3.5.1:jar",
        "//prebuilts/tools/common/m2/repository/com/nothome/javaxdelta/2.0.1:jar",
        "//prebuilts/tools/common/m2/repository/it/unimi/dsi/fastutil/8.4.0:jar",
        "//prebuilts/tools/common/m2/repository/log4j/log4j/1.2.17:jar",
        "//prebuilts/tools/common/m2/repository/net/java/dev/jna/jna-platform/5.5.0:jar",
        "//prebuilts/tools/common/m2/repository/net/java/dev/jna/jna/5.5.0:jar",
        "//prebuilts/studio/intellij-sdk:studio-sdk-updater",
        "//tools/adt/idea/.idea/libraries:unb.kotlin-stdlib-jdk8",
        "//tools/adt/idea/.idea/libraries:unb.studio-analytics-proto",
        "//tools/adt/idea/studio-updater:intellij.android.updater.studio-updater.jar",  # Adds analytics to the updater
        "//tools/analytics-library/shared:analytics-shared.jar",
        "//tools/analytics-library/tracker:analytics-tracker.jar",
        "//tools/base/annotations:studio.android.sdktools.android-annotations.jar",
        "//tools/base/common:studio.android.sdktools.common.jar",
    ],
)

# managed by go/iml_to_build [unb]
iml_module(
    name = "studio",
    iml_files = ["studio.iml"],
    project = "unb",
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//prebuilts/studio/intellij-sdk:studio-sdk",
        "//tools/adt/idea/android-plugin:intellij.android.plugin[module]",
        "//tools/vendor/google/android-apk[module]",
        "//tools/adt/idea/compose-ide-plugin:intellij.android.compose-ide-plugin[module]",
        "//tools/adt/idea/layoutlib:intellij.android.layoutlib[module]",
        "//tools/adt/idea/layoutlib-native:intellij.android.layoutlib-native[module]",
        "//tools/vendor/google/android-ndk[module]",
        "//tools/studio/google/cloud/tools/android-studio-plugin:google-cloud-tools-as[module]",
        "//tools/vendor/google/firebase[module]",
        "//tools/studio/google/cloud/testing/firebase-testing[module]",
        "//tools/studio/google/appindexing:google-appindexing[module]",
        "//tools/studio/google/cloud/tools/core-plugin:google-cloud-tools-core-as[module]",
        "//tools/studio/google/cloud/tools/google-login-plugin:google-login-as[module]",
        "//tools/studio/google/samples:google-samples[module]",
        "//tools/adt/idea/smali:intellij.android.smali[module]",
        "//tools/studio/google/cloud/testing/test-recorder[module]",
        "//tools/vendor/google/url-assistant[module]",
        "//prebuilts/studio/intellij-sdk:studio-sdk-plugin-configurationScript",
        "//prebuilts/studio/intellij-sdk:studio-sdk-plugin-copyright",
        "//prebuilts/studio/intellij-sdk:studio-sdk-plugin-coverage",
        "//prebuilts/studio/intellij-sdk:studio-sdk-plugin-editorconfig",
        "//prebuilts/studio/intellij-sdk:studio-sdk-plugin-filePrediction",
        "//prebuilts/studio/intellij-sdk:studio-sdk-plugin-git4idea",
        "//prebuilts/studio/intellij-sdk:studio-sdk-plugin-github",
        "//prebuilts/studio/intellij-sdk:studio-sdk-plugin-gradle",
        "//prebuilts/studio/intellij-sdk:studio-sdk-plugin-gradle-java",
        "//prebuilts/studio/intellij-sdk:studio-sdk-plugin-Groovy",
        "//prebuilts/studio/intellij-sdk:studio-sdk-plugin-hg4idea",
        "//prebuilts/studio/intellij-sdk:studio-sdk-plugin-IntelliLang",
        "//prebuilts/studio/intellij-sdk:studio-sdk-plugin-java-decompiler",
        "//prebuilts/studio/intellij-sdk:studio-sdk-plugin-java-i18n",
        "//prebuilts/studio/intellij-sdk:studio-sdk-plugin-java-ide-customization",
        "//prebuilts/studio/intellij-sdk:studio-sdk-plugin-junit",
        "//prebuilts/studio/intellij-sdk:studio-sdk-plugin-Kotlin",
        "//prebuilts/studio/intellij-sdk:studio-sdk-plugin-maven-model",
        "//prebuilts/studio/intellij-sdk:studio-sdk-plugin-platform-images",
        "//prebuilts/studio/intellij-sdk:studio-sdk-plugin-properties",
        "//prebuilts/studio/intellij-sdk:studio-sdk-plugin-properties-resource-bundle-editor",
        "//prebuilts/studio/intellij-sdk:studio-sdk-plugin-repository-search",
        "//prebuilts/studio/intellij-sdk:studio-sdk-plugin-settings-repository",
        "//prebuilts/studio/intellij-sdk:studio-sdk-plugin-sh",
        "//prebuilts/studio/intellij-sdk:studio-sdk-plugin-stream-debugger",
        "//prebuilts/studio/intellij-sdk:studio-sdk-plugin-svn4idea",
        "//prebuilts/studio/intellij-sdk:studio-sdk-plugin-tasks",
        "//prebuilts/studio/intellij-sdk:studio-sdk-plugin-terminal",
        "//prebuilts/studio/intellij-sdk:studio-sdk-plugin-testng",
        "//prebuilts/studio/intellij-sdk:studio-sdk-plugin-textmate",
        "//prebuilts/studio/intellij-sdk:studio-sdk-plugin-vcs-changeReminder",
        "//prebuilts/studio/intellij-sdk:studio-sdk-plugin-yaml",
    ],
)
