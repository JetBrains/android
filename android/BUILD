load("//tools/base/bazel:bazel.bzl", "iml_module")
load("//tools/base/bazel:maven.bzl", "maven_repo")
load("//tools/base/bazel:maven.bzl", "maven_java_library")

# managed by go/iml_to_build
iml_module(
    name = "intellij.android.core",
    # do not sort: must match IML order
    srcs = [
        "resources",
        "src",
        "gen",
    ],
    bundle_data = [
        "//tools/adt/idea/android/annotations",
        "//tools/adt/idea/android/lib:licenses",
        "//tools/adt/idea/android/lib:bundled-libs",
        "//tools/adt/idea/android/lib:sample-data",
        "//tools/adt/idea/android:profiler-artifacts",
        "//tools/adt/idea/android:aia-artifacts",
    ] + glob([
        "build.xml",
    ]),
    iml_files = ["intellij.android.core.iml"],
    javacopts = ["-Xep:MissingCasesInEnumSwitch:WARN"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    exports = ["//tools/adt/idea/build-common:intellij.android.buildCommon"],
    # do not sort: must match IML order
    runtime_deps = [
        "//tools/idea/xml/relaxng:intellij.relaxng",
        "//tools/idea/platform/tasks-platform-impl:intellij.platform.tasks.impl",
        "//tools/idea/json:intellij.json",
        "//tools/idea/platform/credential-store:intellij.platform.credentialStore",
        "//tools/idea/jvm/jvm-analysis-impl:intellij.jvm.analysis.impl",
        "//tools/idea/java/compiler/instrumentation-util-8:intellij.java.compiler.instrumentationUtil.java8",
        "//tools/idea/plugins/IntelliLang/intellilang-jps-plugin:intellij.java.langInjection.jps",
        "//tools/adt/idea/adt-branding:intellij.android.adt.branding",
    ],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/java/openapi:intellij.java[module]",
        "//tools/idea/xml/dom-openapi:intellij.xml.dom[module]",
        "//tools/idea/java/execution/impl:intellij.java.execution.impl[module]",
        "//tools/idea/xml/dom-impl:intellij.xml.dom.impl[module]",
        "//tools/idea:intellij.platform.jps.build_and_others[module]",
        "//tools/idea/xml/impl:intellij.xml.impl[module]",
        "//tools/idea/platform/xdebugger-impl:intellij.platform.debugger.impl[module]",
        "//tools/idea/java/execution/openapi:intellij.java.execution[module]",
        "//tools/idea/platform/smRunner:intellij.platform.smRunner[module]",
        "//tools/idea/plugins/junit:intellij.junit[module]",
        "//tools/idea/plugins/maven:intellij.maven[module]",
        "//tools/idea:intellij.java.impl[module]",
        "//tools/idea/plugins/properties:intellij.properties[module]",
        "//tools/idea/plugins/testng:intellij.testng[module]",
        "//tools/adt/idea/build-common:intellij.android.buildCommon[module]",
        "//tools/adt/idea/android-common:intellij.android.common[module]",
        "//tools/adt/idea/rt:intellij.android.rt[module]",
        "//tools/idea/java/java-indexing-api:intellij.java.indexing[module]",
        "//tools/idea/spellchecker:intellij.spellchecker[module]",
        "//tools/base/draw9patch:studio.android.sdktools.draw9patch[module]",
        "//tools/base/build-system:studio.android.sdktools.manifest-merger[module]",
        "//tools/idea/.idea/libraries:freemarker",
        "//tools/idea/images:intellij.platform.images[module]",
        "//tools/idea/platform/platform-api:intellij.platform.ide[module]",
        "//tools/idea/plugins/eclipse:intellij.eclipse[module]",
        "//tools/idea/platform/external-system-api:intellij.platform.externalSystem[module]",
        "//tools/idea/.idea/libraries:gson",
        "//tools/idea/plugins/groovy:intellij.groovy[module]",
        "//tools/base/perflib:studio.android.sdktools.perflib[module]",
        "//tools/idea/.idea/libraries:swingx",
        "//tools/idea/platform/util:intellij.platform.util[module]",
        "//tools/idea/plugins/properties/properties-psi-api:intellij.properties.psi[module]",
        "//tools/idea/platform/bootstrap:intellij.platform.bootstrap[module]",
        "//tools/idea/.idea/libraries:jcip",
        "//tools/adt/idea/adt-ui:intellij.android.adt.ui[module]",
        "//tools/adt/idea/adt-ui-model:intellij.android.adt.ui.model[module]",
        "//tools/adt/idea/android/lib:spantable",
        "//tools/idea/.idea/libraries:jsr305",
        "//prebuilts/tools/common/m2/repository/org/tinyjee/jgraphx/jgraphx/3.4.1.3:jar",
        "//tools/base/repository:studio.android.sdktools.repository[module]",
        "//tools/base/instant-run:studio.android.sdktools.instant-run-client[module]",
        "//tools/base/instant-run:studio.android.sdktools.instant-run-common[module]",
        "//tools/idea/.idea/libraries:jna",
        "//tools/data-binding:studio.baseLibrary[module]",
        "//tools/data-binding:studio.baseLibrarySupport[module]",
        "//tools/data-binding:studio.compilerCommon[module]",
        "//tools/data-binding:studio.compiler[module]",
        "//tools/idea/platform/external-system-impl:intellij.platform.externalSystem.impl[module]",
        "//tools/idea/.idea/libraries:Gradle",
        "//tools/idea/plugins/gradle:intellij.gradle[module]",
        "//tools/idea/plugins/gradle/java:intellij.gradle.java[module]",
        "//tools/adt/idea/layoutlib:intellij.android.layoutlib[module]",
        "//tools/idea/.idea/libraries:baksmali",
        "//tools/idea/.idea/libraries:dexlib2",
        "//tools/base/apkparser:studio.android.sdktools.binary-resources[module]",
        "//tools/base/apkparser/analyzer:studio.android.sdktools.analyzer[module]",
        "//tools/base/pixelprobe:studio.android.sdktools.pixelprobe[module]",
        "//tools/analytics-library/tracker:analytics-tracker[module]",
        "//tools/analytics-library/shared:analytics-shared[module]",
        "//tools/base/common:studio.android.sdktools.common[module]",
        "//tools/adt/idea/observable:intellij.android.observable[module]",
        "//tools/idea/plugins/gradle/tooling-extension-impl:intellij.gradle.toolingExtension.impl[module]",
        "//tools/adt/idea/wizard:intellij.android.wizard[module]",
        "//tools/adt/idea/wizard-model:intellij.android.wizard.model[module]",
        "//tools/idea/.idea/libraries:commons-io",
        "//tools/adt/idea/smali:intellij.android.smali[module]",
        "//tools/base/build-system/java-lib-plugin/java-lib-model:studio.android.sdktools.java-lib-model[module]",
        "//tools/base/build-system/java-lib-plugin/java-lib-model-builder:studio.android.sdktools.java-lib-model-builder[module]",
        "//tools/base/sdk-common:studio.android.sdktools.sdk-common[module]",
        "//tools/base/flags:studio.android.sdktools.flags[module]",
        "//tools/idea/platform/lang-api:intellij.platform.lang[module]",
        "//tools/adt/idea/observable-ui:intellij.android.observable.ui[module]",
        "//tools/adt/idea/artwork:intellij.android.artwork[module]",
        "//tools/adt/idea/project-system[module]",
        "//tools/adt/idea/apkanalyzer[module]",
        "//tools/adt/idea/android-lang[module]",
        "//tools/adt/idea/android-adb[module]",
        "//tools/adt/idea/android/lib:instantapps-api-1.0",
        "//tools/idea/.idea/libraries:batik",
        "//tools/idea/.idea/libraries:xml-apis-ext",
        "//tools/analytics-library/crash:analytics-crash[module]",
        "//tools/idea/.idea/libraries:aapt-proto-jarjar",
        "//tools/adt/idea/android/lib:pepk",
        "//tools/idea/.idea/libraries:protobuf",
        "//tools/idea/.idea/libraries:studio-analytics-proto",
        "//tools/adt/idea/android/src/com/android/tools/idea/gradle/project/sync/ng/nosyncbuilder/proto:nosyncbuilder-jarjar",
        "//tools/adt/idea/deploy:intellij.android.deploy[module]",
    ],
)

