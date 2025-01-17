load(
    "//tools/adt/idea/aswb:build-visibility.bzl",
    "DEFAULT_TEST_VISIBILITY",
    "G3PLUGINS_VISIBILITY",
    "INTELLIJ_PLUGINS_VISIBILITY",
    "PLUGIN_PACKAGES_VISIBILITY",
)
load(
    "//tools/adt/idea/aswb/build_defs:build_defs.bzl",
    "intellij_plugin",
    "intellij_plugin_library",
    "stamped_plugin_xml",
)
load(
    "//tools/adt/idea/aswb/testing:test_defs.bzl",
    "intellij_integration_test_suite",
    "intellij_unit_test_suite",
)
load("//tools/adt/idea/studio:studio.bzl", "intellij_plugin_import")
load("//tools/base/bazel:jvm_import.bzl", "jvm_import")

java_library(
    name = "base",
    srcs = glob(["src/**/*.java"]),
    javacopts = ["-Xep:FutureReturnValueIgnored:OFF"],
    resources = glob(["src/resources/**/*"]),
    visibility = PLUGIN_PACKAGES_VISIBILITY,
    deps = [
        "//tools/adt/idea/aswb/base/src/com/google/idea/blaze/base/command/buildresult/bepparser",
        "//tools/adt/idea/aswb/common/actions",
        "//tools/adt/idea/aswb/common/experiments",
        "//tools/adt/idea/aswb/common/settings",
        "//tools/adt/idea/aswb/common/ui/properties",
        "//tools/adt/idea/aswb/common/util:concurrency",
        "//tools/adt/idea/aswb/common/util:platform",
        "//tools/adt/idea/aswb/common/util:process",
        "//tools/adt/idea/aswb/common/util:transactions",
        "//tools/adt/idea/aswb/proto:proto_deps",
        "//tools/adt/idea/aswb/querysync",
        "//tools/adt/idea/aswb/shared",
        "//tools/adt/idea/aswb/shared:artifact",
        "//tools/adt/idea/aswb/shared:exception",
        "//tools/adt/idea/aswb/shared:proto",
        "//tools/adt/idea/aswb/shared:vcs",
        "//tools/adt/idea/aswb/third_party/java/auto_value",
        "//tools/vendor/google/aswb/plugin_api",
        "//tools/vendor/google/aswb/plugin_api:jsr305",  # unuseddeps: keep for @Nullable
        "@maven//:com.google.protobuf.protobuf-java",  # unuseddeps: keep for consistent protobuf-java version
    ],
)

intellij_plugin_library(
    name = "plugin_library",
    plugin_xmls = ["src/META-INF/blaze-base.xml"],
    visibility = PLUGIN_PACKAGES_VISIBILITY,
    deps = [
        ":base",
        "//tools/adt/idea/aswb/aswb/src/com/android/tools/idea/rendering/tokens",
        "//tools/adt/idea/aswb/base/src/com/google/idea/blaze/base/deployment/liveedit/tokens",
        "//tools/adt/idea/aswb/base/src/com/google/idea/blaze/base/project/indexing",
    ],
)

java_library(
    name = "proto_wrapper",
    srcs = [
        "src/com/google/idea/blaze/base/ideinfo/ProjectDataInterner.java",
        "src/com/google/idea/blaze/base/ideinfo/ProtoWrapper.java",
    ],
    neverlink = 1,
    visibility = ["//visibility:private"],
    deps = [
        ":base",
        "//tools/adt/idea/aswb/common/experiments",
        "//tools/adt/idea/aswb/proto:proto_deps",
        "//tools/vendor/google/aswb/plugin_api",
        "//tools/vendor/google/aswb/plugin_api:jsr305",
    ],
)

# self-contained custom formatter API, exposed so other plugins can optionally depend on it
java_library(
    name = "formatter_api",
    srcs = glob(["src/com/google/idea/blaze/base/formatter/*.java"]),
    neverlink = 1,
    visibility = G3PLUGINS_VISIBILITY,
    deps = [
        "//tools/vendor/google/aswb/plugin_api",
        "//tools/vendor/google/aswb/plugin_api:jsr305",
    ],
)

