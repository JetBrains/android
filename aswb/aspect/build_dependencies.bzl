"""Aspects to build and collect project dependencies."""

load(
    ":build_dependencies_android_deps.bzl",
    _ide_android_not_validated = "IDE_ANDROID",
)

# Load external dependencies of this aspect. These are loaded in a separate file and re-exported as necessary
# to make supporting other versions of bazel easier, by replacing build_dependencies_deps.bzl.
load(
    ":build_dependencies_deps.bzl",
    "ZIP_TOOL_LABEL",
    _ide_cc_not_validated = "IDE_CC",
    _ide_java_not_validated = "IDE_JAVA",
    _ide_java_proto_not_validated = "IDE_JAVA_PROTO",
    _ide_kotlin_not_validated = "IDE_KOTLIN",
)

ALWAYS_BUILD_RULES = "java_proto_library,java_lite_proto_library,java_mutable_proto_library,kt_proto_library_helper,_java_grpc_library,_java_lite_grpc_library,kt_grpc_library_helper,java_stubby_library,kt_stubby_library_helper,aar_import,java_import, j2kt_native_import"

def _rule_function(
        rule):  # @unused
    return []

def _target_rule_function(
        target,  # @unused
        rule):  # @unused
    return []

def _unique(values):
    return {k: None for k in values}.keys()

def _validate_ide(unvalidated, template):
    "Basic validation that a provided implementation conforms to a given template"
    for a in dir(template):
        if not hasattr(unvalidated, a):
            fail("attribute missing: ", a, unvalidated)
        elif type(getattr(unvalidated, a)) != type(getattr(template, a)):
            fail("attribute type mismatch: ", a, type(getattr(unvalidated, a)), type(getattr(template, a)))
    return struct(**{a: getattr(unvalidated, a) for a in dir(template) if a not in dir(struct())})

IDE_JAVA = _validate_ide(
    _ide_java_not_validated,
    template = struct(
        srcs_attributes = [],  # Additional srcs like attributes.
        get_java_info = _target_rule_function,  # A function that takes a rule and returns a JavaInfo like structure (or the provider itself).
    ),
)

IDE_ANDROID = _validate_ide(
    _ide_android_not_validated,
    template = struct(
        get_android_info = _target_rule_function,  # A function that takes a rule and returns a JavaInfo like structure (or the provider itself).
    ),
)

IDE_KOTLIN = _validate_ide(
    _ide_kotlin_not_validated,
    template = struct(
        srcs_attributes = [],  # Additional srcs like attributes.
        follow_attributes = [],  # Additional attributes for the aspect to follow and request DependenciesInfo provider.
        follow_additional_attributes = [],  # Additional attributes for the aspect to follow without requesting DependenciesInfo provider.
        followed_dependencies = _rule_function,  # A function that takes a rule and returns a list of dependencies (targets or toolchain containers).
        toolchains_aspects = [],  # Toolchain types for the aspect to follow.
        get_kotlin_info = _target_rule_function,  # A function that takes a rule and returns a marker struct if the target
        # was recognised as a Kotlin related target and `followed_dependenices` must be called.
    ),
)

IDE_JAVA_PROTO = _validate_ide(
    _ide_java_proto_not_validated,
    template = struct(
        get_java_proto_info = _target_rule_function,  # A function that takes a rule and returns a marker structure (empty for now).
        srcs_attributes = [],  # Additional srcs like attributes.
        follow_attributes = [],  # Additional attributes for the aspect to follow and request DependenciesInfo provider.
        followed_dependencies = _rule_function,  # A function that takes a rule and returns a list of dependencies (targets or toolchain containers).
        toolchains_aspects = [],  # Toolchain types for the aspect to follow.
    ),
)

IDE_CC = _validate_ide(
    _ide_cc_not_validated,
    template = struct(
        c_compile_action_name = "",  # An action named to be used with cc_common.get_memory_inefficient_command_line or similar.
        cpp_compile_action_name = "",  # An action named to be used with cc_common.get_memory_inefficient_command_line or similar.
        follow_attributes = ["_cc_toolchain"],  # Additional attributes for the aspect to follow and request DependenciesInfo provider.
        toolchains_aspects = [],  # Toolchain types for the aspect to follow.
        toolchain_target = _rule_function,  # A function that takes a rule and returns a toolchain target (or a toolchain container).
    ),
)

JVM_SRC_ATTRS = _unique(["srcs"] + IDE_JAVA.srcs_attributes + IDE_JAVA_PROTO.srcs_attributes + IDE_KOTLIN.srcs_attributes)

def _noneToEmpty(d):
    return d if d else depset()