# managed by go/iml_to_build
iml_module(
    name = "intellij.android.core.tests",
    iml_files = ["intellij.android.core.tests.iml"],
    javacopts = ["-XepAllErrorsAsWarnings"],
    test_class = "com.android.tools.idea.IdeaTestSuite",
    # keep sorted
    test_data = [
        ":android-gradle-1.5.0_repo.zip",
        ":com.android.tools.build_gradle",
        ":test_deps.zip",
        "//prebuilts/studio/jdk",
        "//prebuilts/studio/layoutlib:buildprop",
        "//prebuilts/studio/layoutlib:data/fonts",
        "//prebuilts/studio/layoutlib:data/res",
        "//prebuilts/studio/sdk:build-tools/latest",
        "//prebuilts/studio/sdk:cmake",
        "//prebuilts/studio/sdk:constraint-layout_latest",
        "//prebuilts/studio/sdk:databinding_latest",
        "//prebuilts/studio/sdk:docs",
        "//prebuilts/studio/sdk:espresso_latest",
        "//prebuilts/studio/sdk:gms_latest",
        "//prebuilts/studio/sdk:licenses",
        "//prebuilts/studio/sdk:ndk-bundle",
        "//prebuilts/studio/sdk:platform-tools",
        "//prebuilts/studio/sdk:platforms/latest",
        "//prebuilts/studio/sdk:platforms/latest-preview",
        "//prebuilts/studio/sdk:sources",
        "//prebuilts/studio/sdk:support_latest",
        "//prebuilts/studio/sdk:test-runner_latest",
        "//prebuilts/studio/sdk:typos",
        "//prebuilts/studio/sdk:wearable_latest",
        "//prebuilts/tools/common/kotlin-plugin:kotlin-plugin-for-studio",
        "//prebuilts/tools/common/offline-m2:android-gradle-2.2.0",
        "//tools/adt/idea/adt-ui/lib/libwebp",
        "//tools/adt/idea/android/annotations",
        "//tools/adt/idea/android/lib:androidWidgets",
        "//tools/adt/idea/android/lib:sampleData",
        "//tools/adt/idea/android/testData",
        "//tools/adt/idea/artwork:device-art-resources",
        "//tools/base/build-system:gradle-distrib",
        "//tools/base/build-system:studio_repo.zip",
        "//tools/base/templates",
        "//tools/base/third_party/kotlin:kotlin-m2repository.zip",
        "//tools/data-binding:data_binding_runtime.zip",
        "//tools/external/gradle:gradle-distrib-2.4",
        "//tools/idea:platform-build-info",
        "//tools/idea/java/jdkAnnotations",
        "//tools/idea/java/mockJDK-1.7",
    ],
    # do not sort: must match IML order
    test_runtime_deps = [
        "//tools/idea/platform/tasks-platform-impl:intellij.platform.tasks.impl",
        "//tools/idea/json:intellij.json",
        "//tools/idea/platform/credential-store:intellij.platform.credentialStore",
        "//tools/idea/java/compiler/instrumentation-util-8:intellij.java.compiler.instrumentationUtil.java8",
        "//tools/idea/jvm/jvm-analysis-impl:intellij.jvm.analysis.impl",
        "//tools/idea/plugins/IntelliLang/intellilang-jps-plugin:intellij.java.langInjection.jps",
    ],
    test_shard_count = 8,
    test_srcs = ["testSrc"],
    test_tags = [
        "slow",
        "cpu:3",
        "block-network",
        "no_test_mac",
        "no_test_windows",
    ],
    test_timeout = "eternal",
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/platform/lang-impl:intellij.platform.lang.impl[module, test]",
        "//tools/idea/platform/testFramework:intellij.platform.testFramework[module, test]",
        "//tools/idea/.idea/libraries:KotlinTest[test]",
        "//tools/adt/idea/android:intellij.android.core[module, test]",
        "//tools/idea/spellchecker:intellij.spellchecker[module, test]",
        "//tools/idea/xml/xml-psi-api:intellij.xml.psi[module, test]",
        "//tools/adt/idea/adt-ui:intellij.android.adt.ui[module, test]",
        "//tools/adt/idea/adt-ui-model:intellij.android.adt.ui.model[module, test]",
        "//tools/idea/.idea/libraries:Mocks[test]",
        "//tools/idea/.idea/libraries:mockito[test]",
        "//tools/idea/.idea/libraries:truth[test]",
        "//tools/idea:intellij.platform.jps.build_and_others[module, test]",
        "//tools/idea/xml/dom-impl:intellij.xml.dom.impl[module, test]",
        "//tools/base/testutils:studio.android.sdktools.testutils[module, test]",
        "//tools/idea/.idea/libraries:freemarker[test]",
        "//tools/idea/platform/external-system-impl:intellij.platform.externalSystem.impl[module, test]",
        "//tools/adt/idea/observable:intellij.android.observable[module, test]",
        "//tools/idea/java/compiler/openapi:intellij.java.compiler[module, test]",
        "//tools/base/common:studio.android.sdktools.common[module, test]",
        "//tools/idea/plugins/gradle/tooling-extension-api:intellij.gradle.toolingExtension[module, test]",
        "//tools/base/build-system:studio.android.sdktools.manifest-merger[module, test]",
        "//tools/idea/.idea/libraries:Eclipse[test]",
        "//tools/idea/xml/xml-psi-impl:intellij.xml.psi.impl[module, test]",
        "//tools/idea/xml/impl:intellij.xml.impl[module, test]",
        "//tools/idea/plugins/gradle:intellij.gradle[module, test]",
        "//tools/idea/plugins/gradle:intellij.gradle.tests[module, test]",
        "//tools/idea/plugins/groovy/groovy-psi:intellij.groovy.psi[module, test]",
        "//tools/adt/idea/layoutlib:intellij.android.layoutlib[module, test]",
        "//tools/idea/plugins/properties/properties-psi-api:intellij.properties.psi[module, test]",
        "//tools/base/instant-run:studio.android.sdktools.instant-run-client[module, test]",
        "//tools/idea/platform/diff-impl:intellij.platform.diff.impl[module, test]",
        "//tools/idea/.idea/libraries:dexlib2[test]",
        "//tools/idea/plugins/junit:intellij.junit[module, test]",
        "//tools/idea/java/execution/impl:intellij.java.execution.impl[module, test]",
        "//tools/adt/idea/android-test-framework:intellij.android.testFramework[module, test]",
        "//tools/idea/.idea/libraries:jsr305[test]",
        "//tools/analytics-library/shared:analytics-shared[module, test]",
        "//tools/analytics-library/testing:android.sdktools.analytics-testing[module, test]",
        "//tools/analytics-library/tracker:analytics-tracker[module, test]",
        "//tools/adt/idea/wizard:intellij.android.wizard[module, test]",
        "//tools/adt/idea/wizard-model:intellij.android.wizard.model[module, test]",
        "//tools/idea/.idea/libraries:guava-testlib[test]",
        "//tools/base/fakeadbserver:studio.android.sdktools.fakeadbserver[module, test]",
        "//tools/adt/idea/adt-branding:intellij.android.adt.branding[module, test]",
        "//tools/adt/idea/sdk-updates:intellij.android.sdkUpdates[module, test]",
        "//tools/base/build-system/java-lib-plugin/java-lib-model:studio.android.sdktools.java-lib-model[module, test]",
        "//tools/idea/.idea/libraries:equalsverifier[test]",
        "//tools/base/apkparser/analyzer:studio.android.sdktools.analyzer[module, test]",
        "//tools/base/sdk-common:studio.android.sdktools.sdk-common[module, test]",
        "//tools/base/flags:studio.android.sdktools.flags[module, test]",
        "//tools/idea/plugins/properties/properties-psi-impl:intellij.properties.psi.impl[module, test]",
        "//tools/adt/idea/artwork:intellij.android.artwork[module, test]",
        "//tools/adt/idea/android-common:intellij.android.common[module, test]",
        "//tools/idea/images:intellij.platform.images[module, test]",
        "//tools/adt/idea/apkanalyzer[module, test]",
        "//tools/idea/.idea/libraries:sqlite[test]",
        "//tools/adt/idea/project-system[module, test]",
        "//tools/adt/idea/android-adb[module, test]",
        "//tools/adt/idea/project-system-gradle[module, test]",
        "//tools/adt/idea/project-system:project-system-tests[module, test]",
        "//tools/adt/idea/adt-testutils[module, test]",
        "//tools/adt/idea/android-lang[module, test]",
        "//tools/adt/idea/android/lib:instantapps-api-1.0",
        "//tools/analytics-library/crash:analytics-crash[module, test]",
        "//tools/idea/.idea/libraries:commons-io",
        "//tools/idea/.idea/libraries:aapt-proto-jarjar",
        "//tools/adt/idea/android/lib:pepk[test]",
        "//tools/idea/.idea/libraries:protobuf",
        "//tools/idea/.idea/libraries:studio-analytics-proto",
        "//tools/adt/idea/android/src/com/android/tools/idea/gradle/project/sync/ng/nosyncbuilder/proto:nosyncbuilder-jarjar[test]",
        "//tools/idea/.idea/libraries:jimfs[test]",
        "//tools/adt/idea/android-kotlin:intellij.android.kotlin.extensions[module, test]",
        "//tools/adt/idea/android-kotlin:intellij.android.kotlin.idea[module, test]",
        "//tools/adt/idea/android-kotlin:intellij.android.kotlin.output.parser[module, test]",
        "//tools/adt/idea/deploy:intellij.android.deploy[module, test]",
    ],
)

filegroup(
    name = "profiler-artifacts",
    srcs = select({
        "//tools/base/bazel:windows": ["//tools/base/profiler:studio-profiler-grpc-1.0-jarjar.jar"],
        "//conditions:default": [
            "//tools/base/profiler:studio-profiler-grpc-1.0-jarjar.jar",
            "//tools/base/profiler/app:perfa.jar",
            "//tools/base/profiler/app:perfa_okhttp.dex",
            "//tools/base/profiler/native/perfa:android",
            "//tools/base/profiler/native/perfd:android",
            "//tools/base/profiler/transform:profilers-transform.jar",
        ],
    }),
    visibility = ["//visibility:public"],
)

filegroup(
    name = "aapt-proto-artifacts",
    srcs = [
        "//tools/base/sdk-common/aapt-proto-jar-generator:aapt-proto-jarjar.jar",
    ],
    visibility = ["//visibility:public"],
)

filegroup(
    name = "aia-artifacts",
    srcs = [
        "//tools/base/sdk-common/aia-manifest-jar-generator:aia-manifest-proto-1.0-jarjar.jar",
    ],
    visibility = ["//visibility:public"],
)

filegroup(
    name = "analytics-artifacts",
    srcs = ["//tools/analytics-library/protos/src/main/proto:studio.proto"],
    visibility = ["//visibility:public"],
)

