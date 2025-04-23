load("//tools/base/bazel:bazel.bzl", "iml_module")

# managed by go/iml_to_build
iml_module(
    name = "app-inspection.inspectors.network.view",
    srcs = ["src"],
    iml_files = ["app-inspection.inspectors.network.view.iml"],
    resources = ["resources"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "@intellij//:intellij-sdk",
        "@intellij//:com.intellij.java",
        "//tools/adt/idea/app-inspection/inspector/ide:app-inspection.inspector.ide",
        "//tools/adt/idea/app-inspection/inspectors/network/model:app-inspection.inspectors.network.model",
        "//tools/adt/idea/.idea/libraries:studio-proto",
        "//tools/adt/idea/.idea/libraries:network_inspector_java_proto",
        "//tools/adt/idea/app-inspection/api:app-inspection.api",
        "//tools/adt/idea/app-inspection/inspector/api:app-inspection.inspector.api",
        "//tools/adt/idea/adt-ui:intellij.android.adt.ui",
        "//tools/adt/idea/adt-ui-model:intellij.android.adt.ui.model",
        "//tools/adt/idea/artwork:intellij.android.artwork",
        "//tools/adt/idea/inspectors-common/ui:intellij.android.inspectors-common.ui",
        "//tools/adt/idea/inspectors-common/api:intellij.android.inspectors-common.api",
        "//tools/adt/idea/codenavigation",
        "//tools/adt/idea/android-common:intellij.android.common",
        "//tools/base/flags:studio.android.sdktools.flags",
        "@intellij//:intellij.json.split",
    ],
)

# managed by go/iml_to_build
iml_module(
    name = "app-inspection.inspectors.network.view.tests",
    iml_files = ["app-inspection.inspectors.network.view.tests.iml"],
    test_class = "com.android.tools.idea.appinspection.inspectors.network.NetworkViewTestSuite",
    # do not sort: must match IML order
    test_deps = [
        "//tools/adt/idea/.idea/libraries:junit4",
        "//tools/adt/idea/.idea/libraries:studio-proto",
        "//tools/adt/idea/.idea/libraries:network_inspector_java_proto",
        "//tools/adt/idea/.idea/libraries:studio-analytics-proto",
        "//tools/adt/idea/.idea/libraries:truth",
        "//tools/adt/idea/app-inspection/inspectors/network/ide:app-inspection.inspectors.network.ide",
        "//tools/adt/idea/app-inspection/inspectors/network/view:app-inspection.inspectors.network.view",
        "//tools/adt/idea/app-inspection/inspectors/network/model:app-inspection.inspectors.network.model",
        "//tools/adt/idea/app-inspection/inspector/api:app-inspection.inspector.api",
        "//tools/adt/idea/inspectors-common/api:intellij.android.inspectors-common.api",
        "//tools/adt/idea/adt-ui:intellij.android.adt.ui",
        "//tools/adt/idea/adt-ui-model:intellij.android.adt.ui.model",
        "//tools/adt/idea/inspectors-common/ui:intellij.android.inspectors-common.ui",
        "//tools/adt/idea/android-test-framework:intellij.android.testFramework",
        "//tools/adt/idea/android-common:intellij.android.common",
        "//tools/adt/idea/adt-testutils:intellij.android.adt.testutils",
        "//tools/base/testutils:studio.android.sdktools.testutils",
        "//tools/adt/idea/codenavigation",
        "//tools/base/flags:studio.android.sdktools.flags",
        "//tools/analytics-library/testing:android.sdktools.analytics-testing",
        "//tools/analytics-library/tracker:analytics-tracker",
        "//tools/adt/idea/android:intellij.android.core",
        "//tools/adt/idea/.idea/libraries:kotlin-test",
        "@intellij//:intellij.json.split",
    ],
    test_friends = ["//tools/adt/idea/app-inspection/inspectors/network/view:app-inspection.inspectors.network.view"],
    test_srcs = ["testSrc"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "@intellij//:intellij-sdk",
        "@intellij//:com.intellij.java",
    ],
)