def _package_dependencies_impl(target, ctx):
    dep_info = target[DependenciesInfo]
    java_info = IDE_JAVA.get_java_info(target, ctx.rule)

    java_info_files = _noneToEmpty(dep_info.java_info_files)
    cc_info_files = _noneToEmpty(dep_info.cc_info_files)

    return [OutputGroupInfo(
        qs_jars = _noneToEmpty(dep_info.compile_time_jars),
        qs_transitive_runtime_jars = java_info.transitive_runtime_jars_depset if java_info else depset(),
        qs_info = java_info_files,
        qs_aars = _noneToEmpty(dep_info.aars),
        qs_gensrcs = _noneToEmpty(dep_info.gensrcs),
        qs_cc_headers = _noneToEmpty(dep_info.cc_headers),
        qs_cc_info = cc_info_files,
    )]

def _write_java_target_info(ctx, label, target_info):
    """Write java target info to a file in proto format.

    The proto format used is defined by proto bazel.intellij.JavaArtifacts.
    """
    file_name = label.name + ".java-info.txt"
    artifact_info_file = ctx.actions.declare_file(file_name)
    ctx.actions.write(
        artifact_info_file,
        _encode_target_info_proto(target_info),
    )
    return artifact_info_file

def _write_cc_target_info(label, cc_compilation_info, ctx):
    """Write CC target info to a file in proto format.

    The proto format used defined by proto bazel.intellij.CcCompilationInfo.
    """
    cc_info_file_name = label.name + ".cc-info.txt"
    cc_info_file = ctx.actions.declare_file(cc_info_file_name)
    ctx.actions.write(
        cc_info_file,
        _encode_cc_compilation_info_proto(label, cc_compilation_info),
    )
    return cc_info_file

DependenciesInfo = provider(
    "The out-of-project dependencies",
    fields = {
        "label": "the label of a target it describes",
        "java_info_file": "a file containing java related info",
        "java_info_files": "a list of java info files",
        "compile_time_jars": "a list of jars generated by targets",
        "aars": "a list of aars with resource files",
        "gensrcs": "a list of sources generated by project targets",
        "expand_sources": "boolean, true if the sources for this target should be expanded when it appears inside another rules srcs list",
        "cc_info_files": "a list of cc info files",
        "cc_compilation_info": "a structure containing info required to compile cc sources",
        "cc_headers": "a depset of generated headers required to compile cc sources",
        "cc_toolchain_info": "struct containing cc toolchain info, with keys file (the output file) and id (unique ID for the toolchain info, referred to from elsewhere)",
    },
)

def create_dependencies_info(
        label,
        java_info_file = None,
        java_info_files = depset(),
        compile_time_jars = depset(),
        aars = depset(),
        gensrcs = depset(),
        expand_sources = False,
        cc_info_files = depset(),
        cc_compilation_info = None,
        cc_headers = depset(),
        cc_toolchain_info = None):
    """A helper function to create a DependenciesInfo provider instance."""
    return DependenciesInfo(
        label = label,
        java_info_file = java_info_file,
        java_info_files = java_info_files,
        compile_time_jars = compile_time_jars,
        aars = aars,
        gensrcs = gensrcs,
        expand_sources = expand_sources,
        cc_info_files = cc_info_files,
        cc_compilation_info = cc_compilation_info,
        cc_headers = cc_headers,
        cc_toolchain_info = cc_toolchain_info,
    )

def create_java_dependencies_info(
        info_file,
        info_files,
        compile_time_jars,
        aars,
        gensrcs,
        expand_sources):
    """A helper function to create a DependenciesInfo provider instance."""
    return struct(
        info_file = info_file,
        info_files = info_files,
        compile_time_jars = compile_time_jars,
        aars = aars,
        gensrcs = gensrcs,
        expand_sources = expand_sources,
    )

def create_cc_dependencies_info(
        cc_info_files = depset(),
        cc_compilation_info = None,
        cc_headers = depset(),
        cc_toolchain_info = None):
    """A helper function to create a DependenciesInfo provider instance."""
    return struct(
        cc_info_files = cc_info_files,
        cc_compilation_info = cc_compilation_info,
        cc_headers = cc_headers,
        cc_toolchain_info = cc_toolchain_info,
    )

def create_cc_toolchain_info(
        cc_toolchain_info = None):
    """A helper function to create a DependenciesInfo provider instance."""
    return struct(
        cc_toolchain_info = cc_toolchain_info,
    )