filegroup(
    name = "nosyncbuilder-artifacts",
    srcs = [
        "//tools/adt/idea/android/src/com/android/tools/idea/gradle/project/sync/ng/nosyncbuilder/proto:nosyncbuilder-jarjar.jar",
    ],
    visibility = ["//visibility:public"],
)

# Various artifacts that the ANT and within-idea studio build depend on.
filegroup(
    name = "artifacts",
    srcs = [
        ":aapt-proto-artifacts",
        ":aia-artifacts",
        ":analytics-artifacts",
        ":nosyncbuilder-artifacts",
        ":profiler-artifacts",
        "//tools/adt/idea/swingp:swing-instrumenter_deploy.jarjar.jar",
        "//tools/base/build-system:gradle-api_neverlink",  # Provided when run in Gradle.
        "//tools/base/deploy/installer:android",
        "//tools/base/deploy/proto:java_proto",
        "//tools/base/third_party/kotlin:kotlin-m2repository.zip",
    ],
)

maven_repo(
    name = "test_deps",
    # keep sorted: for buildifier
    artifacts = [
        "//prebuilts/tools/common/m2/repository/android/arch/core/common/1.1.0:jar",
        "//prebuilts/tools/common/m2/repository/android/arch/core/common/1.1.1:jar",
        "//prebuilts/tools/common/m2/repository/android/arch/core/runtime/1.1.0:aar",
        "//prebuilts/tools/common/m2/repository/android/arch/core/runtime/1.1.1:aar",
        "//prebuilts/tools/common/m2/repository/android/arch/lifecycle/common/1.1.0:jar",
        "//prebuilts/tools/common/m2/repository/android/arch/lifecycle/common/1.1.1:jar",
        "//prebuilts/tools/common/m2/repository/android/arch/lifecycle/compiler/1.1.1:jar",
        "//prebuilts/tools/common/m2/repository/android/arch/lifecycle/extensions/1.1.1:aar",
        "//prebuilts/tools/common/m2/repository/android/arch/lifecycle/livedata-core/1.1.0:aar",
        "//prebuilts/tools/common/m2/repository/android/arch/lifecycle/livedata-core/1.1.1:aar",
        "//prebuilts/tools/common/m2/repository/android/arch/lifecycle/livedata/1.1.1:aar",
        "//prebuilts/tools/common/m2/repository/android/arch/lifecycle/runtime/1.1.0:aar",
        "//prebuilts/tools/common/m2/repository/android/arch/lifecycle/runtime/1.1.1:aar",
        "//prebuilts/tools/common/m2/repository/android/arch/lifecycle/viewmodel/1.1.0:aar",
        "//prebuilts/tools/common/m2/repository/android/arch/lifecycle/viewmodel/1.1.1:aar",
        "//prebuilts/tools/common/m2/repository/android/arch/navigation/navigation-common-ktx/1.0.0-alpha04:aar",
        "//prebuilts/tools/common/m2/repository/android/arch/navigation/navigation-common/1.0.0-alpha04:aar",
        "//prebuilts/tools/common/m2/repository/android/arch/navigation/navigation-fragment-ktx/1.0.0-alpha04:aar",
        "//prebuilts/tools/common/m2/repository/android/arch/navigation/navigation-fragment/1.0.0-alpha04:aar",
        "//prebuilts/tools/common/m2/repository/android/arch/navigation/navigation-runtime-ktx/1.0.0-alpha04:aar",
        "//prebuilts/tools/common/m2/repository/android/arch/navigation/navigation-runtime/1.0.0-alpha04:aar",
        "//prebuilts/tools/common/m2/repository/android/arch/persistence/db-framework/1.0.0:aar",
        "//prebuilts/tools/common/m2/repository/android/arch/persistence/db/1.0.0:aar",
        "//prebuilts/tools/common/m2/repository/android/arch/persistence/room/common/1.0.0:jar",
        "//prebuilts/tools/common/m2/repository/android/arch/persistence/room/runtime/1.0.0:aar",
        "//prebuilts/tools/common/m2/repository/androidx/annotation/annotation/1.0.0-alpha1:jar",
        "//prebuilts/tools/common/m2/repository/androidx/appcompat/appcompat/1.0.0-alpha1:aar",
        "//prebuilts/tools/common/m2/repository/androidx/arch/core/core-common/2.0.0-alpha1:jar",
        "//prebuilts/tools/common/m2/repository/androidx/arch/core/core-runtime/2.0.0-alpha1:aar",
        "//prebuilts/tools/common/m2/repository/androidx/asynclayoutinflater/asynclayoutinflater/1.0.0-alpha1:aar",
        "//prebuilts/tools/common/m2/repository/androidx/collection/collection/1.0.0-alpha1:jar",
        "//prebuilts/tools/common/m2/repository/androidx/constraintlayout/constraintlayout-solver/1.1.0:jar",
        "//prebuilts/tools/common/m2/repository/androidx/constraintlayout/constraintlayout/1.1.0:aar",
        "//prebuilts/tools/common/m2/repository/androidx/coordinatorlayout/coordinatorlayout/1.0.0-alpha1:aar",
        "//prebuilts/tools/common/m2/repository/androidx/core/core/1.0.0-alpha1:aar",
        "//prebuilts/tools/common/m2/repository/androidx/cursoradapter/cursoradapter/1.0.0-alpha1:aar",
        "//prebuilts/tools/common/m2/repository/androidx/customview/customview/1.0.0-alpha1:aar",
        "//prebuilts/tools/common/m2/repository/androidx/documentfile/documentfile/1.0.0-alpha1:aar",
        "//prebuilts/tools/common/m2/repository/androidx/drawerlayout/drawerlayout/1.0.0-alpha1:aar",
        "//prebuilts/tools/common/m2/repository/androidx/fragment/fragment/1.0.0-alpha1:aar",
        "//prebuilts/tools/common/m2/repository/androidx/interpolator/interpolator/1.0.0-alpha1:aar",
        "//prebuilts/tools/common/m2/repository/androidx/legacy/legacy-support-core-ui/1.0.0-alpha1:aar",
        "//prebuilts/tools/common/m2/repository/androidx/legacy/legacy-support-core-utils/1.0.0-alpha1:aar",
        "//prebuilts/tools/common/m2/repository/androidx/lifecycle/lifecycle-common/2.0.0-alpha1:jar",
        "//prebuilts/tools/common/m2/repository/androidx/lifecycle/lifecycle-compiler/2.0.0-alpha1:jar",
        "//prebuilts/tools/common/m2/repository/androidx/lifecycle/lifecycle-livedata-core/2.0.0-alpha1:aar",
        "//prebuilts/tools/common/m2/repository/androidx/lifecycle/lifecycle-livedata/2.0.0-alpha1:aar",
        "//prebuilts/tools/common/m2/repository/androidx/lifecycle/lifecycle-runtime/2.0.0-alpha1:aar",
        "//prebuilts/tools/common/m2/repository/androidx/lifecycle/lifecycle-viewmodel/2.0.0-alpha1:aar",
        "//prebuilts/tools/common/m2/repository/androidx/loader/loader/1.0.0-alpha1:aar",
        "//prebuilts/tools/common/m2/repository/androidx/localbroadcastmanager/localbroadcastmanager/1.0.0-alpha1:aar",
        "//prebuilts/tools/common/m2/repository/androidx/print/print/1.0.0-alpha1:aar",
        "//prebuilts/tools/common/m2/repository/androidx/slice/slices-builders/1.0.0-alpha1:aar",
        "//prebuilts/tools/common/m2/repository/androidx/slice/slices-core/1.0.0-alpha1:aar",
        "//prebuilts/tools/common/m2/repository/androidx/slice/slices-view/1.0.0-alpha1:aar",
        "//prebuilts/tools/common/m2/repository/androidx/slidingpanelayout/slidingpanelayout/1.0.0-alpha1:aar",
        "//prebuilts/tools/common/m2/repository/androidx/swiperefreshlayout/swiperefreshlayout/1.0.0-alpha1:aar",
        "//prebuilts/tools/common/m2/repository/androidx/vectordrawable/vectordrawable-animated/1.0.0-alpha1:aar",
        "//prebuilts/tools/common/m2/repository/androidx/vectordrawable/vectordrawable/1.0.0-alpha1:aar",
        "//prebuilts/tools/common/m2/repository/androidx/viewpager/viewpager/1.0.0-alpha1:aar",
        "//prebuilts/tools/common/m2/repository/com/android/support/animated-vector-drawable/26.0.2:aar",
        "//prebuilts/tools/common/m2/repository/com/android/support/animated-vector-drawable/27.1.1:aar",
        "//prebuilts/tools/common/m2/repository/com/android/support/appcompat-v7/26.0.2:aar",
        "//prebuilts/tools/common/m2/repository/com/android/support/appcompat-v7/27.1.1:aar",
        "//prebuilts/tools/common/m2/repository/com/android/support/constraint/constraint-layout-solver/1.1.0:jar",
        "//prebuilts/tools/common/m2/repository/com/android/support/constraint/constraint-layout/1.1.0:aar",
        "//prebuilts/tools/common/m2/repository/com/android/support/design/27.1.1:aar",
        "//prebuilts/tools/common/m2/repository/com/android/support/leanback-v17/27.1.1:aar",
        "//prebuilts/tools/common/m2/repository/com/android/support/multidex/1.0.1:aar",
        "//prebuilts/tools/common/m2/repository/com/android/support/palette-v7/26.1.0:aar",
        "//prebuilts/tools/common/m2/repository/com/android/support/percent/27.1.1:aar",
        "//prebuilts/tools/common/m2/repository/com/android/support/recyclerview-v7/27.1.1:aar",
        "//prebuilts/tools/common/m2/repository/com/android/support/support-annotations/26.0.2:jar",
        "//prebuilts/tools/common/m2/repository/com/android/support/support-annotations/26.1.0:jar",
        "//prebuilts/tools/common/m2/repository/com/android/support/support-annotations/27.1.1:jar",
        "//prebuilts/tools/common/m2/repository/com/android/support/support-compat/26.0.2:aar",
        "//prebuilts/tools/common/m2/repository/com/android/support/support-compat/26.1.0:aar",
        "//prebuilts/tools/common/m2/repository/com/android/support/support-compat/27.1.1:aar",
        "//prebuilts/tools/common/m2/repository/com/android/support/support-core-ui/26.0.2:aar",
        "//prebuilts/tools/common/m2/repository/com/android/support/support-core-ui/26.1.0:aar",
        "//prebuilts/tools/common/m2/repository/com/android/support/support-core-ui/27.1.1:aar",
        "//prebuilts/tools/common/m2/repository/com/android/support/support-core-utils/26.0.2:aar",
        "//prebuilts/tools/common/m2/repository/com/android/support/support-core-utils/26.1.0:aar",
        "//prebuilts/tools/common/m2/repository/com/android/support/support-core-utils/27.1.1:aar",
        "//prebuilts/tools/common/m2/repository/com/android/support/support-fragment/26.0.2:aar",
        "//prebuilts/tools/common/m2/repository/com/android/support/support-fragment/26.1.0:aar",
        "//prebuilts/tools/common/m2/repository/com/android/support/support-fragment/27.1.1:aar",
        "//prebuilts/tools/common/m2/repository/com/android/support/support-media-compat/26.0.2:aar",
        "//prebuilts/tools/common/m2/repository/com/android/support/support-media-compat/26.1.0:aar",
        "//prebuilts/tools/common/m2/repository/com/android/support/support-media-compat/27.1.1:aar",
        "//prebuilts/tools/common/m2/repository/com/android/support/support-v13/26.0.2:aar",
        "//prebuilts/tools/common/m2/repository/com/android/support/support-v13/26.1.0:aar",
        "//prebuilts/tools/common/m2/repository/com/android/support/support-v13/27.1.1:aar",
        "//prebuilts/tools/common/m2/repository/com/android/support/support-v4/26.0.2:aar",
        "//prebuilts/tools/common/m2/repository/com/android/support/support-v4/26.1.0:aar",
        "//prebuilts/tools/common/m2/repository/com/android/support/support-v4/27.1.1:aar",
        "//prebuilts/tools/common/m2/repository/com/android/support/support-vector-drawable/26.0.2:aar",
        "//prebuilts/tools/common/m2/repository/com/android/support/support-vector-drawable/26.1.0:aar",
        "//prebuilts/tools/common/m2/repository/com/android/support/support-vector-drawable/27.1.1:aar",
        "//prebuilts/tools/common/m2/repository/com/android/support/test/espresso/espresso-core/3.0.2:aar",
        "//prebuilts/tools/common/m2/repository/com/android/support/test/espresso/espresso-idling-resource/3.0.2:aar",
        "//prebuilts/tools/common/m2/repository/com/android/support/test/monitor/1.0.2:aar",
        "//prebuilts/tools/common/m2/repository/com/android/support/test/rules/1.0.2:aar",
        "//prebuilts/tools/common/m2/repository/com/android/support/test/runner/1.0.2:aar",
        "//prebuilts/tools/common/m2/repository/com/android/support/transition/26.1.0:aar",
        "//prebuilts/tools/common/m2/repository/com/android/support/transition/27.1.1:aar",
        "//prebuilts/tools/common/m2/repository/com/android/support/wear/27.1.1:aar",
        "//prebuilts/tools/common/m2/repository/com/github/bumptech/glide/glide/3.8.0:jar",
        "//prebuilts/tools/common/m2/repository/com/google/android/gms/play-services-ads-license/12.0.1:aar",
        "//prebuilts/tools/common/m2/repository/com/google/android/gms/play-services-ads-lite-license/12.0.1:aar",
        "//prebuilts/tools/common/m2/repository/com/google/android/gms/play-services-ads-lite/12.0.1:aar",
        "//prebuilts/tools/common/m2/repository/com/google/android/gms/play-services-ads-lite/15.0.1:aar",
        "//prebuilts/tools/common/m2/repository/com/google/android/gms/play-services-ads/12.0.1:aar",
        "//prebuilts/tools/common/m2/repository/com/google/android/gms/play-services-ads/15.0.1:aar",
        "//prebuilts/tools/common/m2/repository/com/google/android/gms/play-services-audience/15.0.1:aar",
        "//prebuilts/tools/common/m2/repository/com/google/android/gms/play-services-auth-api-phone-license/12.0.1:aar",
        "//prebuilts/tools/common/m2/repository/com/google/android/gms/play-services-auth-api-phone/12.0.1:aar",
        "//prebuilts/tools/common/m2/repository/com/google/android/gms/play-services-auth-base-license/12.0.1:aar",
        "//prebuilts/tools/common/m2/repository/com/google/android/gms/play-services-auth-base/12.0.1:aar",
        "//prebuilts/tools/common/m2/repository/com/google/android/gms/play-services-auth/12.0.1:aar",
        "//prebuilts/tools/common/m2/repository/com/google/android/gms/play-services-base-license/12.0.1:aar",
        "//prebuilts/tools/common/m2/repository/com/google/android/gms/play-services-base/12.0.1:aar",
        "//prebuilts/tools/common/m2/repository/com/google/android/gms/play-services-base/15.0.1:aar",
        "//prebuilts/tools/common/m2/repository/com/google/android/gms/play-services-basement-license/12.0.1:aar",
        "//prebuilts/tools/common/m2/repository/com/google/android/gms/play-services-basement/12.0.1:aar",
        "//prebuilts/tools/common/m2/repository/com/google/android/gms/play-services-basement/15.0.1:aar",
        "//prebuilts/tools/common/m2/repository/com/google/android/gms/play-services-gass-license/12.0.1:aar",
        "//prebuilts/tools/common/m2/repository/com/google/android/gms/play-services-gass/12.0.1:aar",
        "//prebuilts/tools/common/m2/repository/com/google/android/gms/play-services-gass/15.0.1:aar",
        "//prebuilts/tools/common/m2/repository/com/google/android/gms/play-services-maps/15.0.1:aar",
        "//prebuilts/tools/common/m2/repository/com/google/android/gms/play-services-plus/15.0.1:aar",
        "//prebuilts/tools/common/m2/repository/com/google/android/gms/play-services-tasks-license/12.0.1:aar",
        "//prebuilts/tools/common/m2/repository/com/google/android/gms/play-services-tasks/12.0.1:aar",
        "//prebuilts/tools/common/m2/repository/com/google/android/gms/play-services-tasks/15.0.1:aar",
        "//prebuilts/tools/common/m2/repository/com/google/android/gms/play-services-wearable/15.0.1:aar",
        "//prebuilts/tools/common/m2/repository/com/google/android/support/wearable/2.3.0:aar",
        "//prebuilts/tools/common/m2/repository/com/google/android/things/androidthings/0.4-devpreview:jar",
        "//prebuilts/tools/common/m2/repository/com/google/android/things/contrib/driver-apa102/0.3:aar",
        "//prebuilts/tools/common/m2/repository/com/google/android/things/contrib/driver-bmx280/0.2:aar",
        "//prebuilts/tools/common/m2/repository/com/google/android/things/contrib/driver-button/0.3:aar",
        "//prebuilts/tools/common/m2/repository/com/google/android/things/contrib/driver-cap12xx/0.3:aar",
        "//prebuilts/tools/common/m2/repository/com/google/android/things/contrib/driver-gps/0.3:aar",
        "//prebuilts/tools/common/m2/repository/com/google/android/things/contrib/driver-ht16k33/0.3:aar",
        "//prebuilts/tools/common/m2/repository/com/google/android/things/contrib/driver-mma7660fc/0.2:aar",
        "//prebuilts/tools/common/m2/repository/com/google/android/things/contrib/driver-pwmservo/0.2:aar",
        "//prebuilts/tools/common/m2/repository/com/google/android/things/contrib/driver-pwmspeaker/0.2:aar",
        "//prebuilts/tools/common/m2/repository/com/google/android/things/contrib/driver-ssd1306/0.3:aar",
        "//prebuilts/tools/common/m2/repository/com/google/android/things/contrib/driver-tm1637/0.2:aar",
        "//prebuilts/tools/common/m2/repository/com/google/android/wearable/wearable/2.3.0:jar",
        "//prebuilts/tools/common/m2/repository/com/google/code/findbugs/jsr305/1.3.9:jar",
        "//prebuilts/tools/common/m2/repository/com/google/code/findbugs/jsr305/2.0.1:jar",
        "//prebuilts/tools/common/m2/repository/com/google/code/gson/gson/2.2.4:jar",
        "//prebuilts/tools/common/m2/repository/com/google/code/gson/gson/2.6.2:jar",
        "//prebuilts/tools/common/m2/repository/com/google/firebase/firebase-common-license/12.0.1:aar",
        "//prebuilts/tools/common/m2/repository/com/google/firebase/firebase-common/12.0.1:aar",
        "//prebuilts/tools/common/m2/repository/com/google/firebase/firebase-iid-license/12.0.1:aar",
        "//prebuilts/tools/common/m2/repository/com/google/firebase/firebase-iid/12.0.1:aar",
        "//prebuilts/tools/common/m2/repository/com/google/firebase/firebase-messaging-license/12.0.1:aar",
        "//prebuilts/tools/common/m2/repository/com/google/firebase/firebase-messaging/12.0.1:aar",
        "//prebuilts/tools/common/m2/repository/com/google/guava/guava/18.0:jar",
        "//prebuilts/tools/common/m2/repository/com/google/guava/guava/19.0:jar",
        "//prebuilts/tools/common/m2/repository/com/linkedin/testbutler/test-butler-app/1.3.1:apk",
        "//prebuilts/tools/common/m2/repository/com/squareup/javawriter/2.1.1:jar",
        "//prebuilts/tools/common/m2/repository/commons-lang/commons-lang/2.4:jar",
        "//prebuilts/tools/common/m2/repository/javax/annotation/javax.annotation-api/1.2:jar",
        "//prebuilts/tools/common/m2/repository/javax/inject/javax.inject/1:jar",
        "//prebuilts/tools/common/m2/repository/junit/junit/4.12:jar",
        "//prebuilts/tools/common/m2/repository/net/bytebuddy/byte-buddy-agent/1.6.5:jar",
        "//prebuilts/tools/common/m2/repository/net/bytebuddy/byte-buddy/1.6.5:jar",
        "//prebuilts/tools/common/m2/repository/org/hamcrest/hamcrest-core/1.3:jar",
        "//prebuilts/tools/common/m2/repository/org/hamcrest/hamcrest-integration/1.3:jar",
        "//prebuilts/tools/common/m2/repository/org/hamcrest/hamcrest-library/1.3:jar",
        "//prebuilts/tools/common/m2/repository/org/jdeferred/jdeferred-android-aar/1.2.2:aar",
        "//prebuilts/tools/common/m2/repository/org/jdeferred/jdeferred-android-aar/1.2.3:aar",
        "//prebuilts/tools/common/m2/repository/org/jdeferred/jdeferred-core/1.2.2:jar",
        "//prebuilts/tools/common/m2/repository/org/jdeferred/jdeferred-core/1.2.3:jar",
        "//prebuilts/tools/common/m2/repository/org/mockito/mockito-core/2.7.1:jar",
        "//prebuilts/tools/common/m2/repository/org/objenesis/objenesis/2.5:jar",
        "//prebuilts/tools/common/m2/repository/org/slf4j/slf4j-api/1.7.2:jar",
    ],
    visibility = ["//visibility:public"],
)