# formatter_api for tests.
java_library(
    name = "formatter_api_for_test",
    testonly = True,
    srcs = glob(["src/com/google/idea/blaze/base/formatter/*.java"]),
    visibility = G3PLUGINS_VISIBILITY,
    deps = [
        "//tools/vendor/google/aswb/plugin_api",
        "//tools/vendor/google/aswb/plugin_api:jsr305",  # unuseddeps: keep (b/146560321)
    ],
)

# self-contained label parsing API, exposed so other plugins can optionally depend on it
java_library(
    name = "label_api",
    srcs = [
        "src/com/google/idea/blaze/base/ideinfo/ArtifactLocation.java",
        "src/com/google/idea/blaze/base/model/primitives/InvalidTargetException.java",
        "src/com/google/idea/blaze/base/model/primitives/Kind.java",
        "src/com/google/idea/blaze/base/model/primitives/Label.java",
        "src/com/google/idea/blaze/base/model/primitives/LanguageClass.java",
        "src/com/google/idea/blaze/base/model/primitives/PackagePathValidator.java",
        "src/com/google/idea/blaze/base/model/primitives/RuleType.java",
        "src/com/google/idea/blaze/base/model/primitives/TargetExpression.java",
        "src/com/google/idea/blaze/base/model/primitives/TargetName.java",
        "src/com/google/idea/blaze/base/model/primitives/WildcardTargetPattern.java",
        "src/com/google/idea/blaze/base/model/primitives/WorkspacePath.java",
    ],
    neverlink = 1,
    visibility = G3PLUGINS_VISIBILITY,
    deps = [
        ":proto_wrapper",
        "//tools/adt/idea/aswb/proto:proto_deps",
        "//tools/adt/idea/aswb/shared",
        "//tools/adt/idea/aswb/third_party/java/auto_value",
        "//tools/vendor/google/aswb/plugin_api",
        "//tools/vendor/google/aswb/plugin_api:jsr305",
    ],
)

# self-contained sync listener API, exposed so other plugins can optionally depend on it
java_library(
    name = "sync_listener_api",
    srcs = [
        "src/com/google/idea/blaze/base/sync/SimpleSyncListener.java",
        "src/com/google/idea/blaze/base/sync/SyncMode.java",
        "src/com/google/idea/blaze/base/sync/SyncResult.java",
    ],
    neverlink = 1,
    visibility = INTELLIJ_PLUGINS_VISIBILITY,
    deps = [
        "//tools/vendor/google/aswb/plugin_api",
    ],
)

# self-contained VCS state listener API, exposed so other plugins can optionally depend on it
java_library(
    name = "vcs_sync_listener_api",
    srcs = [
        "src/com/google/idea/blaze/base/vcs/VcsSyncListener.java",
    ],
    neverlink = 1,
    visibility = INTELLIJ_PLUGINS_VISIBILITY,
    deps = [
        "//tools/vendor/google/aswb/plugin_api",
    ],
)

# neverlink library exposed so other plugins can optionally depend on it
java_library(
    name = "vfs_api",
    srcs = [
        "src/com/google/idea/blaze/base/io/VfsUtils.java",
        "src/com/google/idea/blaze/base/io/VirtualFileSystemProvider.java",
    ],
    neverlink = 1,
    visibility = G3PLUGINS_VISIBILITY,
    deps = [
        "//tools/vendor/google/aswb/plugin_api",
        "//tools/vendor/google/aswb/plugin_api:jsr305",
    ],
)

# neverlink library exposed so other plugins can optionally depend on it
java_library(
    name = "absolute_path_patcher_api",
    srcs = [
        "src/com/google/idea/blaze/base/io/AbsolutePathPatcher.java",
    ],
    neverlink = 1,
    visibility = G3PLUGINS_VISIBILITY,
    deps = [
        ":vfs_api",
        "//tools/vendor/google/aswb/plugin_api",
        "//tools/vendor/google/aswb/plugin_api:jsr305",
    ],
)