def merge_dependencies_info(
        target,
        ctx,  # @unused
        java_dep_info,
        cc_dep_info,
        cc_toolchain_dep_info):
    """Merge multiple DependenciesInfo providers into one.

    Depsets and dicts are merged. For members such as `cc_compilation_info`, we require that at most one of the
    DependenciesInfo's defines this which should always be the case.

    Args:
      target: the target.
      ctx: the context which is ignored in this function.
      java_dep_info: java dep info.
      cc_dep_info: cc dep info.
      cc_toolchain_dep_info: cc toolchain dep info.
    Returns:
      Merged dependencies info.
    """

    if not java_dep_info and not cc_dep_info and not cc_toolchain_dep_info:
        return create_dependencies_info(label = target.label)

    merged = create_dependencies_info(
        label = target.label,
        java_info_file = java_dep_info.info_file if java_dep_info else None,
        java_info_files = java_dep_info.info_files if java_dep_info else None,
        compile_time_jars = java_dep_info.compile_time_jars if java_dep_info else None,
        aars = java_dep_info.aars if java_dep_info else None,
        gensrcs = java_dep_info.gensrcs if java_dep_info else None,
        expand_sources = java_dep_info.expand_sources if java_dep_info else None,
        cc_info_files = cc_dep_info.cc_info_files if cc_dep_info else None,
        cc_compilation_info = cc_dep_info.cc_compilation_info if cc_dep_info else None,
        cc_headers = cc_dep_info.cc_headers if cc_dep_info else None,
        cc_toolchain_info = cc_toolchain_dep_info.cc_toolchain_info if cc_toolchain_dep_info else None,
    )
    return merged

def one_of(a, b):
    """
    Returns whichever of a or b is not None, None if both are, or fails if neither are.

    Args:
      a: maybe None argument.
      b: maybe None argument.
    Returns:
      Whichever of a or b is not None, None if both are, or fails if neither are.
    """
    if a == None:
        return b
    if b == None:
        return a
    fail("Expected at most one, but got both", a, b)

def _encode_target_info_proto(target_info):
    contents = struct(
        target = target_info.target,
        dep_java_info_files = _encode_file_list(target_info.dep_java_info_files),
        jars = _encode_file_list(target_info.jars),
        output_jars = _encode_file_list(target_info.output_jars),
        ide_aars = _encode_file_list(target_info.ide_aars),
        gen_srcs = _encode_file_list(target_info.gen_srcs),
        srcs = target_info.srcs,
        srcjars = target_info.srcjars,
        android_resources_package = target_info.android_resources_package,
    )
    return proto.encode_text(contents)

def _encode_file_list(files):
    """Encodes a list of files as a struct.

    Returns:
      A list fo structs matching the bazel.intellij.OutputArtifact proto message.
    """
    r = []
    for f in files:
        if f.is_directory:
            r.append(struct(directory = f.path))
        else:
            r.append(struct(file = f.path))
    return r

def _encode_cc_compilation_info_proto(label, cc_compilation_info):
    return proto.encode_text(
        struct(targets = [
            struct(
                label = str(label),
                defines = cc_compilation_info.transitive_defines,
                include_directories = cc_compilation_info.transitive_include_directory,
                quote_include_directories = cc_compilation_info.transitive_quote_include_directory,
                system_include_directories = cc_compilation_info.transitive_system_include_directory,
                framework_include_directories = cc_compilation_info.framework_include_directory,
                gen_hdrs = _encode_file_list(cc_compilation_info.gen_headers),
                toolchain_id = cc_compilation_info.toolchain_id,
            ),
        ]),
    )

# Do not remove parameters. They may be used to configure experiment values.
#buildifier: disable=unused_parameters
def package_dependencies(unused_parameters):
    return aspect(
        implementation = _package_dependencies_impl,
        required_aspect_providers = [[DependenciesInfo]],
    )

def declares_android_resources(target, ctx):
    """
    Returns true if the target has resource files and an android provider.

    The IDE needs aars from targets that declare resources. AndroidIdeInfo
    has a defined_android_resources flag, but this returns true for additional
    cases (aidl files, etc), so we check if the target has resource files.

    Args:
      target: the target.
      ctx: the context.
    Returns:
      True if the target has resource files and an android provider.
    """
    if IDE_ANDROID.get_android_info(target, ctx.rule) == None:
        return False
    return hasattr(ctx.rule.attr, "resource_files") and len(ctx.rule.attr.resource_files) > 0

def declares_aar_import(ctx):
    """
    Returns true if the target has aar and is aar_import rule.

    Args:
      ctx: the context.
    Returns:
      True if the target has aar and is aar_import rule.
    """
    return ctx.rule.kind == "aar_import" and hasattr(ctx.rule.attr, "aar")

