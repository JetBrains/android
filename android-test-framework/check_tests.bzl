# Defines a macro for checking iml module tests.

def check_tests(
        iml_module,
        disallow_gradle_project_tests = False,
        agp_test_module = None,
        gradle_project_tests_allowlist = None,
        **kwargs):
    """Checks iml_module tests meet certain requirements.

    Args:
        iml_module: The module to perform checks on
        disallow_gradle_project_tests: Fails if tests using AndroidGradleProjectRule or
          AndroidGradleTestCase are added to iml_module.
        agp_test_module: The module intended to contain gradle project tests.
        gradle_project_tests_allowlist: If disallow_gradle_project_tests is set, only gradle tests
          in the allowlist are permitted. Allowlists are text files containing full class names
          delimited by newlines, and are generated in check_tests output.
    """
    test_jar = "%s_test.jar" % iml_module
    jvm_flags = kwargs.pop("jvm_flags", [])
    jvm_flags.append("-Dtestlib=$(location %s)" % test_jar)
    if disallow_gradle_project_tests:
        jvm_flags.append("-Ddisallow_gradle_project_tests=true")
    if agp_test_module:
        jvm_flags.append("-Dagp_test_module=%s" % agp_test_module)

    data = kwargs.pop("data", [])
    data.append(test_jar)
    if gradle_project_tests_allowlist:
        data.append(gradle_project_tests_allowlist)
        jvm_flags.append("-Dgradle_project_tests_allowlist=$(location %s)" % gradle_project_tests_allowlist)

    name = "%s_check_tests" % iml_module[iml_module.index(":") + 1::]
    native.java_test(
        name = name,
        runtime_deps = [
            "//tools/adt/idea/android-test-framework:intellij.android.testFramework_testlib",
            "%s_testlib" % iml_module,
        ],
        jvm_flags = jvm_flags,
        data = data,
        test_class = "com.android.tools.idea.testing.ImlModuleCheckTests",
    )
