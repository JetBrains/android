"""
The list of artifacts that bazel may need to download while running in tests.
"""

# Note: when Bazel runs in tests it attempts to fetch the artifacts from a cache by their content hash.
#       Theis mapping is used at the build time to prepare the bazel artifact cache by downloading the artifacts from the Internet.
# Note: If a test that runs bazel in bazel fails with an error thaat it cannot access the Internet to download
#       a specific artifact it should be enough to add the url to this map together with its expected sha256. It can usually
#       be obtained from the website that hosts the artifact or from the Central Bazel repository at https://registry.bazel.build/.

#keep sorted
_ASWB_TEST_DEP_ARTIFACTS = {
    "https://github.com/JetBrains/kotlin/releases/download/v1.9.22/kotlin-compiler-1.9.22.zip": "88b39213506532c816ff56348c07bbeefe0c8d18943bffbad11063cf97cac3e6",
    "https://github.com/bazel-contrib/bazel_features/releases/download/v1.21.0/bazel_features-v1.21.0.tar.gz": "af3d4fb1cf4f25942cb4a933b1ad93a0ea9fe9ee70c2af7f369fb72a67c266e5",
    "https://github.com/bazelbuild/apple_support/releases/download/1.15.1/apple_support.1.15.1.tar.gz": "c4bb2b7367c484382300aee75be598b92f847896fb31bbd22f3a2346adf66a80",
    "https://github.com/bazelbuild/bazel-skylib/releases/download/1.7.1/bazel-skylib-1.7.1.tar.gz": "bc283cdfcd526a52c3201279cda4bc298652efa898b10b4db0837dc51652756f",
    "https://github.com/bazelbuild/platforms/releases/download/0.0.10/platforms-0.0.10.tar.gz": "218efe8ee736d26a3572663b374a253c012b716d8af0c07e842e82f238a0a7ee",
    "https://github.com/bazelbuild/rules_cc/releases/download/0.1.1/rules_cc-0.1.1.tar.gz": "712d77868b3152dd618c4d64faaddefcc5965f90f5de6e6dd1d5ddcd0be82d42",  # {"name": "rules_cc.tar.gz", "sha256": "d75a040c32954da0d308d3f2ea2ba735490f49b3a7aa3e4b40259ca4b814f825"},
    "https://github.com/bazelbuild/rules_java/releases/download/8.6.1/rules_java-8.6.1.tar.gz": "c5bc17e17bb62290b1fd8fdd847a2396d3459f337a7e07da7769b869b488ec26",
    "https://github.com/bazelbuild/rules_jvm_external/releases/download/6.3/rules_jvm_external-6.3.tar.gz": "c18a69d784bcd851be95897ca0eca0b57dc86bb02e62402f15736df44160eb02",
    "https://github.com/bazelbuild/rules_kotlin/releases/download/v1.9.6/rules_kotlin-v1.9.6.tar.gz": "3b772976fec7bdcda1d84b9d39b176589424c047eb2175bed09aac630e50af43",
    "https://github.com/bazelbuild/rules_license/releases/download/1.0.0/rules_license-1.0.0.tar.gz": "26d4021f6898e23b82ef953078389dd49ac2b5618ac564ade4ef87cced147b38",
    "https://github.com/bazelbuild/rules_pkg/releases/download/1.0.1/rules_pkg-1.0.1.tar.gz": "d20c951960ed77cb7b341c2a59488534e494d5ad1d30c4818c736d57772a9fef",
    "https://github.com/bazelbuild/rules_proto/releases/download/6.0.0/rules_proto-6.0.0.tar.gz": "303e86e722a520f6f326a50b41cfc16b98fe6d1955ce46642a5b7a67c11c0f5d",
    "https://github.com/bazelbuild/rules_python/releases/download/0.40.0/rules_python-0.40.0.tar.gz": "690e0141724abb568267e003c7b6d9a54925df40c275a870a4d934161dc9dd53",
    "https://github.com/bazelbuild/rules_shell/releases/download/v0.2.0/rules_shell-v0.2.0.tar.gz": "410e8ff32e018b9efd2743507e7595c26e2628567c42224411ff533b57d27c28",
    "https://github.com/bazelbuild/stardoc/releases/download/0.7.1/stardoc-0.7.1.tar.gz": "fabb280f6c92a3b55eed89a918ca91e39fb733373c81e87a18ae9e33e75023ec",
    "https://github.com/indygreg/python-build-standalone/releases/download/20241016/cpython-3.11.10+20241016-x86_64-unknown-linux-gnu-install_only.tar.gz": "8b50a442b04724a24c1eebb65a36a0c0e833d35374dbdf9c9470d8a97b164cd9",
    "https://github.com/protocolbuffers/protobuf/releases/download/v29.0/protobuf-29.0.zip": "2e442d21839ec9dbafda4cc9083239aa04e78fc9c27dfa59b5374e968050cd22",
    "https://mirror.bazel.build/bazel_java_tools/releases/java/v13.13/java_tools-v13.13.zip": "df895d5067f2dad4524109ebfddac442d2514d0e2f95f6abc098cfae98b9bbb5",
    "https://mirror.bazel.build/bazel_java_tools/releases/java/v13.13/java_tools_linux-v13.13.zip": "60c10e91f5900801423f9c5b020cc0c7da16dbaeee9c22891b38e7017306a8e7",
    "https://mirror.bazel.build/cdn.azul.com/zulu/bin/zulu17.50.19-ca-jdk17.0.11-linux_x64.tar.gz": "a1e8ac9ae5804b84dc07cf9d8ebe1b18247d70c92c1e0de97ea10109563f4379",
    "https://mirror.bazel.build/cdn.azul.com/zulu/bin/zulu21.36.17-ca-jdk21.0.4-linux_x64.tar.gz": "318d0c2ed3c876fb7ea2c952945cdcf7decfb5264ca51aece159e635ac53d544",
    "https://mirror.bazel.build/coursier_cli/coursier_cli_v2_1_8.jar": "2b78bfdd3ef13fd1f42f158de0f029d7cbb1f4f652d51773445cf2b6f7918a87",
}

ASWB_TEST_DEPS = {
    k: {"sha256": v, "name": v}
    for k, v in _ASWB_TEST_DEP_ARTIFACTS.items()
}

ASWB_TEST_DEP_LABELS = ["@aswb_test_deps_" + v["name"] + "//file" for k, v in ASWB_TEST_DEPS.items()]