def _collect_dependencies_impl(target, ctx, params):
    return _collect_dependencies_core_impl(
        target,
        ctx,
        params,
    )

def collect_dependencies_for_test(target, ctx, include = []):
    return _collect_dependencies_core_impl(
        target,
        ctx,
        struct(
            include = include,
            exclude = "",
            always_build_rules = ALWAYS_BUILD_RULES,
            generate_aidl_classes = None,
            use_generated_srcjars = True,
        ),
    )

def _package_prefix_match(package, prefix):
    if (package == prefix):
        return True
    if package.startswith(prefix) and package[len(prefix)] == "/":
        return True
    return False

def _get_repo_name(label):
    # The API to get the repo name changed between bazel versions. Use whichever exists:
    return label.repo_name if "repo_name" in dir(label) else label.workspace_name

def _target_within_project_scope(label, include, exclude):
    repo = _get_repo_name(label)
    package = label.package
    result = False
    if include:
        if len(include) == 1 and include[0] == "//":
            # when workspace root is included
            result = True
        else:
            for inc in [Label(i) for i in include]:
                if _get_repo_name(inc) == repo and _package_prefix_match(package, inc.package):
                    result = True
                    break
    if result and len(exclude) > 0:
        for exc in [Label(i) for i in exclude]:
            if _get_repo_name(exc) == repo and _package_prefix_match(package, exc.package):
                result = False
                break
    return result

def _get_dependency_attribute(rule, attr):
    if hasattr(rule.attr, attr):
        to_add = getattr(rule.attr, attr)
        if type(to_add) == "list":
            return [t for t in to_add if type(t) == "Target"]
        elif type(to_add) == "Target":
            return [to_add]
    return []

def _get_followed_java_dependency_infos(
        label,  # @unused
        rule):
    deps = []
    for attr in FOLLOW_JAVA_ATTRIBUTES:
        deps.extend(_get_dependency_attribute(rule, attr))

    deps.extend(IDE_JAVA_PROTO.followed_dependencies(rule))
    deps.extend(IDE_KOTLIN.followed_dependencies(rule))

    return {
        str(dep[DependenciesInfo].label): dep[DependenciesInfo]  # NOTE: This handles duplicates.
        for dep in deps
        if DependenciesInfo in dep and dep[DependenciesInfo].java_info_files
    }