maven_repo(
    name = "android-gradle-1.5.0_repo",
    # keep sorted: for buildifier
    artifacts = [
        "//prebuilts/tools/common/m2/repository/com/android/databinding/baseLibrary/1.0-rc5:jar",
        "//prebuilts/tools/common/m2/repository/com/android/databinding/compilerCommon/1.0-rc5:jar",
        "//prebuilts/tools/common/m2/repository/com/android/tools/annotations/24.5.0:jar",
        "//prebuilts/tools/common/m2/repository/com/android/tools/build/builder-model/1.5.0:jar",
        "//prebuilts/tools/common/m2/repository/com/android/tools/build/builder-test-api/1.5.0:jar",
        "//prebuilts/tools/common/m2/repository/com/android/tools/build/builder/1.5.0:jar",
        "//prebuilts/tools/common/m2/repository/com/android/tools/build/gradle-core/1.5.0:jar",
        "//prebuilts/tools/common/m2/repository/com/android/tools/build/gradle/1.5.0:jar",
        "//prebuilts/tools/common/m2/repository/com/android/tools/build/manifest-merger/24.5.0:jar",
        "//prebuilts/tools/common/m2/repository/com/android/tools/build/transform-api/1.5.0:jar",
        "//prebuilts/tools/common/m2/repository/com/android/tools/common/24.5.0:jar",
        "//prebuilts/tools/common/m2/repository/com/android/tools/ddms/ddmlib/24.5.0:jar",
        "//prebuilts/tools/common/m2/repository/com/android/tools/dvlib/24.5.0:jar",
        "//prebuilts/tools/common/m2/repository/com/android/tools/external/com-intellij/uast/145.597.4:jar",
        "//prebuilts/tools/common/m2/repository/com/android/tools/jack/jack-api/0.9.0:jar",
        "//prebuilts/tools/common/m2/repository/com/android/tools/jill/jill-api/0.9.0:jar",
        "//prebuilts/tools/common/m2/repository/com/android/tools/layoutlib/layoutlib-api/24.5.0:jar",
        "//prebuilts/tools/common/m2/repository/com/android/tools/lint/lint-api/24.5.0:jar",
        "//prebuilts/tools/common/m2/repository/com/android/tools/lint/lint-checks/24.5.0:jar",
        "//prebuilts/tools/common/m2/repository/com/android/tools/lint/lint/24.5.0:jar",
        "//prebuilts/tools/common/m2/repository/com/android/tools/sdk-common/24.5.0:jar",
        "//prebuilts/tools/common/m2/repository/com/android/tools/sdklib/24.5.0:jar",
        "//prebuilts/tools/common/m2/repository/com/google/code/gson/gson/2.2.4:jar",
        "//prebuilts/tools/common/m2/repository/com/google/guava/guava/17.0:jar",
        "//prebuilts/tools/common/m2/repository/com/googlecode/juniversalchardet/juniversalchardet/1.0.3:jar",
        "//prebuilts/tools/common/m2/repository/com/squareup/javawriter/2.5.0:jar",
        "//prebuilts/tools/common/m2/repository/com/tunnelvisionlabs/antlr4-annotations/4.5:jar",
        "//prebuilts/tools/common/m2/repository/com/tunnelvisionlabs/antlr4-runtime/4.5:jar",
        "//prebuilts/tools/common/m2/repository/com/tunnelvisionlabs/antlr4/4.5:jar",
        "//prebuilts/tools/common/m2/repository/commons-io/commons-io/2.4:jar",
        "//prebuilts/tools/common/m2/repository/net/sf/kxml/kxml2/2.3.0:jar",
        "//prebuilts/tools/common/m2/repository/net/sf/proguard/proguard-base/5.2.1:jar",
        "//prebuilts/tools/common/m2/repository/net/sf/proguard/proguard-gradle/5.2.1:jar",
        "//prebuilts/tools/common/m2/repository/org/abego/treelayout/org.abego.treelayout.core/1.0.1:jar",
        "//prebuilts/tools/common/m2/repository/org/antlr/ST4/4.0.8:jar",
        "//prebuilts/tools/common/m2/repository/org/antlr/antlr-runtime/3.5.2:jar",
        "//prebuilts/tools/common/m2/repository/org/antlr/antlr/3.5.2:jar",
        "//prebuilts/tools/common/m2/repository/org/apache/commons/commons-compress/1.8.1:jar",
        "//prebuilts/tools/common/m2/repository/org/apache/commons/commons-lang3/3.3.2:jar",
        "//prebuilts/tools/common/m2/repository/org/apache/httpcomponents/httpclient/4.1.1:jar",
        "//prebuilts/tools/common/m2/repository/org/apache/httpcomponents/httpcore/4.1:jar",
        "//prebuilts/tools/common/m2/repository/org/apache/httpcomponents/httpmime/4.1:jar",
        "//prebuilts/tools/common/m2/repository/org/bouncycastle/bcpkix-jdk15on/1.48:jar",
        "//prebuilts/tools/common/m2/repository/org/bouncycastle/bcprov-jdk15on/1.48:jar",
        "//prebuilts/tools/common/m2/repository/org/eclipse/jdt/core/compiler/ecj/4.4.2:jar",
        "//prebuilts/tools/common/m2/repository/org/jacoco/org.jacoco.core/0.7.4.201502262128:jar",
        "//prebuilts/tools/common/m2/repository/org/jacoco/org.jacoco.report/0.7.4.201502262128:jar",
        "//prebuilts/tools/common/m2/repository/org/jetbrains/annotations/13.0:jar",
        "//prebuilts/tools/common/m2/repository/org/ow2/asm/asm-analysis/5.0.3:jar",
        "//prebuilts/tools/common/m2/repository/org/ow2/asm/asm-commons/5.0.3:jar",
        "//prebuilts/tools/common/m2/repository/org/ow2/asm/asm-tree/5.0.3:jar",
        "//prebuilts/tools/common/m2/repository/org/ow2/asm/asm/5.0.3:jar",
    ],
    visibility = ["//visibility:public"],
)

