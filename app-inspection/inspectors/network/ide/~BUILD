load("//tools/base/bazel:bazel.bzl", "iml_module")

# managed by go/iml_to_build
iml_module(
    name = "app-inspection.inspectors.network.ide",
    srcs = ["src"],
    iml_files = ["app-inspection.inspectors.network.ide.iml"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "@intellij//:intellij-sdk",
        "@intellij//:com.intellij.java",
        "//tools/adt/idea/app-inspection/inspector/ide:app-inspection.inspector.ide[module]",
        "//tools/adt/idea/app-inspection/inspector/api:app-inspection.inspector.api[module]",
        "//tools/adt/idea/artwork:intellij.android.artwork[module]",
        "//tools/adt/idea/app-inspection/inspectors/network/view:app-inspection.inspectors.network.view[module]",
        "//tools/adt/idea/app-inspection/inspectors/network/model:app-inspection.inspectors.network.model[module]",
        "//tools/base/flags:studio.android.sdktools.flags[module]",
        "//tools/adt/idea/android-common:intellij.android.common[module]",
        "//tools/adt/idea/adt-ui:intellij.android.adt.ui[module]",
        "//tools/adt/idea/adt-ui-model:intellij.android.adt.ui.model[module]",
        "//tools/adt/idea/native-symbolizer[module]",
        "//tools/adt/idea/inspectors-common/api-ide:intellij.android.inspectors-common.api-ide[module]",
        "//tools/adt/idea/inspectors-common/ui:intellij.android.inspectors-common.ui[module]",
        "//tools/adt/idea/inspectors-common/api:intellij.android.inspectors-common.api[module]",
        "//tools/analytics-library/tracker:analytics-tracker[module]",
        "//tools/adt/idea/.idea/libraries:studio-analytics-proto",
        "//tools/adt/idea/codenavigation[module]",
    ],
)

# managed by go/iml_to_build
iml_module(
    name = "app-inspection.inspectors.network.ide.tests",
    iml_files = ["app-inspection.inspectors.network.ide.tests.iml"],
    test_class = "com.android.tools.idea.appinspection.inspectors.network.NetworkIdeTestSuite",
    test_data = glob(["testData/**"]),
    test_srcs = ["testSrc"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "@intellij//:intellij-sdk",
        "@intellij//:com.intellij.java",
        "//tools/base/testutils:studio.android.sdktools.testutils[module, test]",
        "//tools/adt/idea/adt-testutils:intellij.android.adt.testutils[module, test]",
        "//tools/adt/idea/android-test-framework:intellij.android.testFramework[module, test]",
        "//tools/adt/idea/.idea/libraries:truth[test]",
        "//tools/adt/idea/inspectors-common/api:intellij.android.inspectors-common.api[module, test]",
        "//tools/adt/idea/inspectors-common/ui:intellij.android.inspectors-common.ui[module, test]",
        "//tools/adt/idea/app-inspection/inspectors/network/ide:app-inspection.inspectors.network.ide[module, test]",
        "//tools/adt/idea/adt-ui:intellij.android.adt.ui[module, test]",
        "//tools/adt/idea/app-inspection/inspectors/network/view:app-inspection.inspectors.network.view[module, test]",
        "//tools/adt/idea/app-inspection/inspectors/network/model:app-inspection.inspectors.network.model[module, test]",
        "//tools/adt/idea/inspectors-common/api-ide:intellij.android.inspectors-common.api-ide[module, test]",
        "//tools/adt/idea/codenavigation[module]",
    ],
)