def _collect_own_java_artifacts(
        target,
        ctx,
        always_build_rules,
        generate_aidl_classes,
        use_generated_srcjars,
        target_is_within_project_scope):
    rule = ctx.rule

    must_build_main_artifacts = (
        not target_is_within_project_scope or rule.kind in always_build_rules
    )

    own_jar_files = []
    own_jar_depsets = []
    own_output_jar_files = []
    own_ide_aar_files = []
    own_gensrc_files = []
    own_src_file_paths = []
    own_srcjar_file_paths = []
    resource_package = ""

    java_info = IDE_JAVA.get_java_info(target, ctx.rule)
    kotlin_info = IDE_KOTLIN.get_kotlin_info(target, ctx.rule)

    # Targets recognised as java_proto_info can have java_info dependencies.
    java_proto_info = IDE_JAVA_PROTO.get_java_proto_info(target, ctx.rule)
    android_info = IDE_ANDROID.get_android_info(target, ctx.rule)

    if must_build_main_artifacts:
        # For rules that we do not follow dependencies of (either because they don't
        # have further dependencies with JavaInfo or do so in attributes we don't care)
        # we gather all their transitive dependencies. If they have dependencies, we
        # only gather their own compile jars and continue down the tree.
        # This is done primarily for rules like proto, whose toolchain classes
        # are collected via attribute traversal, but still requires jars for any
        # proto deps of the underlying proto_library.
        if java_info:
            own_jar_depsets.append(java_info.compile_jars_depset)
            own_output_jar_files = java_info.java_output_compile_jars

        if declares_android_resources(target, ctx):
            ide_aar = _get_ide_aar_file(target, ctx)
            if ide_aar:
                # TODO(mathewi) - handle source aars
                if not ide_aar.is_source:
                    own_ide_aar_files.append(ide_aar)
        elif declares_aar_import(ctx):
            ide_aar = rule.attr.aar.files.to_list()[0]

            # TODO(mathewi) - handle source aars
            if not ide_aar.is_source:
                own_ide_aar_files.append(ide_aar)

    else:
        if android_info != None:
            resource_package = android_info.java_package

            if generate_aidl_classes:
                add_base_idl_jar = False
                idl_jar = android_info.idl_class_jar
                if idl_jar != None:
                    own_jar_files.append(idl_jar)
                    add_base_idl_jar = True

                generated_java_files = android_info.idl_generated_java_files
                if generated_java_files:
                    own_gensrc_files += generated_java_files
                    add_base_idl_jar = True

                # An AIDL base jar needed for resolving base classes for aidl generated stubs.
                if add_base_idl_jar:
                    if hasattr(rule.attr, "_aidl_lib"):
                        own_jar_depsets.append(rule.attr._aidl_lib.files)
                    elif hasattr(rule.attr, "_android_sdk") and hasattr(android_common, "AndroidSdkInfo"):
                        android_sdk_info = getattr(rule.attr, "_android_sdk")[android_common.AndroidSdkInfo]
                        own_jar_depsets.append(android_sdk_info.aidl_lib.files)

        # Add generated java_outputs (e.g. from annotation processing)
        generated_class_jars = []
        if java_info:
            for generated_output in java_info.generated_outputs:
                # Prefer source jars if they exist and are requested:
                if use_generated_srcjars and generated_output.generated_source_jar:
                    own_gensrc_files.append(generated_output.generated_source_jar)
                elif generated_output.generated_class_jar:
                    generated_class_jars.append(generated_output.generated_class_jar)

        if generated_class_jars:
            own_jar_files += generated_class_jars

        # Add generated sources for included targets
        for src_attr in JVM_SRC_ATTRS:
            if hasattr(rule.attr, src_attr):
                for src in getattr(rule.attr, src_attr):
                    # If the target that generates this source specifies that
                    # the sources should be expanded, we ignore the generated
                    # sources - the IDE will substitute the target sources
                    # themselves instead.
                    if not (DependenciesInfo in src and src[DependenciesInfo].expand_sources):
                        for file in src.files.to_list():
                            if not file.is_source:
                                own_gensrc_files.append(file)

    if not target_is_within_project_scope:
        for src_attr in JVM_SRC_ATTRS:
            if hasattr(rule.attr, src_attr):
                for src in getattr(rule.attr, src_attr):
                    for file in src.files.to_list():
                        if file.is_source:
                            own_src_file_paths.append(file.path)
                        else:
                            own_gensrc_files.append(file)
        if hasattr(rule.attr, "srcjar"):
            if rule.attr.srcjar and type(rule.attr.srcjar) == "Target":
                for file in rule.attr.srcjar.files.to_list():
                    if file.is_source:
                        own_srcjar_file_paths.append(file.path)
                    else:
                        own_gensrc_files.append(file)

    if not (java_info or kotlin_info or android_info or java_proto_info or own_gensrc_files or own_src_file_paths or own_srcjar_file_paths):
        return None
    if own_jar_files or len(own_jar_depsets) > 1:
        own_jar_depset = depset(own_jar_files, transitive = own_jar_depsets)
        # here two cases left: own_jar_files is None/[] and own_jar_depsets is None/[] or a singleton.

    elif not own_jar_depsets:
        own_jar_depset = depset()
    elif len(own_jar_depsets) == 1:
        own_jar_depset = own_jar_depsets[0]
    else:
        # See the comment above.
        fail("Unexpected: " + str(own_jar_files) + " " + str(own_jar_depsets))

    return struct(
        jar_depset = own_jar_depset,
        output_jar_depset = depset(own_output_jar_files),
        ide_aar_depset = depset(own_ide_aar_files),
        gensrc_depset = depset(own_gensrc_files),
        src_file_paths = own_src_file_paths,
        srcjar_file_paths = own_srcjar_file_paths,
        android_resources_package = resource_package,
    )

def _target_to_artifact_entry(
        label = "",
        dep_java_info_files = [],
        jars = [],
        output_jars = [],
        ide_aars = [],
        gen_srcs = [],
        srcs = [],
        srcjars = [],
        android_resources_package = ""):
    return struct(
        target = label,
        dep_java_info_files = dep_java_info_files,
        jars = jars,
        output_jars = output_jars,
        ide_aars = ide_aars,
        gen_srcs = gen_srcs,
        srcs = srcs,
        srcjars = srcjars,
        android_resources_package = android_resources_package,
    )