# ATTENTION: This file is generated from tools/buildSrc/base/dependencies.properties, see top of this file.
maven_java_library(
    name = "com.android.databinding_baseLibrary",
    export_artifact = "//prebuilts/tools/common/m2/repository/com/android/databinding/baseLibrary/3.0.0:jar",
    visibility = ["//visibility:public"],
    exports = [],
)

# ATTENTION: This file is generated from tools/buildSrc/base/dependencies.properties, see top of this file.
maven_java_library(
    name = "com.android.databinding_compilerCommon",
    export_artifact = "//prebuilts/tools/common/m2/repository/com/android/databinding/compilerCommon/3.0.0:jar",
    visibility = ["//visibility:public"],
    exports = [
        ":com.android.databinding_baseLibrary",
        ":com.android.tools_annotations",
        ":com.google.guava_guava",
        ":com.googlecode.juniversalchardet_juniversalchardet",
        ":commons-io_commons-io",
        ":org.antlr_antlr4",
    ],
)

# ATTENTION: This file is generated from tools/buildSrc/base/dependencies.properties, see top of this file.
maven_java_library(
    name = "com.android.tools.analytics-library_protos",
    export_artifact = "//prebuilts/tools/common/m2/repository/com/android/tools/analytics-library/protos/26.0.0:jar",
    visibility = ["//visibility:public"],
    exports = [":com.google.protobuf_protobuf-java"],
)

