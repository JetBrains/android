load("//tools/base/bazel:bazel.bzl", "iml_module")
load("//tools/adt/idea/studio:studio.bzl", "studio_plugin", "android_studio")

# managed by go/iml_to_build [unb]
iml_module(
    name = "unb.studio",
    srcs = ["src"],
    iml_files = ["studio.iml"],
    tags = [
        "manual",
        "unb",
    ],
    test_srcs = ["testSrc"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//prebuilts/studio/intellij-sdk:studio-sdk",
        "//tools/adt/idea/android-plugin:unb.intellij.android.plugin[module]",
        "//tools/vendor/google/android-apk:unb.android-apk[module]",
        "//tools/adt/idea/compose-ide-plugin:unb.intellij.android.compose-ide-plugin[module]",
        "//tools/adt/idea/layoutlib:unb.intellij.android.layoutlib[module]",
        "//tools/adt/idea/layoutlib-native:unb.intellij.android.layoutlib-native[module]",
        "//tools/vendor/google/android-ndk:unb.android-ndk[module]",
        "//tools/studio/google/cloud/tools/android-studio-plugin:unb.google-cloud-tools-as[module]",
        "//tools/vendor/google/firebase:unb.firebase[module]",
        "//tools/studio/google/cloud/testing/firebase-testing:unb.firebase-testing[module]",
        "//tools/studio/google/appindexing:unb.google-appindexing[module]",
        "//tools/studio/google/cloud/tools/core-plugin:unb.google-cloud-tools-core-as[module]",
        "//tools/studio/google/cloud/tools/google-login-plugin:unb.google-login-as[module]",
        "//tools/studio/google/samples:unb.google-samples[module]",
        "//tools/adt/idea/smali:unb.intellij.android.smali[module]",
        "//tools/studio/google/cloud/testing/test-recorder:unb.test-recorder[module]",
        "//tools/vendor/google/url-assistant:unb.url-assistant[module]",
        "//tools/adt/idea/.idea/libraries:unb.studio-plugin-Compose",
        "//tools/adt/idea/.idea/libraries:unb.studio-plugin-configurationScript",
        "//tools/adt/idea/.idea/libraries:unb.studio-plugin-copyright",
        "//tools/adt/idea/.idea/libraries:unb.studio-plugin-coverage",
        "//tools/adt/idea/.idea/libraries:unb.studio-plugin-editorconfig",
        "//tools/adt/idea/.idea/libraries:unb.studio-plugin-filePrediction",
        "//tools/adt/idea/.idea/libraries:unb.studio-plugin-git4idea",
        "//tools/adt/idea/.idea/libraries:unb.studio-plugin-github",
        "//tools/adt/idea/.idea/libraries:unb.studio-plugin-gradle",
        "//tools/adt/idea/.idea/libraries:unb.studio-plugin-gradle-java",
        "//tools/adt/idea/.idea/libraries:unb.studio-plugin-Groovy",
        "//tools/adt/idea/.idea/libraries:unb.studio-plugin-hg4idea",
        "//tools/adt/idea/.idea/libraries:unb.studio-plugin-IntelliLang",
        "//tools/adt/idea/.idea/libraries:unb.studio-plugin-java",
        "//tools/adt/idea/.idea/libraries:unb.studio-plugin-java-decompiler",
        "//tools/adt/idea/.idea/libraries:unb.studio-plugin-java-i18n",
        "//tools/adt/idea/.idea/libraries:unb.studio-plugin-java-ide-customization",
        "//tools/adt/idea/.idea/libraries:unb.studio-plugin-junit",
        "//tools/adt/idea/.idea/libraries:unb.studio-plugin-Kotlin",
        "//tools/adt/idea/.idea/libraries:unb.studio-plugin-maven-model",
        "//tools/adt/idea/.idea/libraries:unb.studio-plugin-platform-images",
        "//tools/adt/idea/.idea/libraries:unb.studio-plugin-properties",
        "//tools/adt/idea/.idea/libraries:unb.studio-plugin-properties-resource-bundle-editor",
        "//tools/adt/idea/.idea/libraries:unb.studio-plugin-repository-search",
        "//tools/adt/idea/.idea/libraries:unb.studio-plugin-settings-repository",
        "//tools/adt/idea/.idea/libraries:unb.studio-plugin-sh",
        "//tools/adt/idea/.idea/libraries:unb.studio-plugin-stream-debugger",
        "//tools/adt/idea/.idea/libraries:unb.studio-plugin-svn4idea",
        "//tools/adt/idea/.idea/libraries:unb.studio-plugin-tasks",
        "//tools/adt/idea/.idea/libraries:unb.studio-plugin-terminal",
        "//tools/adt/idea/.idea/libraries:unb.studio-plugin-testng",
        "//tools/adt/idea/.idea/libraries:unb.studio-plugin-textmate",
        "//tools/adt/idea/.idea/libraries:unb.studio-plugin-vcs-changeReminder",
        "//tools/adt/idea/.idea/libraries:unb.studio-plugin-yaml",
    ],
)

