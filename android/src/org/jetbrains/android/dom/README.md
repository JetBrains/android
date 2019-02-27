# Android DOM extensions

Code in this package hooks into IntelliJ DOM mechanism to provide schema for XML files used in Android projects. Based on this information
the IDE offers code completion and can highlight invalid tags or attributes.

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

See Javadoc on `AndroidDomTest` class (and check its subclasses) to get an idea how to test changes to files in this package.

## Misc implementation details

To disable spellchecking inside a tag's value, use
[NoSpellchecking](../../../../../../../../idea/spellchecker/src/com/intellij/spellchecker/xml/NoSpellchecking.java) annotation. See
`XmlSpellcheckingStrategy#isSuppressedFor` for the code that implements that.

## res/xml DOM

The res/xml directory can contain different kinds of files, e.g.
[description of preferences](https://developer.android.com/guide/topics/ui/settings/) or
[paths](https://developer.android.com/reference/android/support/v4/content/FileProvider#SpecifyFiles) for a `FileProvider`. We handle them
all using one DOM type, [XmlResourceElement](xml/XmlResourceElement.java) and
[XmlResourceDomFileDescription](xml/XmlResourceDomFileDescription.java). The latter overrides `acceptsOtherRootTagNames` and because is the
only [AndroidResourceDomFileDescription](AndroidResourceDomFileDescription.java) for the xml folder type, it's always picked.

Everything is handled dynamically even if some cases are just hardcoded, see e.g. `SubtagsProcessingUtil#registerXmlResourcesSubtags`.

*TODO:* Is there a reason for not breaking this down into multiple DOM definitions, some of them fully static?
