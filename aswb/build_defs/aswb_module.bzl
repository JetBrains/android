"""Build rules for aggregating ASwB modules and packaging them.

This file defines `aswb_module` and `aswb_plugin_module` rules used to aggregate
naked libraries and package them into plugins, tracking target labels and plugin XMLs.

### Terminology:

When the aspect traverses the dependency graph of an `aswb_module`, it intercepts and collects naked
libraries up to recognized module boundaries (targets providing `ImlModuleInfo`, `PluginInfo`, or `IntellijInfo`).

- **Transitive Labels**: The full set of target labels discovered by the aspect in this module's subgraph.
  This includes *everything* up to the module boundaries, including targets that might logically
  belong to other `aswb_module` subgraphs.
- **Aggregated Labels**: The subset of transitive labels that are *actually* packaged (aggregated)
  into the current module's `.jar` file. These are filtered by checking if their package matches any
  of the module's `scope_prefixes`, and excluding targets that are explicitly claimed by downstream modules
  (`aswb_module_deps`) or libraries (`lib_deps`).

### Build Process:

1. **Discovery (Aspect):** The `deps` of the module are traversed. Any target that does not export an
   `ImlModuleInfo`, `PluginInfo`, or `IntellijInfo` is collected into the transitive closure.
2. **Aggregation:** The module takes the transitive jars and filters them down to the Aggregated Labels.
   It merges these specific jars into a single `<module>.jar` hermetically using `singlejar`.
   It simultaneously filters and aggregates plugin XMLs and their optional provider configurations based
   on the same `scope_prefixes`.
3. **Enforcement:** The module asserts that *every* target discovered in the Transitive Labels has been
   accounted for. It must either be aggregated in this module, explicitly claimed by a downstream module
   via `aswb_module_deps`, or explicitly acknowledged as a library dependency via `lib_deps`.
   If any naked target leaks out without being explicitly grouped, the build fails.
4. **Exposing as Module Boundary:** The rule exposes an `ImlModuleInfo` containing the newly aggregated `.jar`
   as its `module_jars` and `main_provider`. It declares its downstream dependencies (`aswb_module_deps` + `lib_deps`)
   as its `deps`. By providing this `ImlModuleInfo`, the rule effectively becomes a new module boundary itself,
   masking its internal granular library graph from the broader Bazel build and the IDE's project structure,
   presenting it as a single cohesive unit.

### Packaging into Plugins:

An IDE plugin JAR requires exactly one primary `META-INF/plugin.xml`. Because an IDE plugin is often
composed of multiple `aswb_module` boundaries depending on each other, they cannot individually package
their own XML fragments. Instead, `aswb_module` tracks its collected plugin XMLs and optional XML
configurations via `AswbModuleInfo`.

The `aswb_plugin_module` rule serves as an intermediate aggregator for this XML data. It consumes
multiple `aswb_module` targets, merges all their collected `plugin.xml` fragments into a single file,
processes the optional XMLs, and produces a single `.jar` containing the merged `META-INF` structure.
This `.jar` is exposed via `ImlModuleInfo`, allowing it to be consumed by the final plugin packaging
rules (like `studio_plugin`) alongside the compiled code jars, satisfying the single `plugin.xml` requirement.
"""

load("//tools/adt/idea/aswb/build_defs:intellij_plugin.bzl", "IntellijPluginLibraryInfo")
load("//tools/adt/idea/studio:studio.bzl", "IntellijInfo", "PluginInfo")
load("//tools/base/bazel:bazel.bzl", "ImlModuleInfo")
load("//tools/base/bazel:merge_archives.bzl", "run_singlejar")

AswbModuleInfo = provider(
    doc = """Tracks targets and XML configurations aggregated inside an aswb_module.

This provider is exposed by an aswb_module to allow downstream modules (either another
aswb_module or aswb_plugin_module) to consume its collected Transitive Labels and XMLs.""",
    fields = {
        "included_target_labels": "depset of target labels compiled/aggregated by this aswb_module",
        "transitive_plugin_xml_structs": "depset of structs carrying label and plugin XML file",
        "transitive_optional_plugin_xml_info_structs": "depset of structs carrying label and _OptionalPluginXmlInfo provider",
    },
)

