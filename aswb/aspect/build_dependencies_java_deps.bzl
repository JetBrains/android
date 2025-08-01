def _get_java_info(target, rule):
    if not JavaInfo in target:
        return None
    p = target[JavaInfo]
    generated_outputs = []
    java_output_compile_jars = []
    for java_output in p.java_outputs:
        if java_output.compile_jar:
            java_output_compile_jars.append(java_output.compile_jar)
        generated_outputs.append(
            struct(
                compile_jdeps = java_output.compile_jdeps,
                generated_source_jar = java_output.generated_source_jar,
                generated_class_jar = java_output.generated_class_jar,
            ),
        )
    return struct(
        compile_jars_depset = p.compile_jars,
        java_output_compile_jars = java_output_compile_jars,
        generated_outputs = generated_outputs,
        transitive_compile_time_jars_depset = p.transitive_compile_time_jars,
        transitive_runtime_jars_depset = p.transitive_runtime_jars,
    )

IDE_JAVA = struct(
    srcs_attributes = ["java_srcs", "java_test_srcs"],
    get_java_info = _get_java_info,
)