# ATTENTION: This file is generated from tools/buildSrc/base/dependencies.properties, see top of this file.
maven_java_library(
    name = "com.android.tools.analytics-library_shared",
    export_artifact = "//prebuilts/tools/common/m2/repository/com/android/tools/analytics-library/shared/26.0.0:jar",
    visibility = ["//visibility:public"],
    exports = [
        ":com.android.tools.analytics-library_protos",
        ":com.android.tools_annotations",
        ":com.android.tools_common",
        ":com.google.code.gson_gson",
        ":com.google.guava_guava",
    ],
)

# ATTENTION: This file is generated from tools/buildSrc/base/dependencies.properties, see top of this file.
maven_java_library(
    name = "com.android.tools.analytics-library_tracker",
    export_artifact = "//prebuilts/tools/common/m2/repository/com/android/tools/analytics-library/tracker/26.0.0:jar",
    visibility = ["//visibility:public"],
    exports = [
        ":com.android.tools.analytics-library_protos",
        ":com.android.tools.analytics-library_shared",
        ":com.android.tools_annotations",
        ":com.android.tools_common",
        ":com.google.guava_guava",
        ":com.google.protobuf_protobuf-java",
    ],
)

# ATTENTION: This file is generated from tools/buildSrc/base/dependencies.properties, see top of this file.
maven_java_library(
    name = "com.android.tools.build_apksig",
    export_artifact = "//prebuilts/tools/common/m2/repository/com/android/tools/build/apksig/3.0.0:jar",
    visibility = ["//visibility:public"],
    exports = [],
)

# ATTENTION: This file is generated from tools/buildSrc/base/dependencies.properties, see top of this file.
maven_java_library(
    name = "com.android.tools.build_builder",
    export_artifact = "//prebuilts/tools/common/m2/repository/com/android/tools/build/builder/3.0.0:jar",
    visibility = ["//visibility:public"],
    exports = [
        ":com.android.tools.analytics-library_protos",
        ":com.android.tools.analytics-library_shared",
        ":com.android.tools.analytics-library_tracker",
        ":com.android.tools.build_apksig",
        ":com.android.tools.build_builder-model",
        ":com.android.tools.build_builder-test-api",
        ":com.android.tools.build_manifest-merger",
        ":com.android.tools.ddms_ddmlib",
        ":com.android.tools_common",
        ":com.android.tools_sdk-common",
        ":com.android.tools_sdklib",
        ":com.googlecode.json-simple_json-simple",
        ":com.squareup_javawriter",
        ":it.unimi.dsi_fastutil",
        ":net.sf.jopt-simple_jopt-simple",
        ":org.bouncycastle_bcpkix-jdk15on",
        ":org.bouncycastle_bcprov-jdk15on",
        ":org.jetbrains.kotlin_kotlin-stdlib",
        ":org.ow2.asm_asm",
        ":org.ow2.asm_asm-commons",
        ":org.ow2.asm_asm-tree",
        ":org.ow2.asm_asm-util",
    ],
)

# ATTENTION: This file is generated from tools/buildSrc/base/dependencies.properties, see top of this file.
maven_java_library(
    name = "com.android.tools.build_builder-model",
    export_artifact = "//prebuilts/tools/common/m2/repository/com/android/tools/build/builder-model/3.0.0:jar",
    visibility = ["//visibility:public"],
    exports = [":com.android.tools_annotations"],
)

# ATTENTION: This file is generated from tools/buildSrc/base/dependencies.properties, see top of this file.
maven_java_library(
    name = "com.android.tools.build_builder-test-api",
    export_artifact = "//prebuilts/tools/common/m2/repository/com/android/tools/build/builder-test-api/3.0.0:jar",
    visibility = ["//visibility:public"],
    exports = [":com.android.tools.ddms_ddmlib"],
)

# ATTENTION: This file is generated from tools/buildSrc/base/dependencies.properties, see top of this file.
maven_java_library(
    name = "com.android.tools.build_gradle",
    export_artifact = "//prebuilts/tools/common/m2/repository/com/android/tools/build/gradle/3.0.0:jar",
    visibility = ["//visibility:public"],
    exports = [":com.android.tools.build_gradle-core"],
)

# ATTENTION: This file is generated from tools/buildSrc/base/dependencies.properties, see top of this file.
maven_java_library(
    name = "com.android.tools.build_gradle-api",
    export_artifact = "//prebuilts/tools/common/m2/repository/com/android/tools/build/gradle-api/3.0.0:jar",
    visibility = ["//visibility:public"],
    exports = [
        ":com.android.tools.build_builder-model",
        ":com.google.guava_guava",
    ],
)

# ATTENTION: This file is generated from tools/buildSrc/base/dependencies.properties, see top of this file.
maven_java_library(
    name = "com.android.tools.build_gradle-core",
    export_artifact = "//prebuilts/tools/common/m2/repository/com/android/tools/build/gradle-core/3.0.0:jar",
    visibility = ["//visibility:public"],
    exports = [
        ":com.android.databinding_compilerCommon",
        ":com.android.tools.build_builder",
        ":com.android.tools.build_gradle-api",
        ":com.android.tools.build_transform-api",
        ":com.android.tools.external.org-jetbrains_uast",
        ":com.android.tools.lint_lint",
        ":com.google.protobuf_protobuf-java",
        ":net.sf.jopt-simple_jopt-simple",
        ":net.sf.proguard_proguard-gradle",
        ":org.jacoco_org.jacoco.core",
        ":org.jacoco_org.jacoco.report",
        ":org.jetbrains.kotlin_kotlin-stdlib",
        ":org.ow2.asm_asm",
        ":org.ow2.asm_asm-analysis",
        ":org.ow2.asm_asm-commons",
        ":org.ow2.asm_asm-util",
    ],
)

# ATTENTION: This file is generated from tools/buildSrc/base/dependencies.properties, see top of this file.
maven_java_library(
    name = "com.android.tools.build_manifest-merger",
    export_artifact = "//prebuilts/tools/common/m2/repository/com/android/tools/build/manifest-merger/26.0.0:jar",
    visibility = ["//visibility:public"],
    exports = [
        ":com.android.tools_common",
        ":com.android.tools_sdk-common",
        ":com.android.tools_sdklib",
        ":com.google.code.gson_gson",
        ":net.sf.kxml_kxml2",
    ],
)

# ATTENTION: This file is generated from tools/buildSrc/base/dependencies.properties, see top of this file.
maven_java_library(
    name = "com.android.tools.build_transform-api",
    export_artifact = "//prebuilts/tools/common/m2/repository/com/android/tools/build/transform-api/2.0.0-deprecated-use-gradle-api:jar",
    visibility = ["//visibility:public"],
    exports = [],
)

# ATTENTION: This file is generated from tools/buildSrc/base/dependencies.properties, see top of this file.
maven_java_library(
    name = "com.android.tools.ddms_ddmlib",
    export_artifact = "//prebuilts/tools/common/m2/repository/com/android/tools/ddms/ddmlib/26.0.0:jar",
    visibility = ["//visibility:public"],
    exports = [
        ":com.android.tools_common",
        ":net.sf.kxml_kxml2",
    ],
)

# ATTENTION: This file is generated from tools/buildSrc/base/dependencies.properties, see top of this file.
maven_java_library(
    name = "com.android.tools.external.com-intellij_intellij-core",
    export_artifact = "//prebuilts/tools/common/m2/repository/com/android/tools/external/com-intellij/intellij-core/26.0.0:jar",
    visibility = ["//visibility:public"],
    exports = [":org.jetbrains.trove4j_trove4j"],
)

# ATTENTION: This file is generated from tools/buildSrc/base/dependencies.properties, see top of this file.
maven_java_library(
    name = "com.android.tools.external.lombok_lombok-ast",
    export_artifact = "//prebuilts/tools/common/m2/repository/com/android/tools/external/lombok/lombok-ast/0.2.3:jar",
    visibility = ["//visibility:public"],
    exports = [":com.google.guava_guava"],
)

# ATTENTION: This file is generated from tools/buildSrc/base/dependencies.properties, see top of this file.
maven_java_library(
    name = "com.android.tools.external.org-jetbrains_uast",
    export_artifact = "//prebuilts/tools/common/m2/repository/com/android/tools/external/org-jetbrains/uast/26.0.0:jar",
    visibility = ["//visibility:public"],
    exports = [],
)

# ATTENTION: This file is generated from tools/buildSrc/base/dependencies.properties, see top of this file.
maven_java_library(
    name = "com.android.tools.layoutlib_layoutlib-api",
    export_artifact = "//prebuilts/tools/common/m2/repository/com/android/tools/layoutlib/layoutlib-api/26.0.0:jar",
    visibility = ["//visibility:public"],
    exports = [
        ":com.android.tools_annotations",
        ":com.android.tools_common",
        ":com.intellij_annotations",
        ":net.sf.kxml_kxml2",
    ],
)

# ATTENTION: This file is generated from tools/buildSrc/base/dependencies.properties, see top of this file.
maven_java_library(
    name = "com.android.tools.lint_lint",
    export_artifact = "//prebuilts/tools/common/m2/repository/com/android/tools/lint/lint/26.0.0:jar",
    visibility = ["//visibility:public"],
    exports = [
        ":com.android.tools.build_manifest-merger",
        ":com.android.tools.lint_lint-checks",
        ":org.eclipse.jdt.core.compiler_ecj",
        ":org.jetbrains.kotlin_kotlin-stdlib",
    ],
)