_AswbModuleAspectInfo = provider(
    doc = """Collects transitive dependencies of an aswb_module until it reaches another module boundary.

Used to track naked libraries unintercepted by other packaging systems (like targets providing ImlModuleInfo,
PluginInfo, or IntellijInfo). This ensures our transitive dependency graph contains no unexpected or
undeclared targets outside the module's scope.""",
    fields = {
        "target_label": "The label of current target",
        "direct_jar_depset": "depset of direct output JARs",
        "transitive_labels": "depset of ALL transitive labels discovered",
        "transitive_target_jar_structs": "depset of structs carrying label and direct jars list",
        "transitive_plugin_xml_structs": "depset of structs carrying label and plugin XML file",
        "transitive_optional_plugin_xml_info_structs": "depset of structs carrying label and _OptionalPluginXmlInfo provider",
    },
)

def _get_direct_jar_depset(target):
    # buildifier: disable=native-java-info
    if JavaInfo not in target:
        return depset()

    jars = []

    # buildifier: disable=native-java-info
    for jar in target[JavaInfo].java_outputs:
        if jar.class_jar:
            jars.append(jar.class_jar)
        if jar.generated_class_jar:
            jars.append(jar.generated_class_jar)
    return depset(jars)

def _aswb_module_aspect_impl(target, ctx):
    """Aspect implementation that traverses deps to collect naked libraries.

    Traversal stops at recognized module boundaries (targets providing ImlModuleInfo,
    PluginInfo, or IntellijInfo). It builds the full set of Transitive Labels, jars,
    and plugin XMLs to be evaluated and filtered by the main rule."""

    # Approval by omission for pure IML modules
    if ImlModuleInfo in target or PluginInfo in target or IntellijInfo in target:
        return [_AswbModuleAspectInfo(
            target_label = target.label,
            direct_jar_depset = depset(),
            transitive_labels = depset(),
            transitive_target_jar_structs = depset(),
            transitive_plugin_xml_structs = depset(),
            transitive_optional_plugin_xml_info_structs = depset(),
        )]

    # Aggregate transitives
    deps = getattr(ctx.rule.attr, "deps", [])
    exports = getattr(ctx.rule.attr, "exports", [])
    runtime_deps = getattr(ctx.rule.attr, "runtime_deps", [])
    all_deps = deps + exports + runtime_deps
    deps_aspect_info = [dep[_AswbModuleAspectInfo] for dep in all_deps if _AswbModuleAspectInfo in dep]
    transitive_labels = depset(
        [target.label],
        transitive = [info.transitive_labels for info in deps_aspect_info],
    )

    dep_plugin_xml_struct_depsets = [info.transitive_plugin_xml_structs for info in deps_aspect_info]
    dep_optional_plugin_xml_info_struct_depsets = [info.transitive_optional_plugin_xml_info_structs for info in deps_aspect_info]

    direct_jar_depset = _get_direct_jar_depset(target)
    this_item = struct(label = target.label, target = target, jars = tuple(direct_jar_depset.to_list()))
    transitive_target_jar_structs = depset(
        [this_item],
        transitive = [info.transitive_target_jar_structs for info in deps_aspect_info],
    )

    plugin_xml_structs_list = []
    optional_plugin_xml_info_structs_list = []
    if IntellijPluginLibraryInfo in target:
        info = target[IntellijPluginLibraryInfo]
        for xml in info.plugin_xmls.to_list():
            plugin_xml_structs_list.append(struct(label = target.label, file = xml))
        for provider in info.optional_plugin_xmls:
            optional_plugin_xml_info_structs_list.append(struct(label = target.label, provider = provider))

    transitive_plugin_xml_structs = depset(
        plugin_xml_structs_list,
        transitive = dep_plugin_xml_struct_depsets,
        order = "preorder",
    )
    transitive_optional_plugin_xml_info_structs = depset(
        optional_plugin_xml_info_structs_list,
        transitive = dep_optional_plugin_xml_info_struct_depsets,
    )

    return [_AswbModuleAspectInfo(
        target_label = target.label,
        direct_jar_depset = direct_jar_depset,
        transitive_labels = transitive_labels,
        transitive_target_jar_structs = transitive_target_jar_structs,
        transitive_plugin_xml_structs = transitive_plugin_xml_structs,
        transitive_optional_plugin_xml_info_structs = transitive_optional_plugin_xml_info_structs,
    )]

_aswb_module_aspect = aspect(
    attr_aspects = ["deps", "exports", "runtime_deps"],
    implementation = _aswb_module_aspect_impl,
)

def _is_in_scope(pkg, pkg_prefixes):
    for pkg_prefix in pkg_prefixes:
        if pkg == pkg_prefix or pkg.startswith(pkg_prefix + "/"):
            return True
    return False