# Collects artifacts exposed by this java-like (i.e. java, android or proto-for-java) target and its dependencies if it is such a target.
# For non-Java targets only generated sources are collected without recursing to its dependencies. Therefore, for example, if there are
# generated proto files they won't be collected and this use case will need to be supported explicitly. Not following non-Java dependencies
# while collecting Java info files substantially reduces the number of generated and fetched info files.
def _collect_own_and_dependency_java_artifacts(
        target,
        ctx,
        dependency_infos,
        always_build_rules,
        generate_aidl_classes,
        use_generated_srcjars,
        target_is_within_project_scope):
    own_files = _collect_own_java_artifacts(
        target,
        ctx,
        always_build_rules,
        generate_aidl_classes,
        use_generated_srcjars,
        target_is_within_project_scope,
    )

    if not own_files:
        # Any target recognized as a java related target gets at least
        # an empty own_files structure.
        return None

    target_to_artifacts = {}

    # Flattening is fine here as these are files from a single target (maybe some are re-exported from a few of its depende3ncies).
    dep_java_info_files = [info.java_info_file for info in dependency_infos.values() if info.java_info_file]
    jars = own_files.jar_depset.to_list()
    output_jars = own_files.output_jar_depset.to_list()
    ide_aars = own_files.ide_aar_depset.to_list()
    gen_srcs = own_files.gensrc_depset.to_list()  # Flattening is fine here (these are files from one target)
    java_info_file = _write_java_target_info(ctx, target.label, _target_to_artifact_entry(
        label = str(target.label),
        dep_java_info_files = dep_java_info_files,  # No flattening here. This is a list of direct dependencies only.
        jars = jars,
        output_jars = output_jars,
        ide_aars = ide_aars,
        gen_srcs = gen_srcs,
        srcs = own_files.src_file_paths,
        srcjars = own_files.srcjar_file_paths,
        android_resources_package = own_files.android_resources_package,
    ))

    own_and_transitive_jar_depsets = [own_files.jar_depset, own_files.output_jar_depset]  # Copy to prevent changes to own_files.jar_depset.
    own_and_transitive_ide_aar_depsets = []
    own_and_transitive_gensrc_depsets = []

    for info in dependency_infos.values():
        own_and_transitive_jar_depsets.append(info.compile_time_jars)
        own_and_transitive_ide_aar_depsets.append(info.aars)
        own_and_transitive_gensrc_depsets.append(info.gensrcs)

    return struct(
        java_info_file = java_info_file,
        compile_jars = depset(transitive = own_and_transitive_jar_depsets),
        aars = depset(ide_aars, transitive = own_and_transitive_ide_aar_depsets),
        gensrcs = depset(gen_srcs, transitive = own_and_transitive_gensrc_depsets),
    )

def _get_cc_toolchain_dependency_info(rule):
    cc_toolchain_target = IDE_CC.toolchain_target(rule)
    if cc_toolchain_target and DependenciesInfo in cc_toolchain_target:
        return cc_toolchain_target[DependenciesInfo]
    return None

def _collect_own_and_dependency_cc_info(target, rule):
    dependency_info = _get_cc_toolchain_dependency_info(rule)
    compilation_context = target[CcInfo].compilation_context
    cc_toolchain_info = None
    if dependency_info:
        cc_toolchain_info = dependency_info.cc_toolchain_info

    gen_headers = depset()
    compilation_info = None
    if compilation_context:
        gen_headers = depset([f for f in compilation_context.headers.to_list() if not f.is_source])

        compilation_info = struct(
            transitive_defines = compilation_context.defines.to_list(),
            transitive_include_directory = compilation_context.includes.to_list(),
            transitive_quote_include_directory = compilation_context.quote_includes.to_list(),
            transitive_system_include_directory = (
                compilation_context.system_includes.to_list() + (
                    # external_includes was added in newer versions of bazel
                    compilation_context.external_includes.to_list() if hasattr(compilation_context, "external_includes") else []
                )
            ),
            framework_include_directory = compilation_context.framework_includes.to_list(),
            gen_headers = gen_headers.to_list(),
            toolchain_id = cc_toolchain_info.id if cc_toolchain_info else None,
        )
    return struct(
        compilation_info = compilation_info,
        gen_headers = gen_headers,
        cc_toolchain_info = cc_toolchain_info,
    )

def _collect_dependencies_core_impl(
        target,
        ctx,
        params):
    if hasattr(ctx.rule.attr, "tags"):
        if "no-ide" in ctx.rule.attr.tags:
            return create_dependencies_info(label = target.label)

    java_dep_info = _collect_java_dependencies_core_impl(
        target,
        ctx,
        params,
    )
    cc_dep_info = None
    if CcInfo in target:
        cc_dep_info = _collect_cc_dependencies_core_impl(target, ctx)
    cc_toolchain_dep_info = None
    if cc_common.CcToolchainInfo in target:
        cc_toolchain_dep_info = _collect_cc_toolchain_info(target, ctx)
    return merge_dependencies_info(target, ctx, java_dep_info, cc_dep_info, cc_toolchain_dep_info)

