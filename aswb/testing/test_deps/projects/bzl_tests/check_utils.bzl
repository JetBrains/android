"""Helper functions to perform checks."""

load("@bazel_skylib//lib:types.bzl", "types")
load("@rules_testing//lib/private:struct_subject.bzl", "StructSubject")

def label_info_factory(actual, *, meta):
    """Creates a new `LabelSubject` for asserting `Label` objects.

    Args:
        actual: ([`Label`]) the label to check against.
        meta: ([`ExpectMeta`]) the metadata about the call chain.

    Returns:
        [`LabelSubject`].
    """
    self = struct(actual = actual, meta = meta)
    return struct(
        actual = actual,
        equals = lambda *a, **k: _label_subject_equals(self, *a, **k),
    )

def _label_subject_equals(self, other):
    """Asserts the label is equal to `other`.

    Method: LabelSubject.equals

    Args:
        self: implicitly added.
        other: ([`Label`] | [`str`]) the expected value. If a `str` is passed, it
            will be converted to a `Label` using the `Label` function.
    """
    if types.is_string(other):
        other = Label(other)
    if self.actual == other:
        return
    self.meta.add_failure(
        "expected: {}".format(other),
        "actual: {}".format(self.actual),
    )

def target_factory(actual, *, meta):
    """Creates a subject for asserting Targets.

    Method: TargetSubject.new

    Args:
        actual: ([`Target`]) the target to check against.
        meta: ([`ExpectMeta`]) metadata about the call chain.

    Returns:
        [`TargetSubject`] object
    """
    if actual == None:
        return subjects_str_factory(actual = actual, meta = meta)
    self = struct(actual = actual, meta = meta)
    public = struct(
        equals = lambda *a, **k: _target_label_equals(self, *a, **k),
    )
    return public

def _target_label_equals(self, other):
    """Asserts the target's label is equal to `other`.

    Method: TargetSubject.equals

    Args:
        self: implicitly added.
        other: ([`Label`] | [`str`]) the expected value. If a `str` is passed, it
            will be converted to a `Label` using the `Label` function.
    """
    if "label" not in dir(self.actual):
        self.meta.add_failure("Unexpected type {}".format(type(self.actual)), "")
    return _label_subject_equals(struct(actual = self.actual.label, meta = self.meta), other)

def nested_struct_factory(actual, *, meta, attrs):
    """Creates a `StructSubject`, which is a thin wrapper around a [`struct`].

    This is a customized `StructSubject` specific for java_info of IDE_JAVA in blaze aspect test. It's a complicated struct which contains
    nested struct, so we provide a factory to do that.

    Args:
        actual: ([`struct`]) the struct to wrap.
        meta: ([`ExpectMeta`]) object of call context information.
        attrs: ([`dict`] of [`str`] to [`callable`]) the functions to convert
            attributes to subjects. The keys are attribute names that must
            exist on `actual`. The values are functions with the signature
            `def factory(value, *, meta)`, where `value` is the actual attribute
            value of the struct, and `meta` is an [`ExpectMeta`] object.

    Returns:
        [`StructSubject`].
    """
    if actual == None:
        return subjects_str_factory(actual = actual, meta = meta)
    _struct_subject = StructSubject.new(
        actual,
        meta = meta,
        attrs = attrs,
    )
    self = struct(
        actual = _struct_subject,
        meta = meta,
        attrs = attrs,
    )
    return struct(
        acutal = _struct_subject,
        contains_exactly = lambda *a, **k: _struct_contains_exactly(self, *a, **k),
    )

def _struct_contains_exactly(self, expected):
    if len(dir(expected)) != len(self.attrs):
        self.meta.add_failure("The attrs of actual and expected are not the same. Actual: {}. Expected: {}".format(self.actual, expected), "")

    for name in self.attrs.keys():
        if not hasattr(expected, name):
            self.meta.add_failure("Expected does not have attribute: {}. Expected: {}, Actual: {}".format(name, expected, self.actual), "")
        else:
            actual_struct = getattr(self.actual, name)()
            expected_struct = getattr(expected, name)
            actual_struct.contains_exactly(expected_struct)

def _struct_equal(actual, meta, attrs, expected):
    """ This is a copy of _struct_contains_exactly to avoid function called recursively warning."""
    if len(dir(expected)) != len(attrs):
        meta.add_failure("The attrs of actual and expected are not the same. Actual: {}. Expected: {}".format(actual, expected))

    for name in attrs.keys():
        if not hasattr(expected, name):
            meta.add_failure("Expected does not have attribute: {}. Expected: {}, Actual: {}".format(name, expected, actual), "")
        else:
            actual_struct = getattr(actual, name)()
            expected_struct = getattr(expected, name)
            actual_struct.contains_exactly(expected_struct)

def collection_struct_contains_exactly(self, expecteds):
    """Check that a collection contains exactly the given elements.

    * It handles the comparison of struct in collection compared to CollectionSubject.contains_exactly provided by rule_testing
    * The collection must contain all the values, no more or less. The None field should be passed as (attr_name: "")

    Args:
        self: implicitly added.
        expecteds: ([`list`]) values that must exist.

    Returns:
        [`Ordered`] (see `_ordered_incorrectly_new`).
    """
    if len(self.actual) != len(expecteds):
        self.meta.add_failure(msg = "The size of actual and expected are not the same. Actual: {}. Expected: {}".format(self.actual, expecteds))

    for i in range(len(self.actual)):
        actual = self.actual[i]
        expected = expecteds[i]
        _struct_equal(actual = actual, meta = self.meta, attrs = self.attrs, expected = expected)