# ATTENTION: This file is generated from tools/buildSrc/base/dependencies.properties, see top of this file.
maven_java_library(
    name = "com.android.tools.lint_lint-api",
    export_artifact = "//prebuilts/tools/common/m2/repository/com/android/tools/lint/lint-api/26.0.0:jar",
    visibility = ["//visibility:public"],
    exports = [
        ":com.android.tools.build_builder-model",
        ":com.android.tools.build_manifest-merger",
        ":com.android.tools.external.com-intellij_intellij-core",
        ":com.android.tools.external.lombok_lombok-ast",
        ":com.android.tools.external.org-jetbrains_uast",
        ":com.android.tools_sdk-common",
        ":org.jetbrains.kotlin_kotlin-reflect",
        ":org.jetbrains.kotlin_kotlin-stdlib",
        ":org.ow2.asm_asm",
        ":org.ow2.asm_asm-tree",
    ],
)

# ATTENTION: This file is generated from tools/buildSrc/base/dependencies.properties, see top of this file.
maven_java_library(
    name = "com.android.tools.lint_lint-checks",
    export_artifact = "//prebuilts/tools/common/m2/repository/com/android/tools/lint/lint-checks/26.0.0:jar",
    visibility = ["//visibility:public"],
    exports = [
        ":com.android.tools.lint_lint-api",
        ":org.ow2.asm_asm-analysis",
    ],
)

# ATTENTION: This file is generated from tools/buildSrc/base/dependencies.properties, see top of this file.
maven_java_library(
    name = "com.android.tools_annotations",
    export_artifact = "//prebuilts/tools/common/m2/repository/com/android/tools/annotations/26.0.0:jar",
    visibility = ["//visibility:public"],
    exports = [],
)

# ATTENTION: This file is generated from tools/buildSrc/base/dependencies.properties, see top of this file.
maven_java_library(
    name = "com.android.tools_common",
    export_artifact = "//prebuilts/tools/common/m2/repository/com/android/tools/common/26.0.0:jar",
    visibility = ["//visibility:public"],
    exports = [
        ":com.android.tools_annotations",
        ":com.google.guava_guava",
    ],
)

# ATTENTION: This file is generated from tools/buildSrc/base/dependencies.properties, see top of this file.
maven_java_library(
    name = "com.android.tools_dvlib",
    export_artifact = "//prebuilts/tools/common/m2/repository/com/android/tools/dvlib/26.0.0:jar",
    visibility = ["//visibility:public"],
    exports = [":com.android.tools_common"],
)

# ATTENTION: This file is generated from tools/buildSrc/base/dependencies.properties, see top of this file.
maven_java_library(
    name = "com.android.tools_repository",
    export_artifact = "//prebuilts/tools/common/m2/repository/com/android/tools/repository/26.0.0:jar",
    visibility = ["//visibility:public"],
    exports = [
        ":com.android.tools_common",
        ":com.google.jimfs_jimfs",
        ":org.apache.commons_commons-compress",
        ":org.jetbrains.kotlin_kotlin-stdlib",
    ],
)

# ATTENTION: This file is generated from tools/buildSrc/base/dependencies.properties, see top of this file.
maven_java_library(
    name = "com.android.tools_sdk-common",
    export_artifact = "//prebuilts/tools/common/m2/repository/com/android/tools/sdk-common/26.0.0:jar",
    visibility = ["//visibility:public"],
    exports = [
        ":com.android.tools.build_builder-model",
        ":com.android.tools.build_builder-test-api",
        ":com.android.tools.ddms_ddmlib",
        ":com.android.tools_sdklib",
        ":com.google.protobuf_protobuf-java",
        ":org.bouncycastle_bcpkix-jdk15on",
        ":org.bouncycastle_bcprov-jdk15on",
        ":org.jetbrains.kotlin_kotlin-stdlib",
    ],
)

# ATTENTION: This file is generated from tools/buildSrc/base/dependencies.properties, see top of this file.
maven_java_library(
    name = "com.android.tools_sdklib",
    export_artifact = "//prebuilts/tools/common/m2/repository/com/android/tools/sdklib/26.0.0:jar",
    visibility = ["//visibility:public"],
    exports = [
        ":com.android.tools.layoutlib_layoutlib-api",
        ":com.android.tools_dvlib",
        ":com.android.tools_repository",
        ":com.google.code.gson_gson",
        ":org.apache.commons_commons-compress",
        ":org.apache.httpcomponents_httpclient",
        ":org.apache.httpcomponents_httpmime",
    ],
)

# ATTENTION: This file is generated from tools/buildSrc/base/dependencies.properties, see top of this file.
maven_java_library(
    name = "com.google.code.findbugs_jsr305",
    export_artifact = "//prebuilts/tools/common/m2/repository/com/google/code/findbugs/jsr305/1.3.9:jar",
    visibility = ["//visibility:public"],
    exports = [],
)

# ATTENTION: This file is generated from tools/buildSrc/base/dependencies.properties, see top of this file.
maven_java_library(
    name = "com.google.code.gson_gson",
    export_artifact = "//prebuilts/tools/common/m2/repository/com/google/code/gson/gson/2.3:jar",
    visibility = ["//visibility:public"],
    exports = [],
)

# ATTENTION: This file is generated from tools/buildSrc/base/dependencies.properties, see top of this file.
maven_java_library(
    name = "com.google.errorprone_error_prone_annotations",
    export_artifact = "//prebuilts/tools/common/m2/repository/com/google/errorprone/error_prone_annotations/2.0.18:jar",
    visibility = ["//visibility:public"],
    exports = [],
)

# ATTENTION: This file is generated from tools/buildSrc/base/dependencies.properties, see top of this file.
maven_java_library(
    name = "com.google.guava_guava",
    export_artifact = "//prebuilts/tools/common/m2/repository/com/google/guava/guava/22.0:jar",
    visibility = ["//visibility:public"],
    exports = [
        ":com.google.code.findbugs_jsr305",
        ":com.google.errorprone_error_prone_annotations",
        ":com.google.j2objc_j2objc-annotations",
        ":org.codehaus.mojo_animal-sniffer-annotations",
    ],
)

# ATTENTION: This file is generated from tools/buildSrc/base/dependencies.properties, see top of this file.
maven_java_library(
    name = "com.google.j2objc_j2objc-annotations",
    export_artifact = "//prebuilts/tools/common/m2/repository/com/google/j2objc/j2objc-annotations/1.1:jar",
    visibility = ["//visibility:public"],
    exports = [],
)

# ATTENTION: This file is generated from tools/buildSrc/base/dependencies.properties, see top of this file.
maven_java_library(
    name = "com.google.jimfs_jimfs",
    export_artifact = "//prebuilts/tools/common/m2/repository/com/google/jimfs/jimfs/1.1:jar",
    visibility = ["//visibility:public"],
    exports = [":com.google.guava_guava"],
)

# ATTENTION: This file is generated from tools/buildSrc/base/dependencies.properties, see top of this file.
maven_java_library(
    name = "com.google.protobuf_protobuf-java",
    export_artifact = "//prebuilts/tools/common/m2/repository/com/google/protobuf/protobuf-java/3.0.0:jar",
    visibility = ["//visibility:public"],
    exports = [],
)

# ATTENTION: This file is generated from tools/buildSrc/base/dependencies.properties, see top of this file.
maven_java_library(
    name = "com.googlecode.json-simple_json-simple",
    export_artifact = "//prebuilts/tools/common/m2/repository/com/googlecode/json-simple/json-simple/1.1:jar",
    visibility = ["//visibility:public"],
    exports = [],
)

# ATTENTION: This file is generated from tools/buildSrc/base/dependencies.properties, see top of this file.
maven_java_library(
    name = "com.googlecode.juniversalchardet_juniversalchardet",
    export_artifact = "//prebuilts/tools/common/m2/repository/com/googlecode/juniversalchardet/juniversalchardet/1.0.3:jar",
    visibility = ["//visibility:public"],
    exports = [],
)

# ATTENTION: This file is generated from tools/buildSrc/base/dependencies.properties, see top of this file.
maven_java_library(
    name = "com.intellij_annotations",
    export_artifact = "//prebuilts/tools/common/m2/repository/com/intellij/annotations/12.0:jar",
    visibility = ["//visibility:public"],
    exports = [],
)

# ATTENTION: This file is generated from tools/buildSrc/base/dependencies.properties, see top of this file.
maven_java_library(
    name = "com.squareup_javawriter",
    export_artifact = "//prebuilts/tools/common/m2/repository/com/squareup/javawriter/2.5.0:jar",
    visibility = ["//visibility:public"],
    exports = [],
)

# ATTENTION: This file is generated from tools/buildSrc/base/dependencies.properties, see top of this file.
maven_java_library(
    name = "commons-codec_commons-codec",
    export_artifact = "//prebuilts/tools/common/m2/repository/commons-codec/commons-codec/1.6:jar",
    visibility = ["//visibility:public"],
    exports = [],
)

# ATTENTION: This file is generated from tools/buildSrc/base/dependencies.properties, see top of this file.
maven_java_library(
    name = "commons-io_commons-io",
    export_artifact = "//prebuilts/tools/common/m2/repository/commons-io/commons-io/2.4:jar",
    visibility = ["//visibility:public"],
    exports = [],
)

# ATTENTION: This file is generated from tools/buildSrc/base/dependencies.properties, see top of this file.
maven_java_library(
    name = "commons-logging_commons-logging",
    export_artifact = "//prebuilts/tools/common/m2/repository/commons-logging/commons-logging/1.1.1:jar",
    visibility = ["//visibility:public"],
    exports = [],
)

