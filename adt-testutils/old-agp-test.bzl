"""A macro for running @OldAgpTests using OldAgpSuite."""

def old_agp_test(
        name,
        iml_module,
        gradle_version,
        agp_version,
        maven_deps,
        ignore_other_tests,
        **kwargs):
    """Creates a test running with OldAgpSuite.

    Args:
      name: The name of the test. Gradle and AGP versions are
            appended to build the target name. i.e.,
            ${name}_gradle_${gradle_version}_agp_${agp_version}
      iml_module: The iml_module containing tests annotated with @OldAgpTest
      gradle_version: The gradle.version system property argument
      agp_version: The agp.version system property argument
      maven_deps: The maven_repo dependencies required by the test
      ignore_other_tests: Ignores tests not annotated with OldAgpTest. Otherwise the
                          test runner will throw an error for tests missing annotations.
      kwargs: Additional arguments for java_test
    """

    # The java_test output jar of the iml_module macro
    test_jar = "%s_test.jar" % iml_module
    jvm_flags = kwargs.pop("jvm_flags", [])
    jvm_flags.append("-Dignore_other_tests=%s" % ignore_other_tests)
    jvm_flags.append("-Dtest_jar_path=$(location %s)" % test_jar)
    jvm_flags.append("-Dgradle.version=%s" % gradle_version)
    jvm_flags.append("-Dagp.version=%s" % agp_version)

    # Sets the system property for MavenRepoRule
    maven_repo_paths = ["$(location %s)" % maven_dep for maven_dep in maven_deps]
    jvm_flags.append("-Dtest.suite.repos=%s" % ",".join(maven_repo_paths))

    data = kwargs.pop("data", [])
    data.append(test_jar)
    data.extend(maven_deps)

    name = "%s_gradle_%s_agp_%s" % (name, gradle_version, agp_version)
    native.java_test(
        name = name,
        runtime_deps = [
            "%s_testlib" % iml_module,
        ],
        jvm_flags = jvm_flags,
        data = data,
        **kwargs
    )
