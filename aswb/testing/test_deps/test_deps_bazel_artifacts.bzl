"""
The list of artifacts that bazel may need to download while running in tests.
"""

# Note: when Bazel runs in tests it attempts to fetch the artifacts from a cache by their content hash.
#       Theis mapping is used at the build time to prepare the bazel artifact cache by downloading the artifacts from the Internet.
# Note: If a test that runs bazel in bazel fails with an error thaat it cannot access the Internet to download
#       a specific artifact it should be enough to add the url to this map together with its expected sha256. It can usually
#       be obtained from the website that hosts the artifact or from the Central Bazel repository at https://registry.bazel.build/.
_ASWB_TEST_DEP_ARTIFACTS = {
    "https://github.com/bazelbuild/rules_pkg/releases/download/0.10.1/rules_pkg-0.10.1.tar.gz": "d250924a2ecc5176808fc4c25d5cf5e9e79e6346d79d5ab1c493e289e722d1d0",
    "https://github.com/bazelbuild/rules_proto/releases/download/6.0.0/rules_proto-6.0.0.tar.gz": "303e86e722a520f6f326a50b41cfc16b98fe6d1955ce46642a5b7a67c11c0f5d",
    "https://github.com/bazelbuild/rules_cc/releases/download/0.0.10-rc1/rules_cc-0.0.10-rc1.tar.gz": "d75a040c32954da0d308d3f2ea2ba735490f49b3a7aa3e4b40259ca4b814f825",  # {"name": "rules_cc.tar.gz", "sha256": "d75a040c32954da0d308d3f2ea2ba735490f49b3a7aa3e4b40259ca4b814f825"},
    "https://github.com/bazelbuild/bazel-skylib/releases/download/1.5.0/bazel-skylib-1.5.0.tar.gz": "cd55a062e763b9349921f0f5db8c3933288dc8ba4f76dd9416aac68acee3cb94",
    "https://github.com/bazelbuild/bazel-skylib/releases/download/1.6.1/bazel-skylib-1.6.1.tar.gz": "9f38886a40548c6e96c106b752f242130ee11aaa068a56ba7e56f4511f33e4f2",
    "https://github.com/bazelbuild/platforms/releases/download/0.0.10/platforms-0.0.10.tar.gz": "218efe8ee736d26a3572663b374a253c012b716d8af0c07e842e82f238a0a7ee",
    "https://github.com/bazelbuild/rules_python/releases/download/0.24.0/rules_python-0.24.0.tar.gz": "0a8003b044294d7840ac7d9d73eef05d6ceb682d7516781a4ec62eeb34702578",
    "https://github.com/bazelbuild/apple_support/releases/download/1.15.1/apple_support.1.15.1.tar.gz": "c4bb2b7367c484382300aee75be598b92f847896fb31bbd22f3a2346adf66a80",
    "https://github.com/bazelbuild/rules_kotlin/releases/download/v1.9.5/rules_kotlin-v1.9.5.tar.gz": "34e8c0351764b71d78f76c8746e98063979ce08dcf1a91666f3f3bc2949a533d",
    "https://github.com/bazelbuild/rules_java/releases/download/7.6.1/rules_java-7.6.1.tar.gz": "f8ae9ed3887df02f40de9f4f7ac3873e6dd7a471f9cddf63952538b94b59aeb3",
    "https://github.com/bazel-contrib/bazel_features/releases/download/v1.11.0/bazel_features-v1.11.0.tar.gz": "2cd9e57d4c38675d321731d65c15258f3a66438ad531ae09cb8bb14217dc8572",
    "https://github.com/JetBrains/kotlin/releases/download/v1.9.22/kotlin-compiler-1.9.22.zip": "88b39213506532c816ff56348c07bbeefe0c8d18943bffbad11063cf97cac3e6",
    "https://mirror.bazel.build/bazel_java_tools/releases/java/v13.6.0/java_tools_linux-v13.6.0.zip": "0d3fcae7ae40d0a25f17c3adc30a3674f526953c55871189e2efe3463fce3496",
    "https://mirror.bazel.build/bazel_java_tools/releases/java/v13.6.0/java_tools-v13.6.0.zip": "74c978eab040ad4ec38ce0d0970ac813cc2c6f4f6f4f121c0414719487edc991",
    "https://github.com/bazelbuild/rules_jvm_external/releases/download/6.1/rules_jvm_external-6.1.tar.gz": "08ea921df02ffe9924123b0686dc04fd0ff875710bfadb7ad42badb931b0fd50",
    "https://github.com/indygreg/python-build-standalone/releases/download/20230116/cpython-3.11.1+20230116-x86_64-unknown-linux-gnu-install_only.tar.gz": "02a551fefab3750effd0e156c25446547c238688a32fabde2995c941c03a6423",
    "https://mirror.bazel.build/cdn.azul.com/zulu/bin/zulu17.44.53-ca-jdk17.0.8.1-linux_x64.tar.gz": "b9482f2304a1a68a614dfacddcf29569a72f0fac32e6c74f83dc1b9a157b8340",
    "https://mirror.bazel.build/cdn.azul.com/zulu/bin/zulu21.32.17-ca-jdk21.0.2-linux_x64.tar.gz": "5ad730fbee6bb49bfff10bf39e84392e728d89103d3474a7e5def0fd134b300a",
    "https://mirror.bazel.build/coursier_cli/coursier_cli_v2_1_8.jar": "2b78bfdd3ef13fd1f42f158de0f029d7cbb1f4f652d51773445cf2b6f7918a87",
}

ASWB_TEST_DEPS = {
    k: {"sha256": v, "name": v}
    for k, v in _ASWB_TEST_DEP_ARTIFACTS.items()
}

ASWB_TEST_DEP_LABELS = ["@aswb_test_deps_" + v["name"] + "//file" for k, v in ASWB_TEST_DEPS.items()]