def _collect_java_dependencies_core_impl(
        target,
        ctx,
        params):
    target_is_within_project_scope = _target_within_project_scope(target.label, params.include, params.exclude)
    dependency_infos = _get_followed_java_dependency_infos(target.label, ctx.rule)

    own_and_dependencies = _collect_own_and_dependency_java_artifacts(
        target,
        ctx,
        dependency_infos,
        params.always_build_rules,
        params.generate_aidl_classes,
        params.use_generated_srcjars,
        target_is_within_project_scope,
    )

    if own_and_dependencies == None:
        return None

    java_info_file = own_and_dependencies.java_info_file
    compile_jars = own_and_dependencies.compile_jars
    aars = own_and_dependencies.aars
    gensrcs = own_and_dependencies.gensrcs

    expand_sources = False
    if hasattr(ctx.rule.attr, "tags"):
        if "ij-ignore-source-transform" in ctx.rule.attr.tags:
            expand_sources = True

    return create_java_dependencies_info(
        info_file = java_info_file,
        info_files = depset([java_info_file], transitive = [d.java_info_files for d in dependency_infos.values()]),
        compile_time_jars = compile_jars,
        aars = aars,
        gensrcs = gensrcs,
        expand_sources = expand_sources,
    )

def _collect_cc_dependencies_core_impl(target, ctx):
    cc_info = _collect_own_and_dependency_cc_info(target, ctx.rule)
    cc_info_files = []
    cc_info_files = [_write_cc_target_info(target.label, cc_info.compilation_info, ctx)] + ([cc_info.cc_toolchain_info.file] if cc_info.cc_toolchain_info else [])

    return create_cc_dependencies_info(
        cc_info_files = depset(cc_info_files),
        cc_compilation_info = cc_info.compilation_info,
        cc_headers = cc_info.gen_headers,
        cc_toolchain_info = cc_info.cc_toolchain_info,
    )

def _collect_cc_toolchain_info(target, ctx):
    toolchain_info = target[cc_common.CcToolchainInfo]

    cpp_fragment = ctx.fragments.cpp

    # TODO(b/301235884): This logic is not quite right. `ctx` here is the context for the
    #  cc_toolchain target itself, so the `features` and `disabled_features` were using here are
    #  for the cc_toolchain, not the individual targets that this information will ultimately be
    #  used for. Instead, we should attach `toolchain_info` itself to the `DependenciesInfo`
    #  provider, and execute this logic once per top level cc target that we're building, to ensure
    #  that the right features are used.
    feature_config = cc_common.configure_features(
        ctx = ctx,
        cc_toolchain = toolchain_info,
        requested_features = ctx.features,
        unsupported_features = ctx.disabled_features + [
            # Note: module_maps appears to be necessary here to ensure the API works
            # in all cases, and to avoid the error:
            # Invalid toolchain configuration: Cannot find variable named 'module_name'
            # yaqs/3227912151964319744
            "module_maps",
        ],
    )
    c_variables = cc_common.create_compile_variables(
        feature_configuration = feature_config,
        cc_toolchain = toolchain_info,
        user_compile_flags = cpp_fragment.copts + cpp_fragment.conlyopts,
    )
    cpp_variables = cc_common.create_compile_variables(
        feature_configuration = feature_config,
        cc_toolchain = toolchain_info,
        user_compile_flags = cpp_fragment.copts + cpp_fragment.cxxopts,
    )
    c_options = cc_common.get_memory_inefficient_command_line(
        feature_configuration = feature_config,
        action_name = IDE_CC.c_compile_action_name,
        variables = c_variables,
    )
    cpp_options = cc_common.get_memory_inefficient_command_line(
        feature_configuration = feature_config,
        action_name = IDE_CC.cpp_compile_action_name,
        variables = cpp_variables,
    )
    toolchain_id = str(target.label) + "%" + toolchain_info.target_gnu_system_name

    cc_toolchain_info = struct(
        id = toolchain_id,
        compiler_executable = toolchain_info.compiler_executable,
        cpu = toolchain_info.cpu,
        compiler = toolchain_info.compiler,
        target_name = toolchain_info.target_gnu_system_name,
        built_in_include_directories = toolchain_info.built_in_include_directories,
        c_options = c_options,
        cpp_options = cpp_options,
    )

    cc_toolchain_file_name = target.label.name + "." + cc_toolchain_info.target_name + ".txt"
    cc_toolchain_file = ctx.actions.declare_file(cc_toolchain_file_name)
    ctx.actions.write(
        cc_toolchain_file,
        proto.encode_text(
            struct(toolchains = cc_toolchain_info),
        ),
    )

    return create_cc_toolchain_info(
        cc_toolchain_info = struct(file = cc_toolchain_file, id = toolchain_id),
    )

