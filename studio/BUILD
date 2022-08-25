load("//tools/base/bazel:bazel.bzl", "iml_module", "iml_test")
load("//tools/adt/idea/studio:studio.bzl", "android_studio", "studio_plugin")

studio_plugin(
    name = "org.jetbrains.android",
    directory = "android",
    libs = [
        "//prebuilts/r8",
        "//prebuilts/tools/common/archive-patcher:explainer",
        "//prebuilts/tools/common/archive-patcher:generator",
        "//prebuilts/tools/common/archive-patcher:shared",
        "//prebuilts/tools/common/m2:okio",
        "//prebuilts/tools/common/m2:moshi",
        "//prebuilts/tools/common/m2:eclipse-layout-kernel",
        "//prebuilts/tools/common/m2:juniversalchardet",
        "//prebuilts/tools/common/m2:commons-lang",
        "//prebuilts/tools/common/m2:javapoet",
        "//prebuilts/tools/common/m2:auto-common",
        "//prebuilts/tools/common/m2:jetifier-core",
        "//prebuilts/tools/common/m2:flatbuffers-java",
        "//prebuilts/tools/common/m2:tensorflow-lite-metadata",
        "//prebuilts/tools/common/m2:trace-perfetto-library",
        "//tools/adt/idea/.idea/libraries:aapt-proto",
        "//tools/adt/idea/.idea/libraries:aia-proto",
        "//tools/adt/idea/.idea/libraries:android-test-plugin-host-device-info-proto",
        "//tools/adt/idea/.idea/libraries:asm-tools",
        "//tools/adt/idea/.idea/libraries:baksmali",
        "//tools/adt/idea/.idea/libraries:dexlib2",
        "//tools/adt/idea/.idea/libraries:emulator-proto",
        "//tools/adt/idea/.idea/libraries:ffmpeg",
        "//tools/adt/idea/.idea/libraries:firebase_java_proto",
        "//tools/adt/idea/.idea/libraries:HdrHistogram",
        "//tools/adt/idea/.idea/libraries:javax-inject",
        "//tools/adt/idea/.idea/libraries:kotlinx-coroutines-guava",
        "//tools/adt/idea/.idea/libraries:kxml2",
        "//tools/adt/idea/.idea/libraries:layoutinspector-skia-proto",
        "//tools/adt/idea/.idea/libraries:layoutinspector-view-proto",
        "//tools/adt/idea/.idea/libraries:libam-instrumentation-data-proto",
        "//tools/adt/idea/.idea/libraries:libapp-processes-proto",
        "//tools/adt/idea/.idea/libraries:network_inspector_java_proto",
        "//tools/adt/idea/.idea/libraries:perfetto-proto",
        "//tools/adt/idea/.idea/libraries:sqlite-inspector-proto",
        "//tools/adt/idea/.idea/libraries:sqlite",
        "//tools/adt/idea/.idea/libraries:studio-analytics-proto",
        "//tools/adt/idea/.idea/libraries:studio-grpc",
        "//tools/adt/idea/.idea/libraries:studio-proto",
        "//tools/adt/idea/.idea/libraries:transport-proto",
        "//tools/adt/idea/.idea/libraries:utp-core-proto-jarjar",
        "//tools/adt/idea/.idea/libraries:zxing-core",
        "//tools/adt/idea/android:libandroid-core-proto",
        "//tools/adt/idea/android:libstudio.android-test-plugin-host-retention-proto",
        "//tools/adt/idea/android/lib:android-sdk-tools-jps",
        "//tools/adt/idea/android/lib:instantapps-api",
        "//tools/adt/idea/android/lib:spantable",
        "//tools/adt/idea/app-inspection/inspectors/backgroundtask/model:background-inspector-proto",
        "//tools/adt/idea/app-inspection/inspectors/backgroundtask/model:workmanager-inspector-proto",
        "//tools/adt/idea/app-inspection/inspectors/backgroundtask/view:background-inspector-proto",
        "//tools/adt/idea/app-inspection/inspectors/backgroundtask/view:workmanager-inspector-proto",
        "//tools/adt/idea/app-inspection/inspectors/workmanager/model:workmanager-inspector-proto",
        "//tools/adt/idea/app-inspection/inspectors/workmanager/view:workmanager-inspector-proto",
        "//tools/adt/idea/compose-designer:ui-animation-tooling-internal",
        "//tools/adt/idea/profilers-android:traceprocessor-proto",
        "//tools/adt/idea/profilers:traceprocessor-proto",
        "//tools/adt/idea/project-system-gradle/lib:pepk",
        "//tools/adt/idea/utp:libstudio.android-test-plugin-result-listener-gradle-proto",
        "//tools/base/deploy/deployer:deploy_java_proto",
        "//tools/base/deploy/deployer:libjava_sites",
        "//tools/base/lint:liblint-checks-proto",
        "//tools/adt/idea/debuggers:libjava_sites",
        "//tools/base/deploy/deployer:libjava_version",
        "//tools/vendor/google/game-tools/main:game-tools-protos",
        "//tools/data-binding:compilerCommon.antlr_runtime.shaded",
        "//tools/data-binding:compilerCommon.antlr.shaded",
    ],
    licenses = [
        "//prebuilts/r8:license",
        "//prebuilts/tools/common/m2:ffmpeg-licenses",
        "//tools/adt/idea/android/lib:licenses",
    ],
    modules = {
        "adt-ui.jar": [
            "//tools/adt/idea/adt-ui-model:intellij.android.adt.ui.model",
            "//tools/adt/idea/adt-ui:intellij.android.adt.ui",
        ],
        "android-common.jar": [
            "//tools/adt/idea/android-common:intellij.android.common",
            "//tools/adt/idea/jps-model:intellij.android.jps.model",
        ],
        "android-extensions-ide.jar": [
            "//tools/adt/idea/android-kotlin:intellij.android.kotlin.extensions",
            "//tools/adt/idea/android-kotlin:intellij.android.kotlin.extensions.common",
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
            "//tools/base/flags:studio.android.sdktools.flags",
            "//tools/adt/idea/assistant:assistant",
            "//tools/adt/idea/connection-assistant:connection-assistant",
            "//tools/adt/idea/android-adb:intellij.android.adb",
            "//tools/adt/idea/android-adb-ui:intellij.android.adb.ui",
            "//tools/adt/idea/android-lint:intellij.android.lint",
            "//tools/adt/idea/android-templates:intellij.android.templates",
            "//tools/adt/idea/apkanalyzer:intellij.android.apkanalyzer",
            "//tools/adt/idea/app-inspection/api:app-inspection.api",
            "//tools/adt/idea/app-inspection/ide:app-inspection.ide",
            "//tools/adt/idea/app-inspection/inspector/api:app-inspection.inspector.api",
            "//tools/adt/idea/app-inspection/inspector/ide:app-inspection.inspector.ide",
            "//tools/adt/idea/app-inspection/inspectors/backgroundtask/ide:app-inspection.inspectors.backgroundtask.ide",
            "//tools/adt/idea/app-inspection/inspectors/backgroundtask/model:app-inspection.inspectors.backgroundtask.model",
            "//tools/adt/idea/app-inspection/inspectors/backgroundtask/view:app-inspection.inspectors.backgroundtask.view",
            "//tools/adt/idea/app-inspection/inspectors/workmanager/ide:app-inspection.inspectors.workmanager.ide",
            "//tools/adt/idea/app-inspection/inspectors/workmanager/model:app-inspection.inspectors.workmanager.model",
            "//tools/adt/idea/app-inspection/inspectors/workmanager/view:app-inspection.inspectors.workmanager.view",
            "//tools/adt/idea/build-attribution:intellij.android.build-attribution",
            "//tools/adt/idea/intellij.android.compose-common:intellij.android.compose-common",
            "//tools/adt/idea/android:intellij.android.core",
            "//tools/adt/idea/android-navigator:intellij.android.navigator",
            "//tools/adt/idea/dagger:intellij.android.dagger",
            "//tools/adt/idea/databinding:intellij.android.databinding",
            "//tools/adt/idea/app-inspection/inspectors/database:app-inspection.inspectors.database",
            "//tools/adt/idea/debuggers:intellij.android.debuggers",
            "//tools/adt/idea/deploy:intellij.android.deploy",
            "//tools/adt/idea/device-file-explorer-toolwindow:intellij.android.device-file-explorer-toolwindow",
            "//tools/adt/idea/device-monitor-toolwindow:intellij.android.device-monitor-toolwindow",
            "//tools/adt/idea/device-manager:intellij.android.device-manager",
            "//tools/adt/idea/emulator:intellij.android.emulator",
            "//tools/adt/idea/gradle-tooling/studio-gradle-tooling-api:intellij.android.gradle-tooling.api",
            "//tools/adt/idea/gradle-tooling/studio-gradle-tooling-impl:intellij.android.gradle-tooling.impl",
            "//tools/adt/idea/gradle-dsl:intellij.android.gradle.dsl",
            "//tools/adt/idea/gradle-dsl-kotlin:intellij.android.gradle.dsl.kotlin",
            "//tools/adt/idea/gradle-dsl-toml:intellij.android.gradle.dsl.toml",
            "//tools/adt/idea/android-lang-databinding:intellij.android.lang-databinding",
            "//tools/adt/idea/android-lang:intellij.android.lang",
            "//tools/adt/idea/layout-inspector:intellij.android.layout-inspector",
            "//tools/adt/idea/layout-ui:intellij.android.layout-ui",
            "//tools/adt/idea/logcat:intellij.android.logcat",
            "//tools/adt/idea/mlkit:intellij.android.mlkit",
            "//tools/adt/idea/nav/safeargs:intellij.android.nav.safeargs",
            "//tools/adt/idea/android-npw:intellij.android.newProjectWizard",
            "//tools/adt/idea/observable-ui:intellij.android.observable.ui",
            "//tools/adt/idea/observable:intellij.android.observable",
            "//tools/adt/idea/android-plugin:intellij.android.plugin",
            "//tools/adt/idea/profilers-android:intellij.android.profilersAndroid",
            "//tools/adt/idea/project-system-gradle-models:intellij.android.projectSystem.gradle.models",
            "//tools/adt/idea/project-system-gradle-psd:intellij.android.projectSystem.gradle.psd",
            "//tools/adt/idea/project-system-gradle-repository-search:intellij.android.projectSystem.gradle.repositorySearch",
            "//tools/adt/idea/project-system-gradle-sync:intellij.android.projectSystem.gradle.sync",
            "//tools/adt/idea/project-system-gradle-upgrade:intellij.android.projectSystem.gradle.upgrade",
            "//tools/adt/idea/project-system-gradle:intellij.android.projectSystem.gradle",
            "//tools/adt/idea/project-system:intellij.android.projectSystem",
            "//tools/adt/idea/room:intellij.android.room",
            "//tools/adt/idea/sdk-updates:intellij.android.sdkUpdates",
            "//tools/adt/idea/android-test-retention:intellij.android.testRetention",
            "//tools/adt/idea/threading-checker:intellij.android.threading-checker",
            "//tools/adt/idea/android-transport:intellij.android.transport",
            "//tools/adt/idea/wear-pairing:intellij.android.wear-pairing",
            "//tools/adt/idea/wizard-model:intellij.android.wizard.model",
            "//tools/adt/idea/wizard:intellij.android.wizard",
            "//tools/adt/idea/native-symbolizer:native-symbolizer",
            "//tools/adt/idea/whats-new-assistant:whats-new-assistant",
            "//tools/base/dynamic-layout-inspector/common:studio.dynamic-layout-inspector.common",
            "//tools/adt/idea/app-inspection/inspectors/network/ide:app-inspection.inspectors.network.ide",
            "//tools/adt/idea/app-inspection/inspectors/network/model:app-inspection.inspectors.network.model",
            "//tools/adt/idea/app-inspection/inspectors/network/view:app-inspection.inspectors.network.view",
            "//tools/adt/idea/server-flags:intellij.android.server-flags",
            "//tools/adt/idea/codenavigation:codenavigation",
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
        "inspectors-common.jar": [
            "//tools/adt/idea/inspectors-common/api:intellij.android.inspectors-common.api",
            "//tools/adt/idea/inspectors-common/api-ide:intellij.android.inspectors-common.api-ide",
            "//tools/adt/idea/inspectors-common/ui:intellij.android.inspectors-common.ui",
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
            "//tools/base/annotations:studio.android.sdktools.android-annotations",
            "//tools/base/apkparser/analyzer:studio.android.sdktools.analyzer",
            "//tools/base/apkparser:studio.android.sdktools.binary-resources",
            "//tools/base/build-system/builder-model:studio.android.sdktools.builder-model",
            "//tools/base/build-system/builder-test-api:studio.android.sdktools.builder-test-api",
            "//tools/base/adblib:studio.android.sdktools.adblib",
            "//tools/base/adblib-ddmlibcompatibility:studio.android.sdktools.adblib.ddmlibcompatibility",
            "//tools/base/adblib-tools:studio.android.sdktools.adblib.tools",
            "//tools/base/ddmlib:studio.android.sdktools.ddmlib",
            "//tools/base/device-provisioner:studio.android.sdktools.device-provisioner",
            "//tools/base/deploy/deployer:studio.android.sdktools.deployer",
            "//tools/base/device_validator:studio.android.sdktools.dvlib",
            "//tools/base/draw9patch:studio.android.sdktools.draw9patch",
            "//tools/base/lint:studio.android.sdktools.lint-api",
            "//tools/base/lint:studio.android.sdktools.lint-checks",
            "//tools/base/lint:studio.android.sdktools.lint-model",
            "//tools/base/manifest-parser:studio.android.sdktools.manifest-parser",
            "//tools/base/mlkit-common:studio.android.sdktools.mlkit-common",
            "//tools/base/ninepatch:studio.android.sdktools.ninepatch",
            "//tools/base/perflib:studio.android.sdktools.perflib",
            "//tools/base/resource-repository:studio.android.sdktools.resource-repository",
            "//tools/base/threading-agent-callback:studio.android.sdktools.threading-agent-callback",
            "//tools/base/tracer:studio.android.sdktools.tracer",
            "//tools/base/usb-devices:studio.usb-devices",
            "//tools/base/zipflinger:studio.android.sdktools.zipflinger",
        ],
        "sdklib.jar": [
            "//tools/base/sdklib:studio.android.sdktools.sdklib",
        ],
        "utp.jar": [
            "//tools/adt/idea/utp",
        ],
        "wizard-template.jar": [
            "//tools/base/wizard/template-impl:studio.intellij.android.wizardTemplate.impl",
            "//tools/base/wizard/template-plugin:studio.intellij.android.wizardTemplate.plugin",
        ],
        "google-analytics-library.jar": [
            "//tools/analytics-library/shared:analytics-shared",
            "//tools/analytics-library/tracker:analytics-tracker",
            "//tools/analytics-library/publisher:analytics-publisher",
            "//tools/analytics-library/crash:analytics-crash",
        ],
        "android-base-common.jar": [
            "//tools/base/common:studio.android.sdktools.common",
        ],
        "layoutlib-api.jar": [
            "//tools/base/layoutlib-api:studio.android.sdktools.layoutlib-api",
        ],
    },
    resources = {
        "lib": [
            "//prebuilts/tools/common/m2:ffmpeg-platform-bundle",
        ],
        "resources": [
            "//tools/adt/idea/android/lib:sample-data-bundle",
            "//tools/adt/idea/android/lib:apks-bundle",
            "//tools/adt/idea/artwork:device-art-resources-bundle",  # duplicated in android.jar
            "//tools/adt/idea/android/annotations:androidAnnotations",
            "//tools/adt/idea/emulator/native:native_lib",
            "//tools/adt/idea/emulator/screen-sharing-agent:bundle",
            "//tools/base/app-inspection/inspectors/backgroundtask:bundle",
            "//tools/base/app-inspection/inspectors/network:bundle",
            "//tools/base/dynamic-layout-inspector/agent/appinspection:bundle",
            "//tools/base/profiler/transform:profilers-transform",
            "//tools/base/profiler/app:perfa",
            "//tools/base/profiler/app:perfa_okhttp",
            "//tools/base/tracer:trace_agent.jar",  # TODO(b/149320690): remove in 4.1 final release.
            "//tools/base/transport:transport-bundle",
            "//prebuilts/tools:simpleperf-bundle",
            "//prebuilts/tools/common/perfetto:perfetto-bundle",
            "//prebuilts/tools/common/app-inspection/androidx/sqlite:sqlite-inspection-bundle",
            "//tools/base/deploy/installer:android-installer-bundle",
            "//tools/adt/idea/android:asset-studio-bundle",
            "//prebuilts/tools/common/trace-processor-daemon:trace-processor-daemon-bundle",
        ],
    },
    deps = [
        ":com.android.tools.idea.smali",
        "//prebuilts/studio/intellij-sdk:studio-sdk-plugin-Groovy",
        "//prebuilts/studio/intellij-sdk:studio-sdk-plugin-IntelliLang",
        "//prebuilts/studio/intellij-sdk:studio-sdk-plugin-Kotlin",
        "//prebuilts/studio/intellij-sdk:studio-sdk-plugin-gradle",
        "//prebuilts/studio/intellij-sdk:studio-sdk-plugin-gradle-java",
        "//prebuilts/studio/intellij-sdk:studio-sdk-plugin-junit",
        "//prebuilts/studio/intellij-sdk:studio-sdk-plugin-platform-images",
        "//prebuilts/studio/intellij-sdk:studio-sdk-plugin-properties",
        "//prebuilts/studio/intellij-sdk:studio-sdk-plugin-toml",
        "//prebuilts/studio/intellij-sdk:studio-sdk-plugin-webp",
    ],
)

studio_plugin(
    name = "com.android.tools.apk",
    directory = "android-apk",
    modules = {"android-apk.jar": ["//tools/vendor/google/android-apk:android-apk"]},
    deps = [
        ":com.android.tools.idea.smali",
        ":com.android.tools.ndk",
        ":org.jetbrains.android",
    ],
)

studio_plugin(
    name = "com.android.tools.ndk",
    directory = "android-ndk",
    libs = ["//tools/vendor/google/android-ndk/lib:org.eclipse.cdt"],
    modules = {"android-ndk.jar": ["//tools/vendor/google/android-ndk:android-ndk"]},
    resources = {
        "resources/lldb": [
            "//prebuilts/tools:lldb-bundle",
        ],
    },
    deps = [
        ":org.jetbrains.android",
        "//prebuilts/studio/intellij-sdk:studio-sdk-plugin-Kotlin",
        "//prebuilts/studio/intellij-sdk:studio-sdk-plugin-c-clangd",
        "//prebuilts/studio/intellij-sdk:studio-sdk-plugin-c-plugin",
        "//prebuilts/studio/intellij-sdk:studio-sdk-plugin-cidr-base-plugin",
        "//prebuilts/studio/intellij-sdk:studio-sdk-plugin-cidr-debugger-plugin",
        "//prebuilts/studio/intellij-sdk:studio-sdk-plugin-gradle",
    ],
)

studio_plugin(
    name = "com.android.tools.design",
    directory = "design-tools",
    libs = [
        "//tools/adt/idea/.idea/libraries:layoutlib",
    ],
    licenses = [
        "//prebuilts/studio/layoutlib:licenses",
    ],
    modules = {
        "design-tools.jar": [
            "//tools/adt/idea/compose-designer:intellij.android.compose-designer",
            "//tools/adt/idea/design-plugin:intellij.android.design-plugin",
            "//tools/adt/idea/designer/customview:intellij.android.designer.customview",
            "//tools/adt/idea/designer:intellij.android.designer",
            "//tools/adt/idea/glance-designer:intellij.android.glance-designer",
            "//tools/adt/idea/layoutlib:intellij.android.layoutlib",
            "//tools/adt/idea/nav/editor:intellij.android.nav.editor",
        ],
    },
    resources = {
        "resources": [
            "//prebuilts/studio/layoutlib:layoutlib",
        ],
    },
    deps = [
        ":org.jetbrains.android",
        "//prebuilts/studio/intellij-sdk:studio-sdk-plugin-Kotlin",
        "//prebuilts/studio/intellij-sdk:studio-sdk-plugin-platform-images",
    ],
)

studio_plugin(
    name = "androidx.compose.plugins.idea",
    directory = "android-compose-ide-plugin",
    libs = ["//tools/adt/idea/compose-ide-plugin:compiler-hosted-1.2.0-SNAPSHOT"],
    modules = {"android-compose-ide-plugin.jar": ["//tools/adt/idea/compose-ide-plugin:intellij.android.compose-ide-plugin"]},
    deps = [
        ":org.jetbrains.android",
        "//prebuilts/studio/intellij-sdk:studio-sdk-plugin-Kotlin",
    ],
)

studio_plugin(
    name = "com.google.services.firebase",
    directory = "firebase",
    libs = ["//tools/vendor/google/firebase:google-api-services-mobilesdk-v1"],
    licenses = [
        "//tools/vendor/google/firebase:licenses",
    ],
    modules = {"firebase.jar": ["//tools/vendor/google/firebase:firebase"]},
    deps = [
        ":com.google.gct.login",
        ":com.google.gct.test.recorder",
        ":org.jetbrains.android",
        "//prebuilts/studio/intellij-sdk:studio-sdk-plugin-Groovy",
        "//prebuilts/studio/intellij-sdk:studio-sdk-plugin-Kotlin",
    ],
)

studio_plugin(
    name = "com.google.gct.directaccess",
    directory = "directaccess",
    libs = [
        "//tools/adt/idea/.idea/libraries:directaccess_client_proto",
    ],
    modules = {
        "directaccess.jar": ["//tools/studio/google/cloud/testing/directaccess:directaccess"],
        "directaccess-client.jar": ["//tools/vendor/google/directaccess-client:directaccess-client"],
    },
    resources = {
        "resources": ["//tools/vendor/google/directaccess-client/reverse-daemon:daemon"],
    },
    deps = [
        ":com.google.gct.login",
        ":com.google.gct.testing",
        ":org.jetbrains.android",
    ],
)

studio_plugin(
    name = "com.google.gct.testing",
    directory = "firebase-testing",
    libs = [
        "//prebuilts/tools/common/m2:JSch",
        "//tools/studio/google/cloud/testing/firebase-testing/lib:google-api-services-storage-v1-rev1-1.18.0-rc",
        "//tools/studio/google/cloud/testing/firebase-testing/lib:google-api-services-testing-v1-revsnapshot-1.20.0",
        "//tools/studio/google/cloud/testing/firebase-testing/lib:google-api-services-toolresults-v1beta3-rev20151013-1.20.0",
        "//tools/adt/idea/.idea/libraries:tightvnc",
    ],
    licenses = [
        "//tools/studio/google/cloud/testing/firebase-testing/lib:licenses",
    ],
    modules = {"firebase-testing.jar": ["//tools/studio/google/cloud/testing/firebase-testing:firebase-testing"]},
    deps = [
        ":com.google.gct.core",
        ":com.google.gct.login",
        ":org.jetbrains.android",
        "//prebuilts/studio/intellij-sdk:studio-sdk-plugin-Groovy",
    ],
)

studio_plugin(
    name = "com.google.appindexing",
    directory = "google-appindexing",
    libs = ["//tools/studio/google/appindexing/lib:fetchasgoogle"],
    licenses = [
        "//tools/studio/google/appindexing/lib:licenses",
    ],
    modules = {"google-appindexing.jar": ["//tools/studio/google/appindexing:google-appindexing"]},
    deps = [
        ":com.google.gct.login",
        ":com.google.urlassistant",
        ":org.jetbrains.android",
        "//prebuilts/studio/intellij-sdk:studio-sdk-plugin-Groovy",
    ],
)

studio_plugin(
    name = "com.google.gct",
    directory = "google-cloud-tools-as",
    libs = [
        "//tools/studio/google/cloud/tools/android-studio-plugin/lib:gradle-appengine-builder-model-0.1.0",
        "//tools/studio/google/cloud/tools/android-studio-plugin/lib:samplesindex-v1-1.0-SNAPSHOT",
        "//tools/studio/google/cloud/tools/android-studio-plugin/lib:google-api-services-source",
        "//tools/studio/google/cloud/tools/android-studio-plugin/lib:google-api-services-debugger",
    ],
    licenses = [
        "//tools/studio/google/cloud/tools/android-studio-plugin/lib:licenses",
    ],
    modules = {"google-cloud-tools-as.jar": ["//tools/studio/google/cloud/tools/android-studio-plugin:google-cloud-tools-as"]},
    deps = [
        ":com.google.gct.core",
        ":com.google.gct.login",
        ":org.jetbrains.android",
        "//prebuilts/studio/intellij-sdk:studio-sdk-plugin-Groovy",
        "//prebuilts/studio/intellij-sdk:studio-sdk-plugin-Kotlin",
        "//prebuilts/studio/intellij-sdk:studio-sdk-plugin-git4idea",
        "//prebuilts/studio/intellij-sdk:studio-sdk-plugin-gradle",
        "//prebuilts/studio/intellij-sdk:studio-sdk-plugin-gradle-java",
    ],
)

studio_plugin(
    name = "com.google.gct.core",
    directory = "google-cloud-tools-core-as",
    libs = [
        "//tools/studio/google/cloud/tools/core-plugin/third_party/gradle-appengine-builder-model:gradle-appengine-builder-model-0.1.0",
        "//tools/studio/google/cloud/tools/core-plugin/lib:google-api-services-cloudresourcemanager-v1beta1-rev12-1.21.0",
        "//tools/studio/google/cloud/tools/core-plugin/lib:google-api-services-appengine-v1-rev9-1.22.0",
        "//tools/studio/google/cloud/tools/core-plugin/lib:google-api-services-source",
        "//tools/studio/google/cloud/tools/core-plugin/lib:google-api-services-debugger",
    ],
    licenses = [
        "//tools/studio/google/cloud/tools/core-plugin/lib:licenses",
    ],
    modules = {"google-cloud-tools-core-as.jar": ["//tools/studio/google/cloud/tools/core-plugin:google-cloud-tools-core-as"]},
    deps = [
        ":com.google.gct.login",
        "//prebuilts/studio/intellij-sdk:studio-sdk-plugin-git4idea",
        "//prebuilts/studio/intellij-sdk:studio-sdk-plugin-gradle-java",
    ],
)

studio_plugin(
    name = "com.google.gct.login",
    directory = "google-login-as",
    libs = [
        "//tools/studio/google/cloud/tools/google-login-plugin/third_party/jackson-core-asl:jackson-core-asl-1.9.11",
        "//tools/studio/google/cloud/tools/google-login-plugin/third_party/javax-servlet-api:javax.servlet-api-3.0.1",
        "//tools/studio/google/cloud/tools/google-login-plugin/lib:google-api-services-oauth2-v2-rev70-1.18.0-rc",
        "//tools/studio/google/cloud/tools/google-login-plugin/lib:google-gct-login-context-pg",
        "//tools/studio/google/cloud/tools/google-login-plugin/lib:google-http-client-jackson-1.18.0-rc",
        "//tools/studio/google/cloud/tools/google-login-plugin/lib:google.gdt.eclipse.login.common",
        "//prebuilts/tools/common/google-api-java-client/1.20.0:google-api-java-client",
    ],
    licenses = [
        "//tools/studio/google/cloud/tools/google-login-plugin/lib:licenses",
    ],
    modules = {"google-login-as.jar": ["//tools/studio/google/cloud/tools/google-login-plugin:google-login-as"]},
)

studio_plugin(
    name = "com.google.cluestick.studioclient",
    directory = "google-samples",
    modules = {"google-samples.jar": ["//tools/studio/google/samples:google-samples"]},
    deps = [
        ":com.google.gct.login",
        ":org.jetbrains.android",
    ],
)

studio_plugin(
    name = "com.android.tools.idea.smali",
    directory = "smali",
    modules = {"smali.jar": ["//tools/adt/idea/smali:intellij.android.smali"]},
)

studio_plugin(
    name = "com.google.gct.test.recorder",
    directory = "test-recorder",
    libs = [
        "//tools/studio/google/cloud/testing/test-recorder/lib:uiautomatorviewer",
        "//tools/studio/google/cloud/testing/test-recorder/lib:swt",
    ],
    licenses = [
        "//tools/studio/google/cloud/testing/test-recorder/lib:licenses",
    ],
    modules = {"test-recorder.jar": ["//tools/studio/google/cloud/testing/test-recorder:test-recorder"]},
    deps = [
        ":org.jetbrains.android",
    ],
)

studio_plugin(
    name = "com.google.urlassistant",
    directory = "url-assistant",
    modules = {"url-assistant.jar": ["//tools/vendor/google/url-assistant:url-assistant"]},
    deps = [
        ":org.jetbrains.android",
        "//prebuilts/studio/intellij-sdk:studio-sdk-plugin-Groovy",
        "//prebuilts/studio/intellij-sdk:studio-sdk-plugin-Kotlin",
    ],
)

android_studio(
    name = "android-studio",
    codesign_entitlements = "codesign/entitlements.xml",
    codesign_filelist = "codesign/filelist",
    files_win = {
        "safemode/win/studio_safe.bat": "bin/studio_safe.bat",
    },
    jre = "//prebuilts/studio/jdk:jdk-bundle",
    platform = "//prebuilts/studio/intellij-sdk:studio-sdk",
    plugins = [
        ":org.jetbrains.android",
        ":com.android.tools.apk",
        ":com.android.tools.ndk",
        ":com.android.tools.design",
        ":androidx.compose.plugins.idea",
        ":com.google.services.firebase",
        ":com.google.gct.directaccess",
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
    searchable_options = "//tools/adt/idea/searchable-options",
    version_code_name = "Flamingo",
    version_micro_patch = "1.1",
    # This will get added to the end of the full-version string.
    # TODO(b/240707039): automate this.
    version_release_number = 1,
    version_type = "Canary",
    # version_update_message = "<html>this is optional</html>",
    visibility = ["//visibility:public"],
)

py_test(
    name = "test_studio",
    srcs = ["tests/test_studio.py"],
    data = [
        "tests/expected_linux.txt",
        "tests/expected_mac.txt",
        "tests/expected_mac_arm.txt",
        "tests/expected_win.txt",
        ":android-studio.linux.zip",
        ":android-studio.mac.zip",
        ":android-studio.mac_arm.zip",
        ":android-studio.win.zip",
    ],
)

py_test(
    name = "test_updater",
    timeout = "long",  # b/233245811
    srcs = ["tests/test_updater.py"],
    data = [
        ":android-studio.linux.zip",
        ":android-studio.mac.zip",
        ":android-studio.mac_arm.zip",
        ":android-studio.win.zip",
        ":updater",
        ":updater.jar",
    ],
    tags = ["no_windows"],  # b/233968084
)

py_binary(
    name = "stamper",
    srcs = ["stamper.py"],
    visibility = ["//visibility:public"],
)

py_binary(
    name = "check_plugin",
    srcs = ["check_plugin.py"],
    visibility = ["//visibility:public"],
)

py_test(
    name = "tools_test",
    srcs = ["tools_test.py"],
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
        "no_mac",
        "no_windows",
    ],
    visibility = ["//visibility:public"],
    runtime_deps = [
        "//prebuilts/studio/intellij-sdk:studio-sdk-updater",
        "//tools/adt/idea/.idea/libraries:studio-analytics-proto",
        "//tools/adt/idea/studio-updater:intellij.android.updater.studio-updater.jar",  # Adds analytics to the updater
        "//tools/analytics-library/shared:analytics-shared.jar",
        "//tools/analytics-library/tracker:analytics-tracker.jar",
        "//tools/base/annotations:studio.android.sdktools.android-annotations.jar",
        "//tools/base/common:studio.android.sdktools.common.jar",
        "@maven//:com.google.code.gson.gson",
        "@maven//:com.google.guava.failureaccess",
        "@maven//:com.google.guava.guava",
        "@maven//:com.google.j2objc.j2objc-annotations",
        "@maven//:com.google.protobuf.protobuf-java",
        "@maven//:com.nothome.javaxdelta",
        "@maven//:it.unimi.dsi.fastutil",
        "@maven//:log4j.log4j",
        "@maven//:net.java.dev.jna.jna",
        "@maven//:net.java.dev.jna.jna-platform",
        "@maven//:org.jetbrains.kotlin.kotlin-stdlib",
        "@maven//:org.jetbrains.kotlin.kotlin-stdlib-common",
        "@maven//:org.jetbrains.kotlin.kotlin-stdlib-jdk7",
        "@maven//:org.jetbrains.kotlin.kotlin-stdlib-jdk8",
    ],
)

# managed by go/iml_to_build
iml_module(
    name = "studio",
    iml_files = ["studio.iml"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//prebuilts/studio/intellij-sdk:studio-sdk",
        "//prebuilts/studio/intellij-sdk:studio-sdk-plugin-gradle",
        "//tools/adt/idea/android-plugin:intellij.android.plugin[module]",
        "//tools/vendor/google/android-apk[module]",
        "//tools/adt/idea/compose-ide-plugin:intellij.android.compose-ide-plugin[module]",
        "//tools/adt/idea/design-plugin:intellij.android.design-plugin[module]",
        "//tools/vendor/google/android-ndk[module]",
        "//tools/studio/google/cloud/tools/android-studio-plugin:google-cloud-tools-as[module]",
        "//tools/vendor/google/firebase[module]",
        "//tools/studio/google/cloud/testing/directaccess[module]",
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
        "//prebuilts/studio/intellij-sdk:studio-sdk-plugin-git4idea",
        "//prebuilts/studio/intellij-sdk:studio-sdk-plugin-github",
        "//prebuilts/studio/intellij-sdk:studio-sdk-plugin-gradle-java",
        "//prebuilts/studio/intellij-sdk:studio-sdk-plugin-Groovy",
        "//prebuilts/studio/intellij-sdk:studio-sdk-plugin-hg4idea",
        "//prebuilts/studio/intellij-sdk:studio-sdk-plugin-toml",
        "//prebuilts/studio/intellij-sdk:studio-sdk-plugin-IntelliLang",
        "//prebuilts/studio/intellij-sdk:studio-sdk-plugin-java-decompiler",
        "//prebuilts/studio/intellij-sdk:studio-sdk-plugin-java-i18n",
        "//prebuilts/studio/intellij-sdk:studio-sdk-plugin-java-ide-customization",
        "//prebuilts/studio/intellij-sdk:studio-sdk-plugin-junit",
        "//prebuilts/studio/intellij-sdk:studio-sdk-plugin-Kotlin",
        "//prebuilts/studio/intellij-sdk:studio-sdk-plugin-maven-model",
        "//prebuilts/studio/intellij-sdk:studio-sdk-plugin-platform-images",
        "//prebuilts/studio/intellij-sdk:studio-sdk-plugin-properties",
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
        "//prebuilts/studio/intellij-sdk:studio-sdk-plugin-c-clangd",
        "//prebuilts/studio/intellij-sdk:studio-sdk-plugin-c-plugin",
        "//prebuilts/studio/intellij-sdk:studio-sdk-plugin-cidr-base-plugin",
        "//prebuilts/studio/intellij-sdk:studio-sdk-plugin-cidr-debugger-plugin",
        "//prebuilts/studio/intellij-sdk:studio-sdk-plugin-completionMlRanking",
        "//prebuilts/studio/intellij-sdk:studio-sdk-plugin-ml-models-local",
    ],
)

# managed by go/iml_to_build
iml_module(
    name = "intellij.android.studio.tests",
    data = [
        # These are to be able to create patches in the test.
        ":updater",
        ":updater.jar",
    ],
    iml_files = ["tests/intellij.android.studio.tests.iml"],
    tags = [
        "no_mac",
        "no_windows",
    ],
    test_class = "com.android.testutils.JarTestSuite",
    test_data = [
        ":android-studio.linux.zip",
        ":android-studio.mac.zip",
        ":android-studio.win.zip",
        ":android-studio.plugin.lst",
    ],
    test_srcs = ["tests/testSrc"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//prebuilts/studio/intellij-sdk:studio-sdk[test]",
        "//tools/base/perf-logger:studio.perf-logger[module, test]",
        "//tools/base/testutils:studio.android.sdktools.testutils[module, test]",
    ],
)

# managed by go/iml_to_build
iml_module(
    name = "intellij.android.studio.integration",
    enable_tests = False,
    iml_files = ["integration/intellij.android.studio.integration/intellij.android.studio.integration.iml"],
    test_srcs = ["integration/intellij.android.studio.integration/testSrc"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//prebuilts/tools/common/m2:junit-4.12[test]",
        "//tools/adt/idea/as-driver:as-driver.utils[module, test]",
        "//tools/base/testutils:studio.android.sdktools.testutils[module, test]",
        "//prebuilts/studio/intellij-sdk:studio-sdk[test]",
        "//tools/adt/idea/.idea/libraries:truth[test]",
    ],
)

iml_test(
    name = "StartUpTest",
    data = select({
        "@platforms//os:linux": [
            "//tools/adt/idea/studio:android-studio.linux.zip",
        ],
        "@platforms//os:macos": [
            "//tools/adt/idea/studio:android-studio.mac.zip",
        ],
        "@platforms//os:windows": [
            "//tools/adt/idea/studio:android-studio.win.zip",
        ],
    }),
    module = ":intellij.android.studio.integration",
    tags_linux = ["block-network"],
    tags_windows = ["block-network"],
    test_class = "com.android.tools.idea.StartUpTest",
)

iml_test(
    name = "UpdateTest",
    data = select({
        "@platforms//os:linux": [
            ":android-studio.linux.zip",
        ],
        "@platforms//os:macos": [
            ":android-studio.mac.zip",
        ],
        "@platforms//os:windows": [
            ":android-studio.win.zip",
        ],
    }) + [
        ":updater",
        ":updater.jar",
    ],
    module = ":intellij.android.studio.integration",
    tags_linux = ["block-network"],
    tags_windows = ["block-network"],
    test_class = "com.android.tools.idea.UpdateTest",
)

# TODO(b/239839457): Remove this export when a real metadata file is generated.
exports_files(["metadata.textproto.template"])
