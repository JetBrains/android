# Android XML and DOM extensions

Code in this package hooks into IntelliJ XML and DOM mechanism to provide schema for XML files used in Android projects. Based on this
information the IDE offers code completion and can highlight invalid tags or attributes.

## DOM definitions

See the [official docs](http://www.jetbrains.org/intellij/sdk/docs/reference_guide/frameworks_and_external_apis/xml_dom_api.html) for a
general introduction. If it helps, you can think of the lower-level XML API as similar to JDBC (`manifest.findSubtags("activity")`) and
DOM as a higher level ORM-like API (`manifest.getActivities()`).

In Android we use a mixture of static and dynamic DOM definitions:
1. Some files are defined using classes and annotations, see for example [Manifest](manifest/Manifest.java) (note: to get correct
   information you should most likely use the merged manifest, but that's outside of the scope of this doc).
2. Other information is read from resources, using naming conventions to find a styleable that contains attrs relevant to a given XML tag.
   For example if we recognize a tag as corresponding to a View subclass in a layout file (e.g. "TextView"), we find the corresponding
   styleable, look at the attrs it contains and register DOM extensions for the given tag that correspond to these attr resources.
   See [AttributeProcessingUtil](AttributeProcessingUtil.java) and [SubtagsProcessingUtil](SubtagsProcessingUtil.java) for code that
   reads styleables and [AndroidDomExtender](AndroidDomExtender.java) for the extension that plugs into the DOM system.
3. Sometimes the styleable is determined statically, but the attrs are read dynamically to stay up to date with the platform version used
   in the project. This is done using the [`@Styleable` annotation](Styleable.java).

Each file format is defined by a
[DomFileDescription](../../../../../../../../idea/xml/dom-openapi/src/com/intellij/util/xml/DomFileDescription.java) subclass, which
provides a way to tell apart XML files of different types. See [TransitionDomFileDescription](transition/TransitionDomFileDescription.java)
for an example. For a case if you want to create a file format with single possible root, consider using
[AbstractSingleRootFileDescription](AbstractSingleRootFileDescription.java) instead of extending `DomFileDescription` directly.

File formats that are used by Android framework are loosely specified by documentation on developer.android.com, but this information is
quite often isn't accurate, and thus framework inflaters should be used when implementing support for new formats / fixing issues with
support for existing ones. Please make sure to add pointers to framework code, `DomFileDescription` subclasses javadoc is a good place to
store them.

See Javadoc on `AndroidDomTest` class (and check its subclasses) to get an idea how to test changes to DOM definitions.

### Misc implementation details

To disable spellchecking inside a tag's value, use the
[NoSpellchecking](../../../../../../../../idea/spellchecker/src/com/intellij/spellchecker/xml/NoSpellchecking.java) annotation. See
`XmlSpellcheckingStrategy#isSuppressedFor` for the code that implements that.

### res/layout DOM

> **TODO:** Understand this in detail  
> **TODO:** Move this to descriptor? Or use `@CustomChildren`?

### res/xml DOM

The res/xml directory can contain different kinds of files, e.g.
[description of preferences](https://developer.android.com/guide/topics/ui/settings/) or
[paths](https://developer.android.com/reference/android/support/v4/content/FileProvider#SpecifyFiles) for a `FileProvider`. We handle them
all using one DOM type, [XmlResourceElement](xml/XmlResourceElement.java) and
[XmlResourceDomFileDescription](xml/XmlResourceDomFileDescription.java). The latter overrides `acceptsOtherRootTagNames` and because is the
only [AndroidResourceDomFileDescription](AndroidResourceDomFileDescription.java) for the xml folder type, it's always picked.

Everything is handled dynamically even if some cases are just hardcoded, see e.g. `SubtagsProcessingUtil#registerXmlResourcesSubtags`.

> **TODO:** Is there a reason for not breaking this down into multiple DOM definitions, some of them fully static?

## Error highlighting

Errors in XML files can come from two sources: lint checkers or code in this package. Here we only describe the latter. The general rule is
that we prefer writing lint checks when possible, since they can be run by the build system on CI servers etc. Inspections in this package
are an exception, since they mostly simulate build-time errors reported by aapt (another tool run by the build system).

Following the official DOM docs, we provide [AndroidDomInspection](inspections/AndroidDomInspection.java) to check for unresolved
references and other errors reported by [Converters](../../../../../../../../idea/xml/dom-openapi/src/com/intellij/util/xml/Converter.java).

By default, providing a DOM description applicable to a given file is enough to make
[XmlHighlightVisitor](../../../../../../../../idea/xml/xml-analysis-impl/src/com/intellij/codeInsight/daemon/impl/analysis/XmlHighlightVisitor.java)
highlight unrecognized sub-tags and attributes. This works, because IntelliJ comes with
[DomDescriptorProvider](../../../../../../../../idea/xml/dom-impl/src/com/intellij/util/xml/impl/DomDescriptorProvider.java), which provides
[DomElementXmlDescriptor](../../../../../../../../idea/xml/dom-impl/src/com/intellij/xml/impl/dom/DomElementXmlDescriptor.java) instances
which in turn return null when asked about unknown sub-tags or attributes.

This behavior is too aggressive for most Android-specific files, since inflaters that consume these files either just ignore attributes they
don't recognize or pass inside `AttributeSet` objects to custom views which in turn ignore them. To work around this, we implement our own
[AndroidDomElementDescriptorProvider](AndroidDomElementDescriptorProvider.java). Android tag descriptors return
[AndroidAnyTagDescriptor](AndroidAnyTagDescriptor.java) and [AndroidAnyAttributeDescriptor](AndroidAnyAttributeDescriptor.java) when asked
about unknown attributes and sub-tags, essentially telling the XML layer that every attribute and sub-tag is valid. Instead of relying on
the default highlighter, we provide [AndroidUnknownAttributeInspection](inspections/AndroidUnknownAttributeInspection.java) and
[AndroidElementNotAllowedInspection](inspections/AndroidElementNotAllowedInspection.java) that detect cases we care about.

We also have an annotator, which creates gutter icons on lines that reference a drawable or a color.

We opt-out of the usual IntelliJ mechanisms for validating XML files against schema definitions, by resolving all namespaces to a dummy
XSD file in `AndroidXmlSchemaProvider`.

> **TODO:** AndroidUnknownAttributeInspection ignores non-framework attributes
> **TODO:** Can we replace `AndroidMissingOnClickHandlerInspection` with lint checks?

## References

Android-specific references in XML files can originate from:
* `AndroidXmlExtension` if it's the tag name and it doesn't contain a dot. It creates an instance of `AndroidClassTagNameReference` which
handles renaming or moving the class.
* `AndroidXmlReferenceProvider` if it's the tag name and it contains a dot and we know the super class (e.g. `View` or `Preference`). In
this case we create multiple references, one for every package segment and one for the class itself.
* DOM converters, e.g. `ResourceReferenceConverter` for tag values in `res/values` and attribute values in layouts etc.

The DOM layer creates references in two ways. The simple case is a `Converter` implementing `ResolvingConverter`, in which case
`GenericValueReferenceProvider#doCreateReferences` will create a single reference in the corresponding PSI element. The reference delegates
all the work back to the converter, calling `getVariants` and `resolve`. A converter may also get more control over reference creation and
implement `CustomReferencesConverter`, e.g. to create more than one reference in the string. In this case the logic for resolution and
code completion lives in the references. Some of our converters implement both interfaces, which is rather confusing. In this case
`GenericValueReferenceProvider` will create the generic reference only when `createReferences` from `CustomReferencesConverter` returns no
references. This makes it hard to understand which `getVariants` method (from the converter or the reference) will be used.

> **TODO:** Audit our converters and make them not implement both interfaces.

### Resource references

Resource references are handled by `ResourceReferenceConverter`. It uses a strange mixture of `ResolvingConverter` and
`CustomReferenceConverter` which means methods like `getVariants` are both in the converter itself and in `AndroidResourceReference` and
depending on circumstances one or both are called. Methods in the converter are called by `GenericDomValueReference` instances which get
created for every value with a `ResolvingConverter`.

> **TODO:** Remove `AndroidResourceReference` and handle everything in the converter.  
> **TODO:** `AndroidXmlExtension` should override the tag name extension only in layout, preferences etc., not all files.  
> **TODO:** `AndroidXmlExtension` should re-use detection logic from the DOM layer.  
> **TODO:** AndroidClassTagNameReference should rewrite to `<view class="...">` if the new name is not valid XML. This is already
implemented in `XmlTagInnerClassInsertHandler`.  
> **TODO:** Insert references in XML attributes to corresponding attr resources.

## Code completion

Basic code completion functionality comes from our DOM definitions which are turned in XML descriptors which are used by the standard XML
completion machinery. We augment it in a few different ways:

* `AndroidLayoutXmlTagNameProvider` is used in layout files. It creates better `LookupElement` instances and relies on these instances being
equal to the default ones (created by `DefaultXmlTagNameProvider`) to replace them in the final set of completion results.
* Some of the references mentioned above implement `getVariants`
* There's a custom `AndroidXmlCompletionContributor`.

> **TODO:** AndroidLayoutXmlTagNameProvider setting a different insert handler makes the LookupElements not equal, so both appear in
completion, one with the wrong insert handler.  
> **TODO:** Add more lookup strings in other file types, e.g. preferences.  
> **TODO:** Class names in tags are provided by DOM, `getVariants` in `AndroidXmlReferenceProvider.MyClassOrPackageReference`,
        `AndroidXmlCompletionContributor` and `AndroidLayoutXmlTagNameProvider`.  
> **TODO:** Remove the "namespace prefix" completion from `AndroidXmlCompletionContributor`, since all attributes are suggested anyway.  
> **TODO:** Can we make `AndroidXmlCompletionContributor` not specialized to only work on layouts?

When a new tag is inserted, `XmlTagInsertHandler` uses information from `AndroidXmlTagDescriptor` to add required attributes and subtags
to the inserted template (this can be turned off in settings, but it enabled by default). `AndroidXmlTagDescriptor` is an adapter around
`DomElementXmlDescriptor`, so the easiest way to mark an attribute as required is with the `@Required` annotation. `getContentType` is
called on the descriptor to determine if the new tag should be closed or not, depending on whether we expect the tag to have children.

> **TODO:** handle more cases in `getContentType`, e.g. manifest, preference groups. Base this on static DOM information?

## Documentation providers

While editing Android XML files, users can use the "quick documentation" feature to see details about the referenced resources (including
attr resources "referenced" by using XML attributes with matching names). The documentation HTML comes from
`AndroidXmlDocumentationProvider`.

> **TODO:** Documentation on attribute code lookup items is broken.  
> **TODO:** Documentation on class name lookup items is broken for short names.

## Structure view

Because our files come with DOM definitions, they are by default handled by `DomStructureViewBuilderProvider`. Unfortunately this view
ignores `GenericDomValue`, which means it doesn't work very well on most files. Because of that we provide hand-written structure views
for layouts and `res/values` files.

Layout structure view shows icons chosen by `AndroidDomElementDescriptorProvider`.

> **TODO:** AndroidDomElementDescriptorProvider potentially loading icons in UI thread. Where else is this used?  
> **TODO:** Layout structure view doesn't show the root layout name  
> **TODO:** Values structure view should show attrs within styles  
> **TODO:** For other files, the default XML structure view is probably better than the broken DOM one.

## Code style settings

We have a set of classes to provide the "standard Android" formatting of XML files. The settings themselves are stored in
`AndroidXmlCodeStyleSettings` and modified through UI in `AndroidXmlCodeStylePanel`. The most important setting is `USE_CUSTOM_SETTINGS`
which controls whether Android files should get special treatment. It is checked by `AndroidXmlFormattingModelBuilder` when an XML file
is reformatted.

We provide a "predefined style" for XML that enables `USE_CUSTOM_SETTINGS` and a notification panel (in
`AndroidCodeStyleNotificationProvider`) that suggests it is applied.

## GotoDeclaration handling

`XmlAttributeValue` and `XmlTag` goto declaration are not handled by any GotoDeclarationHandler. Instead the references are resolved at the
caret location.

`XmlAttributeName` goto declaration support is provided by `XmlAttributeNameGotoDeclarationHandler` for resource files. The attr resource is
retrieved from the ResourceRepository and wrapped in a `LazyValueResourceElementWrapper` to delay DOM traversal until the user wants to
navigate to the resource declaration.

## Other editor features

`ResourceFoldingBuilder` uses the code folding feature to "collapse" resource references (e.g. `@string/app_name`) and display the
referenced string, integer or dimen instead. This is similar to how Java anonymous classes are displayed using lambda syntax in recent
versions of IntelliJ.

`AndroidXmlExtension` implements support for `aapt:attr`, where attributes can be replaced by special sub-tags. See [Inline complex XML
resources](https://developer.android.com/guide/topics/resources/complex-xml-resources) on DAC.

> **TODO:** This seems to also be implemented in AndroidDomInspection, why do we need both?

`AndroidXmlCharFilter` changes how pressing `|` behaves during completion, to make typing flags like `android:inputType` easier. This works
in conjunction with `FlagConverter` and `AndroidCompletionContributor`.

`AndroidXmlTypedHandler` opens code completion after typing '@' in relevant XML contexts.

> **TODO:** This feature seems unfinished, pressing `|` should insert the current value and open completion again for a second value.

`AndroidLineMarkerProvider` adds gutter icons for related Java files.

> **TODO:** Use `RelatedItemLineMarkerProvider` instead of two separate classes for related files and icons.

`AndroidXmlSpellcheckingStrategy` controls which strings are checked for spelling mistakes and how they are tokenized.

> **TODO:** Remove check for `generated.xml`, these files have a different name now and are marked as generated.  
> **TODO:** It doesn't seem to handle `@NoSpellchecking` that we use in DOM definitions.

`AndroidXmlnsImplicitUsagesProvider` understands that namespace prefixes can be used by Android resource references in XML attributes and
marks referenced namespaces as used.