def _get_ide_aar_file(target, ctx):
    """
    Builds a resource only .aar file for the ide.

    The IDE requires just resource files and the manifest from the IDE.
    Moreover, there are cases when the existing rules fail to build a full .aar
    file from a library, on which other targets can still depend.

    The function builds a minimalistic .aar file that contains resources and the
    manifest only.
    """
    android_info = IDE_ANDROID.get_android_info(target, ctx.rule)
    full_aar = android_info.aar
    if full_aar:
        resource_files = _collect_resource_files(ctx)
        resource_map = _build_ide_aar_file_map(android_info.manifest, resource_files)
        aar = ctx.actions.declare_file(full_aar.short_path.removesuffix(".aar") + "_ide/" + full_aar.basename)
        _package_ide_aar(ctx, aar, resource_map)
        return aar
    else:
        return None

def _collect_resource_files(ctx):
    """
    Collects the list of resource files from the target rule attributes.
    """

    # Unfortunately, there are no suitable bazel providers that describe
    # resource files used a target.
    # However, AndroidIdeInfo returns a reference to a so-called resource APK
    # file, which contains everything the IDE needs to load resources from a
    # given library. However, this format is currently supported by Android
    # Studio in the namespaced resource mode. We should consider conditionally
    # enabling support in Android Studio and use them in ASwB, instead of
    # building special .aar files for the IDE.
    resource_files = []
    for t in ctx.rule.attr.resource_files:
        for f in t.files.to_list():
            resource_files.append(f)
    return resource_files

def _build_ide_aar_file_map(manifest_file, resource_files):
    """
    Build the list of files and their paths as they have to appear in .aar.
    """
    file_map = {}
    file_map["AndroidManifest.xml"] = manifest_file
    for f in resource_files:
        res_dir_path = f.short_path \
            .removeprefix(android_common.resource_source_directory(f)) \
            .removeprefix("/")
        if res_dir_path:
            res_dir_path = "res/" + res_dir_path
            file_map[res_dir_path] = f
    return file_map

def _package_ide_aar(ctx, aar, file_map):
    """
    Declares a file and defines actions to build .aar according to file_map.
    """
    files_map_args = []
    files = []
    for aar_dir_path, f in file_map.items():
        files.append(f)
        files_map_args.append("%s=%s" % (aar_dir_path, f.path))

    ctx.actions.run(
        mnemonic = "GenerateIdeAar",
        executable = ctx.executable._build_zip,
        inputs = files,
        outputs = [aar],
        arguments = ["c", aar.path] + files_map_args,
    )

# List of tuples containing:
#   1. An attribute for the aspect to traverse
#   2. A list of rule kinds to specify which rules for which the attribute labels
#      need to be added as dependencies. If empty, the attribute is followed for
#      all rules.
FOLLOW_JAVA_ATTRIBUTES = [
    "deps",
    "exports",
    "srcs",
    "_junit",
    "_aspect_proto_toolchain_for_javalite",
    "_aspect_java_proto_toolchain",
] + IDE_KOTLIN.follow_attributes

FOLLOW_JAVA_PROTO_ATTRIBUTES = IDE_JAVA_PROTO.follow_attributes
FOLLOW_CC_ATTRIBUTES = IDE_CC.follow_attributes

FOLLOW_ADDITIONAL_ATTRIBUTES = ["runtime", "_toolchain"] + IDE_KOTLIN.follow_additional_attributes

FOLLOW_ATTRIBUTES = _unique(FOLLOW_JAVA_ATTRIBUTES + FOLLOW_JAVA_PROTO_ATTRIBUTES + FOLLOW_CC_ATTRIBUTES + FOLLOW_ADDITIONAL_ATTRIBUTES)

TOOLCHAINS_ASPECTS = IDE_KOTLIN.toolchains_aspects + IDE_JAVA_PROTO.toolchains_aspects + IDE_CC.toolchains_aspects

def collect_dependencies(parameters):
    def _impl(target, ctx):
        return _collect_dependencies_impl(target, ctx, parameters)

    return aspect(
        implementation = _impl,
        provides = [DependenciesInfo],
        attr_aspects = FOLLOW_ATTRIBUTES,
        attrs = {
            "_build_zip": attr.label(
                allow_files = True,
                cfg = "exec",
                executable = True,
                default = ZIP_TOOL_LABEL,
            ),
        },
        fragments = ["cpp"],
        **{
            "toolchains_aspects": TOOLCHAINS_ASPECTS,
        } if TOOLCHAINS_ASPECTS else {}
    )
