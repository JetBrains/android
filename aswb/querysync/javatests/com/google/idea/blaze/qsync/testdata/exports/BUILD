java_library(
    name = "exports",
    srcs = ["TestClassUsingExport.java"],
    deps = [":exported-collect"],
)

java_library(
    name = "exported-collect",
    exports = ["@maven//:com.google.guava.guava"],
)