# neverlink library exposed so other plugins can optionally depend on it
java_library(
    name = "workspace_path_resolver_api",
    srcs = [
        "src/com/google/idea/blaze/base/model/primitives/WorkspacePath.java",
        "src/com/google/idea/blaze/base/model/primitives/WorkspaceRoot.java",
        "src/com/google/idea/blaze/base/settings/BlazeImportSettings.java",
        "src/com/google/idea/blaze/base/settings/BlazeImportSettingsManager.java",
        "src/com/google/idea/blaze/base/settings/BuildSystemName.java",
        "src/com/google/idea/blaze/base/sync/workspace/WorkspacePathResolver.java",
        "src/com/google/idea/blaze/base/sync/workspace/WorkspacePathResolverProvider.java",
    ],
    neverlink = 1,
    visibility = G3PLUGINS_VISIBILITY,
    deps = [
        ":proto_wrapper",
        "//tools/adt/idea/aswb/proto:proto_deps",
        "//tools/vendor/google/aswb/plugin_api",
        "//tools/vendor/google/aswb/plugin_api:jsr305",
    ],
)

# neverlink library exposed so other plugins can optionally depend on it
java_library(
    name = "workspace_file_finder_api",
    srcs = [
        "src/com/google/idea/blaze/base/sync/projectview/WorkspaceFileFinder.java",
    ],
    neverlink = 1,
    visibility = G3PLUGINS_VISIBILITY,
    deps = [
        "//tools/vendor/google/aswb/plugin_api",
        "//tools/vendor/google/aswb/plugin_api:jsr305",
    ],
)

# neverlink library exposed so other plugins can optionally depend on it
java_library(
    name = "workspace_language_checker_api",
    srcs = [
        "src/com/google/idea/blaze/base/model/primitives/LanguageClass.java",
        "src/com/google/idea/blaze/base/sync/projectview/WorkspaceLanguageChecker.java",
    ],
    neverlink = 1,
    visibility = G3PLUGINS_VISIBILITY,
    deps = [
        ":proto_wrapper",
        "//tools/vendor/google/aswb/plugin_api",
        "//tools/vendor/google/aswb/plugin_api:jsr305",
    ],
)

# neverlink library exposed so other plugins can optionally depend on it
java_library(
    name = "logging_api",
    srcs = [
        "src/com/google/idea/blaze/base/logging/EventLogger.java",
        "src/com/google/idea/blaze/base/logging/LoggedDirectoryProvider.java",
        "src/com/google/idea/blaze/base/logging/LoggedSettingsProvider.java",
    ],
    neverlink = 1,
    visibility = G3PLUGINS_VISIBILITY,
    deps = [
        "//tools/adt/idea/aswb/common/experiments",
        "//tools/adt/idea/aswb/third_party/java/auto_value",
        "//tools/vendor/google/aswb/plugin_api",
        "//tools/vendor/google/aswb/plugin_api:jsr305",
    ],
)

# neverlink library exposed so other plugins can optionally depend on it
java_library(
    name = "library_to_target_api",
    srcs = [
        "src/com/google/idea/blaze/base/model/LegacyLibraryToTargetResolver.java",
        "src/com/google/idea/blaze/base/model/LibraryKey.java",
    ],
    neverlink = 1,
    visibility = G3PLUGINS_VISIBILITY,
    deps = [
        ":label_api",
        ":proto_wrapper",
        "//tools/vendor/google/aswb/plugin_api",
        "//tools/vendor/google/aswb/plugin_api:jsr305",  # unuseddeps: keep for @Immutable
    ],
)

# neverlink library exposed so other plugins can optionally depend on it
java_library(
    name = "blaze_user_settings_api",
    srcs = [
        "src/com/google/idea/blaze/base/settings/BlazeUserSettings.java",
    ],
    neverlink = 1,
    deps = [
        ":logging_api",
        "//tools/vendor/google/aswb/plugin_api",
        "//tools/vendor/google/aswb/plugin_api:jsr305",  # unuseddeps: keep for @Nullable
    ],
)

# neverlink library exposed so other plugins can provide dependency information
java_library(
    name = "dep_finder_api",
    srcs = [
        "src/com/google/idea/blaze/base/dependencies/DependencyFinder.java",
        "src/com/google/idea/blaze/base/dependencies/SourceToTargetProvider.java",
        "src/com/google/idea/blaze/base/dependencies/TargetInfo.java",
        "src/com/google/idea/blaze/base/dependencies/TestSize.java",
        "src/com/google/idea/blaze/base/run/SourceToTargetFinder.java",
        "src/com/google/idea/blaze/base/run/targetfinder/FuturesUtil.java",
        "src/com/google/idea/blaze/base/run/targetfinder/TargetFinder.java",
    ],
    neverlink = 1,
    visibility = G3PLUGINS_VISIBILITY,
    deps = [
        ":base",
        "//tools/adt/idea/aswb/proto:proto_deps",
        "//tools/adt/idea/aswb/shared",
        "//tools/vendor/google/aswb/plugin_api",
        "//tools/vendor/google/aswb/plugin_api:jsr305",
    ],
)

