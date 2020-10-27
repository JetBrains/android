# `PreviewElement` definitions

### Overview
The `PreviewElement` interface represents a `@Preview` annotation in the users source code.
The `AnnotationFilePreviewElementFinder` is the class in charge of parsing the Kotlin file
and returning the list of `PreviewElement`s

A `PreviewElementInstance` is a single `PreviewElement` meant to be rendered by the preview.
As such, `PreviewElementInstance`s can be serialized to XML.

### `PreviewElement`
A `PreviewElement` contains all the data required for displaying UI elements like the header
and the configuration of the element. It can also contain a group name used for filtering.

### Templates
`PreviewElementTemplate` is a `PreviewElement` meant to generate `PreviewElementInstance`s.
Templates are not meant to be rendered by the preview so they can not be directly serialized
to XML. The `PreviewElementTemplate#instances` method must be called to obtain the instances.
Note that this method CAN NOT run on the UI thread since the generation of the instances has
the potential of being slow.