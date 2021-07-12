"""A macro for running @OldAgpTests using OldAgpSuite."""

def old_agp_test(
        name,
        iml_module,
        gradle_version,
        agp_version,
        **kwargs):
    """Creates a test running with OldAgpSuite.

    Args:
      name: The name of the test. The Gradle and AGP versions are
            appended to the name. e.g., $name_gradle$Version_agp$Version
      iml_module: The module containing tests annotated with @OldAgpTest
      gradle_version: The gradle.version system property argument
      agp_version: The agp.version system property argument
      kwargs: Additional arguments for java_test
    """
    fail("Not yet implemented")