def _verify_transitive_boundaries(
        all_transitive_labels,
        aggregated_labels,
        downstream_module_labels,
        library_labels):
    approved_labels_map = {label: None for label in aggregated_labels}
    for label in downstream_module_labels:
        approved_labels_map[label] = None
    for label in library_labels:
        approved_labels_map[label] = None

    all_approved_labels_map = {}
    for label in aggregated_labels:
        all_approved_labels_map[label] = None
    for label in approved_labels_map:
        all_approved_labels_map[label] = None

    missing = [label for label in all_transitive_labels if label not in all_approved_labels_map]
    if missing:
        fail("Transitive dependencies outside the module scope must be provided by another module (providing ImlModuleInfo, IntellijPluginInfo, or IntellijInfo) or explicitly listed in lib_deps (for libraries) or aswb_module_deps (for other modules): " + str(missing))

    return all_approved_labels_map

def _gather_transitive_data(deps):
    return struct(
        labels = depset(transitive = [dep[_AswbModuleAspectInfo].transitive_labels for dep in deps if _AswbModuleAspectInfo in dep]),
        target_jar_structs = depset(transitive = [dep[_AswbModuleAspectInfo].transitive_target_jar_structs for dep in deps if _AswbModuleAspectInfo in dep]),
        plugin_xml_structs = depset(transitive = [dep[_AswbModuleAspectInfo].transitive_plugin_xml_structs for dep in deps if _AswbModuleAspectInfo in dep], order = "preorder"),
        optional_plugin_xml_info_structs = depset(transitive = [dep[_AswbModuleAspectInfo].transitive_optional_plugin_xml_info_structs for dep in deps if _AswbModuleAspectInfo in dep]),
    )

def _filter_aggregated_data(scope_prefixes, transitive_data, downstream_module_labels, library_labels):
    pkg_prefixes = []
    for prefix in scope_prefixes:
        p = prefix[2:] if prefix.startswith("//") else prefix
        p = p[:-1] if p.endswith("/") else p
        pkg_prefixes.append(p)

    approved_downstream_target_labels_map = {label: None for label in downstream_module_labels + library_labels}

    consolidated_jars = []
    aggregated_target_labels = []

    for item in transitive_data.target_jar_structs.to_list():
        if item.label in approved_downstream_target_labels_map:
            continue  # Skip items claimed by downstream module scopes

        if _is_in_scope(item.label.package, pkg_prefixes):
            consolidated_jars.extend(item.jars)
            aggregated_target_labels.append(item.label)

    filtered_plugin_xml_structs = []
    for item in transitive_data.plugin_xml_structs.to_list():
        if _is_in_scope(item.label.package, pkg_prefixes):
            filtered_plugin_xml_structs.append(item.file)

    filtered_optional_plugin_xml_info_structs = []
    for item in transitive_data.optional_plugin_xml_info_structs.to_list():
        if _is_in_scope(item.label.package, pkg_prefixes):
            filtered_optional_plugin_xml_info_structs.append(item.provider)

    return struct(
        jars = {jar: None for jar in consolidated_jars}.keys(),
        target_labels = aggregated_target_labels,
        plugin_xml_structs = depset(filtered_plugin_xml_structs, order = "preorder"),
        optional_plugin_xml_info_structs = depset(filtered_optional_plugin_xml_info_structs),
    )

def _aggregate_jars(ctx, jars):
    aggregated_jar = ctx.actions.declare_file(ctx.label.name + ".jar")
    run_singlejar(ctx, jars = jars, out = aggregated_jar)
    return aggregated_jar

def _aswb_module_impl(ctx):
    # 1. Gather all transitive data from deps
    transitive_data = _gather_transitive_data(ctx.attr.deps)

    downstream_module_labels = []
    for dep in ctx.attr.aswb_module_deps:
        downstream_module_labels.extend(dep[AswbModuleInfo].included_target_labels.to_list())

    library_labels = depset(transitive = [dep[_AswbModuleAspectInfo].transitive_labels for dep in ctx.attr.lib_deps if _AswbModuleAspectInfo in dep]).to_list()

    # 2. Filter data down to what this module specifically aggregates
    aggregated_data = _filter_aggregated_data(
        scope_prefixes = ctx.attr.scope_prefixes,
        transitive_data = transitive_data,
        downstream_module_labels = downstream_module_labels,
        library_labels = library_labels,
    )

    # 3. Transitive verification
    all_approved_target_labels_map = _verify_transitive_boundaries(
        all_transitive_labels = transitive_data.labels.to_list(),
        aggregated_labels = aggregated_data.target_labels,
        downstream_module_labels = downstream_module_labels,
        library_labels = library_labels,
    )

    # 4. Execute Actions (Jar Aggregation)
    aggregated_jar = _aggregate_jars(ctx, aggregated_data.jars)

    # 5. Return Providers
    included_targets_provider = AswbModuleInfo(
        included_target_labels = depset(all_approved_target_labels_map.keys()),
        transitive_plugin_xml_structs = aggregated_data.plugin_xml_structs,
        transitive_optional_plugin_xml_info_structs = aggregated_data.optional_plugin_xml_info_structs,
    )

    iml_module_info = ImlModuleInfo(
        module_jars = aggregated_jar,
        java_deps = [],
        test_provider = None,
        # buildifier: disable=native-java-info
        main_provider = JavaInfo(aggregated_jar, aggregated_jar),
        deps = ctx.attr.aswb_module_deps + ctx.attr.lib_deps,
        jvm_target = "21",  # Default or acceptable placeholder
        module_visibility = None,
        names = [ctx.label.name],
    )

    return [
        included_targets_provider,
        iml_module_info,
        DefaultInfo(files = depset([aggregated_jar])),
    ]