# neverlink library exposed so other plugins can provide dependency information
java_library(
    name = "build_batching_service_api",
    srcs = [
        "src/com/google/idea/blaze/base/bazel/BuildSystem.java",
        "src/com/google/idea/blaze/base/logging/utils/ShardStats.java",
        "src/com/google/idea/blaze/base/sync/sharding/BuildBatchingService.java",
    ],
    neverlink = 1,
    visibility = G3PLUGINS_VISIBILITY,
    exports = [
        ":label_api",
    ],
    deps = [
        ":base",
        "//tools/adt/idea/aswb/third_party/java/auto_value",
        "//tools/vendor/google/aswb/plugin_api",
        "//tools/vendor/google/aswb/plugin_api:jsr305",
        "@maven//:com.google.errorprone.error_prone_annotations",
    ],
)

# neverlink library exposed so other plugins can optionally depend on it
java_library(
    name = "blaze_sync_modification_tracker_api",
    srcs = ["src/com/google/idea/blaze/base/sync/BlazeSyncModificationTracker.java"],
    neverlink = 1,
    visibility = G3PLUGINS_VISIBILITY,
    deps = [
        ":base",
        "//tools/vendor/google/aswb/plugin_api",
    ],
)

# neverlink library exposed so other plugins can optionally depend on it
java_library(
    name = "buildifier_binary_provider_api",
    srcs = [
        "src/com/google/idea/blaze/base/buildmodifier/BuildifierBinaryProvider.java",
    ],
    neverlink = 1,
    deps = [
        "//tools/vendor/google/aswb/plugin_api",
        "//tools/vendor/google/aswb/plugin_api:jsr305",  # unuseddeps: keep for @Nullable
    ],
)

# exposed so g3plugins tests can optionally depend on it
java_library(
    name = "remote_file_prefetcher_for_tests",
    testonly = 1,
    srcs = [
        "src/com/google/idea/blaze/base/async/FutureUtil.java",
        "src/com/google/idea/blaze/base/command/buildresult/RemoteOutputArtifact.java",
        "src/com/google/idea/blaze/base/ideinfo/ProtoWrapper.java",
        "src/com/google/idea/blaze/base/prefetch/DefaultPrefetcher.java",
        "src/com/google/idea/blaze/base/prefetch/FetchExecutor.java",
        "src/com/google/idea/blaze/base/prefetch/RemoteArtifactPrefetcher.java",
    ],
    visibility = G3PLUGINS_VISIBILITY,
    deps = [
        ":base",
        "//tools/adt/idea/aswb/base/src/com/google/idea/blaze/base/command/buildresult/bepparser",
        "//tools/adt/idea/aswb/proto:proto_deps",
        "//tools/adt/idea/aswb/shared:artifact",
        "//tools/vendor/google/aswb/plugin_api",
        "//tools/vendor/google/aswb/plugin_api:jsr305",
    ],
)

java_library(
    name = "file_size_scanner",
    srcs = [
        "src/com/google/idea/blaze/base/io/FileSizeScanner.java",
    ],
    neverlink = 1,
    visibility = G3PLUGINS_VISIBILITY,
    deps = [
        ":base",
        "//tools/vendor/google/aswb/plugin_api",
    ],
)

java_library(
    name = "remote_file_prefetcher",
    srcs = [
        "src/com/google/idea/blaze/base/async/FutureUtil.java",
        "src/com/google/idea/blaze/base/command/buildresult/RemoteOutputArtifact.java",
        "src/com/google/idea/blaze/base/prefetch/DefaultPrefetcher.java",
        "src/com/google/idea/blaze/base/prefetch/FetchExecutor.java",
        "src/com/google/idea/blaze/base/prefetch/RemoteArtifactPrefetcher.java",
    ],
    neverlink = 1,
    visibility = G3PLUGINS_VISIBILITY,
    deps = [
        ":base",
        "//tools/adt/idea/aswb/proto:proto_deps",
        "//tools/adt/idea/aswb/shared:artifact",
        "//tools/vendor/google/aswb/plugin_api",
        "//tools/vendor/google/aswb/plugin_api:jsr305",
    ],
)