def subjects_depset_exactly_factory(actual, *, meta):
    """A customized version of `DepsetFileSubject.new`

    We change the behavior of contains_exactly.
    It expects the actual and expected value to be exactly the same (include count).

    Args:
        actual: ([`depset`] of [`File`]) the values to assert on.
        meta: ([`ExpectMeta`]) of call chain information.

    Returns:
        [`Struct`] object.
    """

    return struct(
        actual = actual,
        contains_exactly = lambda *a, **k: _collection_contains_exactly(struct(
            actual = actual,
            meta = meta,
        ), *a, **k),
    )

def subjects_depset_factory(actual, *, meta):
    """A customized version of `DepsetFileSubject.new`

    We change the behavior of contains_exactly.
    It may not check about the length of actual file list as we may not know.

    Args:
        actual: ([`depset`] of [`File`]) the values to assert on.
        meta: ([`ExpectMeta`]) of call chain information.

    Returns:
        [`Struct`] object.
    """

    return struct(
        actual = actual,
        contains_exactly = lambda *a, **k: _collection_contains_exactly(struct(
            actual = actual,
            meta = meta,
        ), use_predicates = True, *a, **k),
    )

def _collection_contains_exactly(self, expecteds, use_predicates = False):
    actual = self.actual
    if type(actual) == "depset":
        actual = actual.to_list()

    # either two list should be exactly the same or predicates is used
    # and predicates is provided in expecteds
    if len(actual) != len(expecteds) and not (use_predicates and len(expecteds) == 1):
        self.meta.add_failure("The size of actual and expected are not the same. Actual: {}. Expected: {}".format(self.actual, expecteds), "")
        return

    for i in range(len(actual)):
        file = actual[i]
        if use_predicates and len(expecteds) == 1:
            expected = expecteds[0]
        else:
            expected = expecteds[i]
        if type(file) == "File":
            _file_subject_short_path_equals_or_end_with(struct(file = file, meta = self.meta), expected)
        elif type(file) == "string":
            _str_subject_equals(struct(actual = file, meta = self.meta), expected)

def subjects_file_factory(actual, *, meta):
    """Creates a FileSubject asserting against the given file.

    This is customized version of _file_subject_new in testing rules. We need to update the compare function to handle the cases that
     some values of attribute are None or empty.

    Args:
        actual: ([`File`]) the file to assert against.
        meta: ([`ExpectMeta`])

    Returns:
        [`FileSubject`] object.
    """
    return struct(
        actual = actual,
        contains_exactly = lambda *a, **k: _file_subject_short_path_equals_or_end_with(struct(file = actual, meta = meta), *a, **k),
    )

def _file_subject_short_path_equals_or_end_with(self, path):
    """Asserts the file's short path is equal to/ end with the given path.

    This is a customized version of FileSubject.short_path_equals. It will check a exact the same or end with case.

    Args:
        self: implicitly added.
        path: ([`str`]) the value the file's `short_path` equal to or end with (if start with *)
    """
    path = self.meta.format_str(path)
    if self.file == None:
        if path:
            self.meta.add_failure(
                "expected: {}".format(path),
                "actual: None",
            )
            return
        else:
            return
    elif path == self.file.short_path:
        return
    if "*" in path:
        index = path.index("*")
        prefix = path[0:index]
        suffix = path[index + 1:]
        if self.file.short_path.startswith(prefix) and self.file.short_path.endswith(suffix):
            return
    self.meta.add_failure(
        "expected: {}".format(path),
        "actual: {}".format(self.file.short_path),
    )

def subjects_collection_predicate_factory(actual, *, meta):
    """Creates a "CollectionSubject" struct. Use predicate to check the actual content. All items must match with that predicate.

    Args:
        actual: ([`collection`]) the values to assert against.
        meta: ([`ExpectMeta`]) the metadata about the call chain.

    Returns:
        [`CollectionSubject`].
    """
    self = struct(actual = actual, meta = meta)
    return struct(
        actual = actual,
        contains_exactly = lambda *a, **k: _collection_contains_exactly(self, use_predicates = True, *a, **k),
    )

def subjects_str_factory(actual, *, meta):
    """Creates a new `StrSubject` for asserting `string` objects.

    Args:
        actual: ([`string`]) the string to check against.
        meta: ([`ExpectMeta`]) the metadata about the call chain.

    Returns:
        [`StrSubject`].
    """
    self = struct(actual = actual, meta = meta)
    return struct(
        actual = actual,
        contains_exactly = lambda *a, **k: _str_subject_equals(self, *a, **k),
    )

def _str_subject_equals(self, other):
    """Asserts that the subject string equals the other string.

    It allows to use predicates to compare items.

    Method: StrSubject.equals

    Args:
        self: implicitly added.
        other: ([`str`]) the expected value it should equal.
   """
    if self.actual == other:
        return
    if other and "*" in other:
        index = other.index("*")
        prefix = other[0:index]
        suffix = other[index + 1:]
        if self.actual.startswith(prefix) and self.actual.endswith(suffix):
            return
    self.meta.add_failure(
        "expected: {}".format(other),
        "actual: {}".format(self.actual),
    )