studio_plugin(
    name = "intellij.android.plugin",
    directory = "android",
    modules = {
        "adt-ui.jar": [
            "//tools/adt/idea/adt-ui-model:unb.intellij.android.adt.ui.model",
            "//tools/adt/idea/adt-ui:unb.intellij.android.adt.ui",
        ],
        "android-common.jar": [
            "//tools/adt/idea/android-common:unb.intellij.android.common",
        ],
        "android-extensions-ide.jar": [
            "//tools/adt/idea/android-kotlin:unb.intellij.android.kotlin.extensions",
        ],
        "android-kotlin.jar": [
            "//tools/adt/idea/android-kotlin:unb.intellij.android.kotlin.idea",
            "//tools/adt/idea/android-kotlin:unb.intellij.android.kotlin.output.parser",
        ],
        "android-profilers.jar": [
            "//tools/adt/idea/profilers-atrace:unb.intellij.android.profilers.atrace",
            "//tools/adt/idea/profilers-ui:unb.intellij.android.profilers.ui",
            "//tools/adt/idea/profilers:unb.intellij.android.profilers",
            "//tools/adt/idea/transport-database:unb.intellij.android.transportDatabase",
        ],
        "android-rt.jar": [
            "//tools/adt/idea/rt:unb.intellij.android.rt",
        ],
        "android.jar": [
            "//tools/adt/idea/analytics:unb.analytics",
            "//tools/adt/idea/android-layout-inspector:unb.android-layout-inspector",
            "//tools/base/flags:unb.studio.android.sdktools.flags",
            "//tools/adt/idea/assistant:unb.assistant",
            "//tools/adt/idea/connection-assistant:unb.connection-assistant",
            "//tools/adt/idea/android-adb:unb.intellij.android.adb",
            "//tools/adt/idea/apkanalyzer:unb.intellij.android.apkanalyzer",
            "//tools/adt/idea/app-inspection/api:unb.intellij.android.app-inspection.api",
            "//tools/adt/idea/app-inspection/ide:unb.intellij.android.app-inspection.ide",
            "//tools/adt/idea/app-inspection/inspector/api:unb.intellij.android.app-inspection.inspector.api",
            "//tools/adt/idea/app-inspection/inspector/ide:unb.intellij.android.app-inspection.inspector.ide",
            "//tools/adt/idea/app-inspection/inspectors/workmanager/ide:unb.intellij.android.app-inspection.inspectors.workmanager.ide",
            "//tools/adt/idea/app-inspection/inspectors/workmanager/model:unb.intellij.android.app-inspection.inspectors.workmanager.model",
            "//tools/adt/idea/app-inspection/inspectors/workmanager/view:unb.intellij.android.app-inspection.inspectors.workmanager.view",
            "//tools/adt/idea/build-attribution:unb.intellij.android.build-attribution",
            "//tools/adt/idea/compose-designer:unb.intellij.android.compose-designer",
            "//tools/adt/idea/android:unb.intellij.android.core",
            "//tools/adt/idea/dagger:unb.intellij.android.dagger",
            "//tools/adt/idea/databinding:unb.intellij.android.databinding",
            "//tools/adt/idea/android-debuggers:unb.intellij.android.debuggers",
            "//tools/adt/idea/deploy:unb.intellij.android.deploy",
            "//tools/adt/idea/designer/customview:unb.intellij.android.designer.customview",
            "//tools/adt/idea/designer:unb.intellij.android.designer",
            "//tools/adt/idea/emulator:unb.intellij.android.emulator",
            "//tools/adt/idea/gradle-tooling/studio-gradle-tooling-api:intellij.android.gradle-tooling.api",
            "//tools/adt/idea/gradle-tooling/studio-gradle-tooling-impl:intellij.android.gradle-tooling.impl",
            "//tools/adt/idea/gradle-dsl:unb.intellij.android.gradle.dsl",
            "//tools/adt/idea/android-lang-databinding:unb.intellij.android.lang-databinding",
            "//tools/adt/idea/android-lang:unb.intellij.android.lang",
            "//tools/adt/idea/layout-inspector:unb.intellij.android.layout-inspector",
            "//tools/adt/idea/layout-ui:unb.intellij.android.layout-ui",
            "//tools/adt/idea/mlkit:unb.intellij.android.mlkit",
            "//tools/adt/idea/nav/editor:unb.intellij.android.nav.editor",
            "//tools/adt/idea/nav/safeargs:unb.intellij.android.nav.safeargs",
            "//tools/adt/idea/android-npw:unb.intellij.android.newProjectWizard",
            "//tools/adt/idea/observable-ui:unb.intellij.android.observable.ui",
            "//tools/adt/idea/observable:unb.intellij.android.observable",
            "//tools/adt/idea/android-plugin:unb.intellij.android.plugin",
            "//tools/adt/idea/profilers-android:unb.intellij.android.profilersAndroid",
            "//tools/adt/idea/project-system-gradle-psd:unb.intellij.android.projectSystem.gradle.psd",
            "//tools/adt/idea/project-system-gradle-sync:unb.intellij.android.projectSystem.gradle.sync",
            "//tools/adt/idea/project-system-gradle:unb.intellij.android.projectSystem.gradle",
            "//tools/adt/idea/project-system:unb.intellij.android.projectSystem",
            "//tools/adt/idea/resources-base:unb.intellij.android.resources-base",
            "//tools/adt/idea/room:unb.intellij.android.room",
            "//tools/adt/idea/sdk-updates:unb.intellij.android.sdkUpdates",
            "//tools/adt/idea/android-test-retention:unb.intellij.android.testRetention",
            "//tools/adt/idea/android-transport:unb.intellij.android.transport",
            "//tools/adt/idea/wizard-model:unb.intellij.android.wizard.model",
            "//tools/adt/idea/wizard:unb.intellij.android.wizard",
            "//tools/adt/idea/native-symbolizer:unb.native-symbolizer",
            "//tools/adt/idea/whats-new-assistant:unb.whats-new-assistant",
        ],
        "artwork.jar": [
            "//tools/adt/idea/artwork:unb.intellij.android.artwork",
        ],
        "repository.jar": [
            "//tools/base/repository:unb.studio.android.sdktools.repository",
        ],
        "build-common.jar": [
            "//tools/adt/idea/build-common:unb.intellij.android.buildCommon",
        ],
        "data-binding.jar": [
            "//tools/data-binding:unb.studio.baseLibrary",
            "//tools/data-binding:unb.studio.baseLibrarySupport",
            "//tools/data-binding:unb.studio.compiler",
            "//tools/data-binding:unb.studio.compilerCommon",
        ],
        "game-tools.jar": [
            "//tools/vendor/google/game-tools/main:unb.android.game-tools.main",
        ],
        "jps/android-jps-plugin.jar": [
            "//tools/adt/idea/jps-plugin:unb.intellij.android.jps",
        ],
        "layoutlib-loader.jar": [
            "//tools/adt/idea/layoutlib-loader:unb.intellij.android.layoutlib-loader",
        ],
        "lint-ide.jar": [
            "//tools/adt/idea/lint:unb.intellij.lint",
        ],
        "manifest-merger.jar": [
            "//tools/base/build-system:unb.studio.android.sdktools.manifest-merger",
        ],
        "pixelprobe.jar": [
            "//tools/base/chunkio:unb.studio.android.sdktools.chunkio",
            "//tools/base/pixelprobe:unb.studio.android.sdktools.pixelprobe",
        ],
        "sdk-common.jar": [
            "//tools/base/sdk-common:unb.studio.android.sdktools.sdk-common",
        ],
        "sdk-tools.jar": [
            "//tools/base/apkparser/analyzer:unb.studio.android.sdktools.analyzer",
            "//tools/base/annotations:unb.studio.android.sdktools.android-annotations",
            "//tools/base/apkparser:unb.studio.android.sdktools.binary-resources",
            "//tools/base/build-system/builder-model:unb.studio.android.sdktools.builder-model",
            "//tools/base/build-system/builder-test-api:unb.studio.android.sdktools.builder-test-api",
            "//tools/base/ddmlib:unb.studio.android.sdktools.ddmlib",
            "//tools/base/deploy/deployer:unb.studio.android.sdktools.deployer",
            "//tools/base/draw9patch:unb.studio.android.sdktools.draw9patch",
            "//tools/base/device_validator:unb.studio.android.sdktools.dvlib",
            "//tools/base/layoutinspector:unb.studio.android.sdktools.layoutinspector",
            "//tools/base/lint:unb.studio.android.sdktools.lint-api",
            "//tools/base/lint:unb.studio.android.sdktools.lint-checks",
            "//tools/base/lint:unb.studio.android.sdktools.lint-model",
            "//tools/base/mlkit-common:unb.studio.android.sdktools.mlkit-common",
            "//tools/base/ninepatch:unb.studio.android.sdktools.ninepatch",
            "//tools/base/perflib:unb.studio.android.sdktools.perflib",
            "//tools/base/tracer:unb.studio.android.sdktools.tracer",
            "//tools/base/zipflinger:unb.studio.android.sdktools.zipflinger",
            "//tools/base/usb-devices:unb.studio.usb-devices",
        ],
        "sdklib.jar": [
            "//tools/base/sdklib:unb.studio.android.sdktools.sdklib",
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
            "//tools/base/tracer:trace_agent.jar",  # TODO(b/149320690): remove in 4.1 final release.
        ],
        "resources": [
            "//tools/base/profiler/transform:profilers-transform",
            "//tools/base/profiler/app:perfa",
            "//tools/base/profiler/app:perfa_okhttp",
            "//tools/base/transport:transport-bundle",
            "//prebuilts/tools:simpleperf-bundle",
            "//prebuilts/tools/common/perfetto:perfetto-bundle",
            "//prebuilts/tools/common/app-inspection/androidx/sqlite:sqlite-inspection-bundle",
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
    modules = {"android-apk.jar": ["//tools/vendor/google/android-apk:unb.android-apk"]},
    tags = ["manual"],
)

studio_plugin(
    name = "com.android.tools.ndk",
    directory = "android-ndk",
    modules = {"android-ndk.jar": ["//tools/vendor/google/android-ndk:unb.android-ndk"]},
    tags = ["manual"],
)

studio_plugin(
    name = "intellij.android.layoutlib",
    directory = "android-layoutlib",
    modules = {
        "android-layoutlib.jar": ["//tools/adt/idea/layoutlib:unb.intellij.android.layoutlib"],
        "layoutlib-api.jar": ["//tools/base/layoutlib-api:studio.android.sdktools.layoutlib-api"],
    },
    tags = ["manual"],
)

studio_plugin(
    name = "com.android.layoutlib.native",
    directory = "android-layoutlib-native",
    modules = {
        "android-layoutlib-native.jar": ["//tools/adt/idea/layoutlib-native:unb.intellij.android.layoutlib-native"],
        "layoutlib-api.jar": ["//tools/base/layoutlib-api:studio.android.sdktools.layoutlib-api"],
    },
    tags = ["manual"],
)

studio_plugin(
    name = "androidx.compose.plugins.idea",
    directory = "android-compose-ide-plugin",
    modules = {"android-compose-ide-plugin.jar": ["//tools/adt/idea/compose-ide-plugin:unb.intellij.android.compose-ide-plugin"]},
    tags = ["manual"],
)

studio_plugin(
    name = "com.google.services.firebase",
    directory = "firebase",
    modules = {"firebase.jar": ["//tools/vendor/google/firebase:unb.firebase"]},
    tags = ["manual"],
)

studio_plugin(
    name = "com.google.gct.testing",
    directory = "firebase-testing",
    modules = {"firebase-testing.jar": ["//tools/studio/google/cloud/testing/firebase-testing:unb.firebase-testing"]},
    tags = ["manual"],
)

studio_plugin(
    name = "com.google.appindexing",
    directory = "google-appindexing",
    modules = {"google-appindexing.jar": ["//tools/studio/google/appindexing:unb.google-appindexing"]},
    tags = ["manual"],
)

studio_plugin(
    name = "com.google.gct",
    directory = "google-cloud-tools-as",
    modules = {"google-cloud-tools-as.jar": ["//tools/studio/google/cloud/tools/android-studio-plugin:unb.google-cloud-tools-as"]},
    tags = ["manual"],
)

studio_plugin(
    name = "com.google.gct.core",
    directory = "google-cloud-tools-core-as",
    modules = {"google-cloud-tools-core-as.jar": ["//tools/studio/google/cloud/tools/core-plugin:unb.google-cloud-tools-core-as"]},
    tags = ["manual"],
)

studio_plugin(
    name = "com.google.gct.login",
    directory = "google-login-as",
    modules = {"google-login-as.jar": ["//tools/studio/google/cloud/tools/google-login-plugin:unb.google-login-as"]},
    tags = ["manual"],
)

studio_plugin(
    name = "com.google.cluestick.studioclient",
    directory = "google-samples",
    modules = {"google-samples.jar": ["//tools/studio/google/samples:unb.google-samples"]},
    tags = ["manual"],
)

studio_plugin(
    name = "com.android.tools.idea.smali",
    directory = "smali",
    modules = {"smali.jar": ["//tools/adt/idea/smali:unb.intellij.android.smali"]},
    tags = ["manual"],
)

studio_plugin(
    name = "com.google.gct.test.recorder",
    directory = "test-recorder",
    modules = {"test-recorder.jar": ["//tools/studio/google/cloud/testing/test-recorder:unb.test-recorder"]},
    tags = ["manual"],
)

studio_plugin(
    name = "com.google.urlassistant",
    directory = "url-assistant",
    modules = {"url-assistant.jar": ["//tools/vendor/google/url-assistant:unb.url-assistant"]},
    tags = ["manual"],
)

android_studio(
    name = "android-studio",
    jre = "//prebuilts/studio/jdk:jdk-bundle",
    modules = {
        "android-base-common.jar": [
            "//tools/base/common:unb.studio.android.sdktools.common",
        ],
        "google-analytics-library.jar": [
            "//tools/base/annotations:unb.studio.android.sdktools.android-annotations",
            "//tools/analytics-library/shared:unb.analytics-shared",
            "//tools/analytics-library/tracker:unb.analytics-tracker",
            "//tools/analytics-library/publisher:unb.analytics-publisher",
            "//tools/analytics-library/crash:unb.analytics-crash",
        ],
    },
    platform = "//prebuilts/studio/intellij-sdk:studio-platform",
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
    },
    searchable_options = "searchable-options",
    tags = ["manual"],
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
    name = "update_searchable_options",
    srcs = ["tests/update_searchable_options.py"],
    data = [
        ":android-studio.linux.zip",
        ":android-studio.plugin.lst",
    ],
    tags = [
        "block_network",
        "manual",
        "no_mac",
        "no_windows",
    ],
)

java_binary(
    name = "stamper",
    srcs = ["src/com/android/tools/Stamper.java"],
    main_class = "com.android.tools.Stamper",
    visibility = ["//visibility:public"],
)

java_test(
    name = "stamper_tests",
    srcs = ["testSrc/com/android/tools/StamperTest.java"],
    test_class = "com.android.tools.StamperTest",
    visibility = ["//visibility:public"],
    deps = [
        ":stamper",
        "//tools/base/third_party:com.google.guava_guava",
        "//tools/base/third_party:junit_junit",
    ],
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
        "//prebuilts/tools/common/m2/repository/it/unimi/dsi/fastutil/7.2.1:jar",
        "//prebuilts/tools/common/m2/repository/log4j/log4j/1.2.17:jar",
        "//prebuilts/tools/common/m2/repository/net/java/dev/jna/jna-platform/5.5.0:jar",
        "//prebuilts/tools/common/m2/repository/net/java/dev/jna/jna/5.5.0:jar",
        "//tools/adt/idea/.idea/libraries:unb.intellij-updater",
        "//tools/adt/idea/.idea/libraries:unb.kotlin-stdlib-jdk8",
        "//tools/adt/idea/.idea/libraries:unb.studio-analytics-proto",
        "//tools/adt/idea/studio-updater:unb.intellij.android.updater.studio-updater.jar",  # Adds analytics to the updater
        "//tools/analytics-library/shared:analytics-shared.jar",
        "//tools/analytics-library/tracker:analytics-tracker.jar",
        "//tools/base/annotations:unb.studio.android.sdktools.android-annotations.jar",
        "//tools/base/common:unb.studio.android.sdktools.common.jar",
    ],
)
