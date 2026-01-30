"""Macro for defining java_library targets in test data."""

def java_library_for_test_data(name, **kwargs):
    """A java_library target for test data."""
    native.java_library(
        name = name,
        **kwargs
    )