java_library(
    name = "artifact_fetcher",
    srcs = [
        "src/com/google/idea/blaze/base/command/buildresult/LocalFileOutputArtifact.java",
        "src/com/google/idea/blaze/base/command/buildresult/LocalFileOutputArtifactWithoutDigest.java",
        "src/com/google/idea/blaze/base/qsync/cache/ArtifactFetchers.java",
        "src/com/google/idea/blaze/base/qsync/cache/FileApiArtifactFetcher.java",
        "src/com/google/idea/blaze/base/settings/BuildBinaryType.java",
    ],
    neverlink = 1,
    visibility = G3PLUGINS_VISIBILITY,
    deps = [
        ":base",
        "//tools/adt/idea/aswb/common/experiments",
        "//tools/adt/idea/aswb/shared",
        "//tools/adt/idea/aswb/shared:artifact",
        "//tools/vendor/google/aswb/plugin_api",
        "//tools/vendor/google/aswb/plugin_api:jsr305",
        "@maven//:com.google.errorprone.error_prone_annotations",
    ],
)

java_library(
    name = "blaze_executor",
    srcs = [
        "src/com/google/idea/blaze/base/async/executor/BlazeExecutor.java",
    ],
    visibility = G3PLUGINS_VISIBILITY,
    deps = [
        "//tools/vendor/google/aswb/plugin_api",
        "@maven//:com.google.guava.guava",
    ],
)

java_library(
    name = "unit_test_utils",
    testonly = 1,
    srcs = glob(["tests/utils/unit/**/*.java"]),
    visibility = PLUGIN_PACKAGES_VISIBILITY,
    deps = [
        ":base",
        "//tools/adt/idea/aswb/base/src/com/google/idea/blaze/base/command/buildresult/bepparser",
        "//tools/adt/idea/aswb/proto:proto_deps",
        "//tools/adt/idea/aswb/shared:artifact",
        "//tools/adt/idea/aswb/shared:exception",
        "//tools/adt/idea/aswb/shared/java/com/google/idea/blaze/common",
        "//tools/adt/idea/aswb/testing:lib",
        "//tools/adt/idea/aswb/third_party/java/auto_value",
        "//tools/adt/idea/aswb/third_party/java/junit",
        "//tools/vendor/google/aswb/plugin_api:jsr305",
        "//tools/vendor/google/aswb/plugin_api:plugin_api_for_tests",
        "//tools/vendor/google/aswb/plugin_api:test_libs",
        "@maven//:com.google.guava.guava",
    ],
)

java_library(
    name = "integration_test_utils",
    testonly = 1,
    srcs = glob(["tests/utils/integration/**/*.java"]),
    data = [
        "//tools/adt/idea/aswb/base/tests/utils/testdata/java/com/test",
        "//tools/adt/idea/aswb/base/tests/utils/testdata/java/com/test:test_srcs",
        "//tools/adt/idea/aswb/base/tests/utils/testdata/java/com/test2",
        "//tools/adt/idea/aswb/base/tests/utils/testdata/java/com/test2:test2_srcs",
    ],
    visibility = PLUGIN_PACKAGES_VISIBILITY,
    deps = [
        ":base",
        ":unit_test_utils",
        "//tools/adt/idea/aswb/proto:proto_deps",
        "//tools/adt/idea/aswb/shared:artifact",
        "//tools/adt/idea/aswb/shared:vcs",
        "//tools/adt/idea/aswb/testing:lib",
        "//tools/adt/idea/aswb/third_party/java/junit",
        "//tools/vendor/google/aswb/plugin_api:jsr305",
        "//tools/vendor/google/aswb/plugin_api:plugin_api_for_tests",
        "//tools/vendor/google/aswb/plugin_api:test_libs",
        "@bazel_tools//tools/java/runfiles",
        "@maven//:com.google.guava.guava",
    ],
)

