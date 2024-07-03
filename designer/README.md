
### Designers Module

This module traditionally contained all the infrastructure to support Layout Editor. As Design Tools evolved, `designers` has become
the main module containing the infrastructure for rendering and providing the framework for other tools.

No new functionality should be added into this module as it has become a big module that sits at the intersection of many other. Infrastructure
should be extracted and sit in a separate module.

Some of the contents of the module as of today:

- `./customview` Preview for custom Views. Will preview any component that inherits from `com.android.View` and show a preview next to the code.
- `./src/com/android/tools/idea/common/error`: Infrastructure for the issues panel that displays render and visual lint errors. Functionality here is used across many areas, including Compose Preview and others.
- `./src/com/android/tools/idea/rendering`: Used to contain low-level rendering framework to obtain previews of resources. This has now been extracted to a new rendering module re-used across Android Studio and screenshot testing.
The module now has a few utilities remaining and some code to handle rendering errors.
- `./src/com/android/tools/idea/uibuilder`: Mostly implementation of the Layout Editor but contains a number of framework pieces used by all other previews.
- `./src/com/android/tools/idea/ml`: Implementation of some ML related experimentation like XML -> Compose code transforms.
- `./src/com/android/tools/idea/common/scene`: Framework for rendering overlays in previews. This is used in the Layout Editor preview and Compose to render the bounding boxes or interactable components.
- `./src/com/android/tools/idea/common/editors`: Some small common infrastructure to run the notifications panel on top of text editors and shortcut setup.
It also includes the code for the `layeredimage` that is used to preview mocks on top the existing editor.
- `./src/com/android/tools/idea/common/model`: Constains the internal representation of XML files used by the Layout Editor. This is also used in other previews and the Navigation Editor as an abstrastion layer to render the previews.