*** note
**Warning:** work in progress
***

This directory contains the main files to build Android Studio unbundled

Currently all the targets needed are tagged as manual, and live in paralell with the main iml_module targets. To generate the iml_modules for this project use:

```
bazel run //tools/base/bazel:iml_to_build -- --project_path tools/adt/idea --strict
```

This will create and/or update the iml_module rules. To update the attributes that are manuallt added to iml_module rules use:

```
bazel run //tools/base/bazel:fix_unbundled_rules
```

When the rules are generated, to build them you can use the
following build/query command:

```
bazel build `bazel query "attr(tags, unb, //...)"` --config=remote
```