aswb_module = rule(
    doc = """Aggregates naked libraries into an ImlModuleInfo boundary.

Traverses `deps` via an aspect to gather transitively reachable targets. Jars and plugin XMLs
from targets matching the `scope_prefix` are merged into a single jar. Any target discovered
in the transitive closure MUST be either aggregated here, explicitly claimed by a downstream
module in `aswb_module_deps`, or listed in `lib_deps`. Output is an `ImlModuleInfo`
representing this subgraph as a unified IDE module.""",
    attrs = {
        "scope_prefixes": attr.string_list(
            mandatory = True,
            doc = "The bazel package prefixes used to filter which transitive targets belong to this module's scope.",
        ),
        "deps": attr.label_list(
            mandatory = True,
            aspects = [_aswb_module_aspect],
            doc = "The naked libraries (e.g. java_library) that form the root of the dependency graph to traverse.",
        ),
        "aswb_module_deps": attr.label_list(
            providers = [AswbModuleInfo],
            doc = "Downstream aswb_modules. Targets aggregated by these dependencies are excluded from this module's aggregation.",
        ),
        "lib_deps": attr.label_list(
            aspects = [_aswb_module_aspect],
            doc = "Libraries explicitly acknowledged as external dependencies. Their targets will not be aggregated.",
        ),
        "_singlejar": attr.label(
            default = Label("@bazel_tools//tools/jdk:singlejar"),
            cfg = "exec",
            executable = True,
        ),
    },
    implementation = _aswb_module_impl,
)

def _merge_main_plugin_xmls(ctx, plugin_xmls):
    xml_list = plugin_xmls.to_list()
    if ctx.file.plugin_xml:
        xml_list = [ctx.file.plugin_xml] + xml_list

    if not xml_list:
        return None

    merged_name = ctx.label.name + "_merged_plugin.xml"
    merged_file = ctx.actions.declare_file(merged_name)
    ctx.actions.run(
        executable = ctx.executable._merge_xml_binary,
        arguments = ["--output", merged_file.path] + [xml.path for xml in xml_list],
        inputs = depset(xml_list),
        outputs = [merged_file],
        mnemonic = "MergeModuleXmls",
    )
    return merged_file

def _merge_optional_plugin_xmls(ctx, optional_providers):
    module_to_xmls = {}
    for provider in optional_providers.to_list():
        for xml in provider.optional_plugin_xmls:
            module_to_xmls.setdefault(xml.module, []).append(xml.plugin_xml)

    module_to_merged_xmls = {}
    for module, xmls in module_to_xmls.items():
        merged_name = "merged_xml_for_" + module + "_" + ctx.label.name + ".xml"
        merged_file = ctx.actions.declare_file(merged_name)
        ctx.actions.run(
            executable = ctx.executable._merge_xml_binary,
            arguments = ["--output", merged_file.path] + [xml.path for xml in xmls],
            inputs = list(xmls),
            outputs = [merged_file],
            mnemonic = "MergeModuleOptionalXmls",
        )
        module_to_merged_xmls[module] = merged_file
    return module_to_merged_xmls

def _stitch_module_dependencies(ctx, final_plugin_xml, module_to_merged_xmls):
    if not final_plugin_xml or not module_to_merged_xmls:
        return final_plugin_xml

    stitched_file = ctx.actions.declare_file("final_plugin_xml_" + ctx.label.name + ".xml")
    args = ["--plugin_xml", final_plugin_xml.path, "--output", stitched_file.path]
    for module in module_to_merged_xmls.keys():
        args.append(module)
        args.append("optional-" + ctx.label.name + "-" + module + ".xml")
    ctx.actions.run(
        executable = ctx.executable._append_optional_xml_elements,
        arguments = args,
        inputs = [final_plugin_xml],
        outputs = [stitched_file],
        mnemonic = "StitchModuleDependencies",
    )
    return stitched_file

