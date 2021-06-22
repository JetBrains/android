filegroup(
    name = "testData",
    srcs = glob(
        include = ["**"],
    ),
    visibility = ["//visibility:public"],
)

filegroup(
    name = "projects",
    srcs = glob(
        include = [
            "projects/**",
            # Used by TranslationsEditorGradleTest
            "stringsEditor/MyApplication/**",
        ],
    ),
    visibility = ["//visibility:public"],
)