intellij_unit_test_suite(
    name = "unit_tests",
    srcs = glob(["tests/unittests/**/*.java"]),
    tags = ["noci:studio-win"],
    test_package_root = "com.google.idea.blaze.base",
    visibility = DEFAULT_TEST_VISIBILITY,
    deps = [
        ":base",
        ":unit_test_utils",
        "//tools/adt/idea/aswb/base/src/com/google/idea/blaze/base/command/buildresult/bepparser",
        "//tools/adt/idea/aswb/common/experiments",
        "//tools/adt/idea/aswb/common/experiments:unit_test_utils",
        "//tools/adt/idea/aswb/common/util:process",
        "//tools/adt/idea/aswb/proto:proto_deps",
        "//tools/adt/idea/aswb/querysync",
        "//tools/adt/idea/aswb/querysync/javatests/com/google/idea/blaze/qsync:TestUtils",
        "//tools/adt/idea/aswb/querysync/javatests/com/google/idea/blaze/qsync/testdata",
        "//tools/adt/idea/aswb/shared",
        "//tools/adt/idea/aswb/shared:artifact",
        "//tools/adt/idea/aswb/shared/java/com/google/idea/blaze/common",
        "//tools/adt/idea/aswb/shared/javatests/com/google/idea/blaze/common:test_utils",
        "//tools/adt/idea/aswb/shared/javatests/com/google/idea/blaze/common/artifact:test_utils",
        "//tools/adt/idea/aswb/testing:lib",
        "//tools/adt/idea/aswb/third_party/java/auto_value",
        "//tools/adt/idea/aswb/third_party/java/junit",
        "//tools/vendor/google/aswb/plugin_api:jsr305",
        "//tools/vendor/google/aswb/plugin_api:plugin_api_for_tests",
        "//tools/vendor/google/aswb/plugin_api:test_libs",
        "@maven//:com.google.guava.guava",
    ],
)

stamped_plugin_xml(
    name = "base_plugin_xml",
    testonly = 1,
    plugin_id = "com.google.idea.blaze.base",
    plugin_name = "com.google.idea.blaze.base",
)

intellij_plugin(
    name = "base_integration_test_plugin",
    testonly = 1,
    plugin_xml = ":base_plugin_xml",
    deps = [":plugin_library"],
)

intellij_plugin_import(
    name = "com.google.idea.blaze.base",
    testonly = 1,
    overwrite_plugin_version = True,
    target_dir = "base",
    exports = [":base_plugin_import"],
)

jvm_import(
    name = "base_plugin_import",
    testonly = 1,
    jars = [
        ":base_integration_test_plugin",
    ],
)

intellij_integration_test_suite(
    name = "integration_tests",
    srcs = glob(["tests/integrationtests/**/*.java"]),
    plugins = [":com.google.idea.blaze.base"],
    required_plugins = "com.google.idea.blaze.base",
    tags = ["noci:studio-win"],
    test_package_root = "com.google.idea.blaze.base",
    visibility = DEFAULT_TEST_VISIBILITY,
    deps = [
        ":base",
        ":integration_test_utils",
        ":unit_test_utils",
        "//tools/adt/idea/aswb/aswb:integration_test_utils",
        "//tools/adt/idea/aswb/proto:proto_deps",
        "//tools/adt/idea/aswb/querysync",
        "//tools/adt/idea/aswb/querysync/javatests/com/google/idea/blaze/qsync/artifacts:mock_artifact_cache",
        "//tools/adt/idea/aswb/shared:exception",
        "//tools/adt/idea/aswb/shared/java/com/google/idea/blaze/common",
        "//tools/adt/idea/aswb/shared/java/com/google/idea/blaze/common/artifact",
        "//tools/adt/idea/aswb/shared/javatests/com/google/idea/blaze/common:test_utils",
        "//tools/adt/idea/aswb/third_party/java/junit",
        "//tools/vendor/google/aswb/plugin_api",
        "//tools/vendor/google/aswb/plugin_api:jsr305",
        "//tools/vendor/google/aswb/plugin_api:plugin_api_for_tests",  # unuseddeps: keep
        "//tools/vendor/google/aswb/plugin_api:test_libs",
        "@maven//:com.google.guava.guava",
    ],
)