def _package_module_jar(ctx, final_plugin_xml, module_to_merged_xmls):
    temp_aggregated_jar = ctx.actions.declare_file(ctx.label.name + "_temp.jar")

    # Create an empty JAR hermetically using singlejar
    run_singlejar(
        ctx = ctx,
        jars = [],
        out = temp_aggregated_jar,
    )

    aggregated_jar = ctx.actions.declare_file(ctx.label.name + ".jar")
    args = ["--deploy_jar", temp_aggregated_jar.path, "--output", aggregated_jar.path]
    if final_plugin_xml:
        args.extend([final_plugin_xml.path, "plugin.xml"])
    for module, merged_xml in module_to_merged_xmls.items():
        args.append(merged_xml.path)
        args.append("optional-" + ctx.label.name + "-" + module + ".xml")

    ctx.actions.run(
        executable = ctx.executable._package_meta_inf_files,
        arguments = args,
        inputs = [temp_aggregated_jar] + ([final_plugin_xml] if final_plugin_xml else []) + module_to_merged_xmls.values(),
        outputs = [aggregated_jar],
        mnemonic = "PackageModuleJar",
    )
    return aggregated_jar

def _aswb_plugin_module_impl(ctx):
    deps = ctx.attr.deps
    plugin_xmls = depset(transitive = [dep[AswbModuleInfo].transitive_plugin_xml_structs for dep in deps if AswbModuleInfo in dep], order = "preorder")
    optional_providers = depset(transitive = [dep[AswbModuleInfo].transitive_optional_plugin_xml_info_structs for dep in deps if AswbModuleInfo in dep])

    # 1. Merge all main plugin XMLs
    final_plugin_xml = _merge_main_plugin_xmls(ctx, plugin_xmls)

    # 2. Merge all optional plugin XMLs grouped by their module dependency
    module_to_merged_xmls = _merge_optional_plugin_xmls(ctx, optional_providers)

    # 3. Stitch the optional dependencies config tags into the main plugin XML
    final_plugin_xml = _stitch_module_dependencies(ctx, final_plugin_xml, module_to_merged_xmls)

    # 4. Package the merged XMLs into the final module JAR
    aggregated_jar = _package_module_jar(ctx, final_plugin_xml, module_to_merged_xmls)

    iml_module_info = ImlModuleInfo(
        module_jars = aggregated_jar,
        java_deps = [],
        test_provider = None,
        # buildifier: disable=native-java-info
        main_provider = JavaInfo(aggregated_jar, aggregated_jar),
        deps = deps,
        jvm_target = "21",
        module_visibility = None,
        names = [ctx.label.name],
    )

    return [
        DefaultInfo(files = depset([aggregated_jar])),
        iml_module_info,
    ]

aswb_plugin_module = rule(
    doc = """An intermediate aggregator for merging IDE plugin XML configurations.

Consumes multiple `aswb_module` targets, merging their collected `plugin.xml` fragments and
stitching in optional dependency configurations. The output is a `.jar` containing the final
merged `META-INF` structure, exposed via `ImlModuleInfo` for final packaging by rules like `studio_plugin`.""",
    attrs = {
        "deps": attr.label_list(
            mandatory = True,
            providers = [AswbModuleInfo],
            doc = "The aswb_modules whose plugin XML fragments and optional configurations should be merged.",
        ),
        "plugin_xml": attr.label(
            allow_single_file = True,
            doc = "An optional base plugin.xml file to which the fragments from `deps` will be appended.",
        ),
        "_singlejar": attr.label(
            default = Label("@bazel_tools//tools/jdk:singlejar"),
            executable = True,
            cfg = "exec",
        ),
        "_merge_xml_binary": attr.label(
            default = Label("//tools/adt/idea/aswb/build_defs:merge_xml"),
            executable = True,
            cfg = "exec",
        ),
        "_append_optional_xml_elements": attr.label(
            default = Label("//tools/adt/idea/aswb/build_defs:append_optional_xml_elements"),
            executable = True,
            cfg = "exec",
        ),
        "_package_meta_inf_files": attr.label(
            default = Label("//tools/adt/idea/aswb/build_defs:package_meta_inf_files"),
            executable = True,
            cfg = "exec",
        ),
    },
    implementation = _aswb_plugin_module_impl,
)
