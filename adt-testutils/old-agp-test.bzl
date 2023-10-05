"""A macro for running @OldAgpTests using OldAgpSuite."""

load("//tools/base/bazel:coverage.bzl", "coverage_java_test")

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
      **kwargs: Additional arguments for java_test
    """

    # The java_test output jar of the iml_module macro
    test_jar = "%s_test.jar" % iml_module
    jvm_flags = kwargs.pop("jvm_flags", [])
    jvm_flags.append("-Dignore_other_tests=%s" % ignore_other_tests)
    jvm_flags.append("-Dtest.suite.jar=$(location %s)" % test_jar)
    jvm_flags.append("-Dgradle.version=%s" % gradle_version)
    jvm_flags.append("-Dagp.version=%s" % agp_version)

    # Sets the system property for MavenRepoRule
    maven_repo_paths = ["$(location %s)" % maven_dep for maven_dep in maven_deps]
    jvm_flags.append("-Dtest.suite.repos=%s" % ",".join(maven_repo_paths))

    data = kwargs.pop("data", [])
    data.append(test_jar)
    data.extend(maven_deps)

    name = "%s_gradle_%s_agp_%s" % (name, gradle_version, agp_version)
    coverage_java_test(
        name = name,
        runtime_deps = [
            "%s_testlib" % iml_module,
        ],
        jvm_flags = jvm_flags,
        data = data,
        **kwargs
    )

def generate_old_agp_tests_from_list(name, iml_module, tests_list, ignore_locations = []):
    """Creates tests running with OldAgpSuite from a list of test descriptions.

    Having all test definitions as a list in one macro allows us to implement a check to ensure all
    OldAgpTest tests from the module are covered with a test target and thus will actually run.

    Args:
      name: The name macro used to generate tests.
      iml_module: The iml_module containing tests annotated with @OldAgpTest
      tests_list: list of kwargs objects, one per required test, containing arguments for that test.
                  See _local_old_agp_test and old_agp_test for test arguments description.
      ignore_locations: List of @OldAgpTest annotated locations (<full classname> or <full classname>#<methodname>)
                        to be ignored by the OldAgpTestTargetsChecker. It is needed when some tests
                        do not need to have a target to run, e.g. ignored, but check still fails for them.
    """
    tests_defined_versions = [test_kwargs["agp_version"] + "@" + test_kwargs["gradle_version"] for test_kwargs in tests_list]
    test_jar = "%s_test.jar" % iml_module

    native.java_test(
        name = "%s_check-version-pairs" % name,
        runtime_deps = [
            "//tools/adt/idea/android-test-framework:intellij.android.testFramework_testlib",
            "%s_testlib" % iml_module,
        ],
        jvm_flags = [
            "-Dold.agp.tests.check.jar=$(location %s)" % test_jar,
            "-Dold.agp.tests.check.ignore.list=%s" % ":".join(ignore_locations),
            "-Dagp.gradle.version.pair.targets=%s" % ":".join(tests_defined_versions),
        ],
        data = [
            test_jar,
        ],
        test_class = "com.android.testutils.junit4.OldAgpTestTargetsChecker",
    )
    for test_kwargs in tests_list:
        old_agp_test(name = name, iml_module = iml_module, **test_kwargs)

def get_agp_versions_from_tests_list(tests_list):
    return [test_kwargs["agp_version"] for test_kwargs in tests_list]