# ATTENTION: This file is generated from tools/buildSrc/base/dependencies.properties, see top of this file.
maven_java_library(
    name = "it.unimi.dsi_fastutil",
    export_artifact = "//prebuilts/tools/common/m2/repository/it/unimi/dsi/fastutil/7.2.0:jar",
    visibility = ["//visibility:public"],
    exports = [],
)

# ATTENTION: This file is generated from tools/buildSrc/base/dependencies.properties, see top of this file.
maven_java_library(
    name = "net.sf.jopt-simple_jopt-simple",
    export_artifact = "//prebuilts/tools/common/m2/repository/net/sf/jopt-simple/jopt-simple/4.9:jar",
    visibility = ["//visibility:public"],
    exports = [],
)

# ATTENTION: This file is generated from tools/buildSrc/base/dependencies.properties, see top of this file.
maven_java_library(
    name = "net.sf.kxml_kxml2",
    export_artifact = "//prebuilts/tools/common/m2/repository/net/sf/kxml/kxml2/2.3.0:jar",
    visibility = ["//visibility:public"],
    exports = [],
)

# ATTENTION: This file is generated from tools/buildSrc/base/dependencies.properties, see top of this file.
maven_java_library(
    name = "net.sf.proguard_proguard-base",
    export_artifact = "//prebuilts/tools/common/m2/repository/net/sf/proguard/proguard-base/5.3.3:jar",
    visibility = ["//visibility:public"],
    exports = [],
)

# ATTENTION: This file is generated from tools/buildSrc/base/dependencies.properties, see top of this file.
maven_java_library(
    name = "net.sf.proguard_proguard-gradle",
    export_artifact = "//prebuilts/tools/common/m2/repository/net/sf/proguard/proguard-gradle/5.3.3:jar",
    visibility = ["//visibility:public"],
    exports = [":net.sf.proguard_proguard-base"],
)

# ATTENTION: This file is generated from tools/buildSrc/base/dependencies.properties, see top of this file.
maven_java_library(
    name = "org.antlr_antlr4",
    export_artifact = "//prebuilts/tools/common/m2/repository/org/antlr/antlr4/4.5.3:jar",
    visibility = ["//visibility:public"],
    exports = [],
)

# ATTENTION: This file is generated from tools/buildSrc/base/dependencies.properties, see top of this file.
maven_java_library(
    name = "org.apache.commons_commons-compress",
    export_artifact = "//prebuilts/tools/common/m2/repository/org/apache/commons/commons-compress/1.12:jar",
    visibility = ["//visibility:public"],
    exports = [],
)

# ATTENTION: This file is generated from tools/buildSrc/base/dependencies.properties, see top of this file.
maven_java_library(
    name = "org.apache.httpcomponents_httpclient",
    export_artifact = "//prebuilts/tools/common/m2/repository/org/apache/httpcomponents/httpclient/4.2.6:jar",
    visibility = ["//visibility:public"],
    exports = [
        ":commons-codec_commons-codec",
        ":commons-logging_commons-logging",
        ":org.apache.httpcomponents_httpcore",
    ],
)

# ATTENTION: This file is generated from tools/buildSrc/base/dependencies.properties, see top of this file.
maven_java_library(
    name = "org.apache.httpcomponents_httpcore",
    export_artifact = "//prebuilts/tools/common/m2/repository/org/apache/httpcomponents/httpcore/4.2.5:jar",
    visibility = ["//visibility:public"],
    exports = [],
)

# ATTENTION: This file is generated from tools/buildSrc/base/dependencies.properties, see top of this file.
maven_java_library(
    name = "org.apache.httpcomponents_httpmime",
    export_artifact = "//prebuilts/tools/common/m2/repository/org/apache/httpcomponents/httpmime/4.1:jar",
    visibility = ["//visibility:public"],
    exports = [
        ":commons-logging_commons-logging",
        ":org.apache.httpcomponents_httpcore",
    ],
)

# ATTENTION: This file is generated from tools/buildSrc/base/dependencies.properties, see top of this file.
maven_java_library(
    name = "org.bouncycastle_bcpkix-jdk15on",
    export_artifact = "//prebuilts/tools/common/m2/repository/org/bouncycastle/bcpkix-jdk15on/1.56:jar",
    visibility = ["//visibility:public"],
    exports = [":org.bouncycastle_bcprov-jdk15on"],
)

# ATTENTION: This file is generated from tools/buildSrc/base/dependencies.properties, see top of this file.
maven_java_library(
    name = "org.bouncycastle_bcprov-jdk15on",
    export_artifact = "//prebuilts/tools/common/m2/repository/org/bouncycastle/bcprov-jdk15on/1.56:jar",
    visibility = ["//visibility:public"],
    exports = [],
)

# ATTENTION: This file is generated from tools/buildSrc/base/dependencies.properties, see top of this file.
maven_java_library(
    name = "org.codehaus.mojo_animal-sniffer-annotations",
    export_artifact = "//prebuilts/tools/common/m2/repository/org/codehaus/mojo/animal-sniffer-annotations/1.14:jar",
    visibility = ["//visibility:public"],
    exports = [],
)

# ATTENTION: This file is generated from tools/buildSrc/base/dependencies.properties, see top of this file.
maven_java_library(
    name = "org.eclipse.jdt.core.compiler_ecj",
    export_artifact = "//prebuilts/tools/common/m2/repository/org/eclipse/jdt/core/compiler/ecj/4.6.1:jar",
    visibility = ["//visibility:public"],
    exports = [],
)

# ATTENTION: This file is generated from tools/buildSrc/base/dependencies.properties, see top of this file.
maven_java_library(
    name = "org.jacoco_org.jacoco.core",
    export_artifact = "//prebuilts/tools/common/m2/repository/org/jacoco/org.jacoco.core/0.7.4.201502262128:jar",
    visibility = ["//visibility:public"],
    exports = [],
)

# ATTENTION: This file is generated from tools/buildSrc/base/dependencies.properties, see top of this file.
maven_java_library(
    name = "org.jacoco_org.jacoco.report",
    export_artifact = "//prebuilts/tools/common/m2/repository/org/jacoco/org.jacoco.report/0.7.4.201502262128:jar",
    visibility = ["//visibility:public"],
    exports = [":org.jacoco_org.jacoco.core"],
)

# ATTENTION: This file is generated from tools/buildSrc/base/dependencies.properties, see top of this file.
maven_java_library(
    name = "org.jetbrains.kotlin_kotlin-reflect",
    export_artifact = "//prebuilts/tools/common/m2/repository/org/jetbrains/kotlin/kotlin-reflect/1.1.3-2:jar",
    visibility = ["//visibility:public"],
    exports = [":org.jetbrains.kotlin_kotlin-stdlib"],
)

# ATTENTION: This file is generated from tools/buildSrc/base/dependencies.properties, see top of this file.
maven_java_library(
    name = "org.jetbrains.kotlin_kotlin-stdlib",
    export_artifact = "//prebuilts/tools/common/m2/repository/org/jetbrains/kotlin/kotlin-stdlib/1.1.3-2:jar",
    visibility = ["//visibility:public"],
    exports = [":org.jetbrains_annotations"],
)

# ATTENTION: This file is generated from tools/buildSrc/base/dependencies.properties, see top of this file.
maven_java_library(
    name = "org.jetbrains.trove4j_trove4j",
    export_artifact = "//prebuilts/tools/common/m2/repository/org/jetbrains/trove4j/trove4j/20160824:jar",
    visibility = ["//visibility:public"],
    exports = [],
)

# ATTENTION: This file is generated from tools/buildSrc/base/dependencies.properties, see top of this file.
maven_java_library(
    name = "org.jetbrains_annotations",
    export_artifact = "//prebuilts/tools/common/m2/repository/org/jetbrains/annotations/13.0:jar",
    visibility = ["//visibility:public"],
    exports = [],
)

# ATTENTION: This file is generated from tools/buildSrc/base/dependencies.properties, see top of this file.
maven_java_library(
    name = "org.ow2.asm_asm",
    export_artifact = "//prebuilts/tools/common/m2/repository/org/ow2/asm/asm/5.1:jar",
    visibility = ["//visibility:public"],
    exports = [],
)

# ATTENTION: This file is generated from tools/buildSrc/base/dependencies.properties, see top of this file.
maven_java_library(
    name = "org.ow2.asm_asm-analysis",
    export_artifact = "//prebuilts/tools/common/m2/repository/org/ow2/asm/asm-analysis/5.1:jar",
    visibility = ["//visibility:public"],
    exports = [":org.ow2.asm_asm-tree"],
)

# ATTENTION: This file is generated from tools/buildSrc/base/dependencies.properties, see top of this file.
maven_java_library(
    name = "org.ow2.asm_asm-commons",
    export_artifact = "//prebuilts/tools/common/m2/repository/org/ow2/asm/asm-commons/5.1:jar",
    visibility = ["//visibility:public"],
    exports = [":org.ow2.asm_asm-tree"],
)

# ATTENTION: This file is generated from tools/buildSrc/base/dependencies.properties, see top of this file.
maven_java_library(
    name = "org.ow2.asm_asm-tree",
    export_artifact = "//prebuilts/tools/common/m2/repository/org/ow2/asm/asm-tree/5.1:jar",
    visibility = ["//visibility:public"],
    exports = [":org.ow2.asm_asm"],
)

# ATTENTION: This file is generated from tools/buildSrc/base/dependencies.properties, see top of this file.
maven_java_library(
    name = "org.ow2.asm_asm-util",
    export_artifact = "//prebuilts/tools/common/m2/repository/org/ow2/asm/asm-util/5.1:jar",
    visibility = ["//visibility:public"],
    exports = [":org.ow2.asm_asm-tree"],
)
