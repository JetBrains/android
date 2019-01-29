This file explain the package architecture in `colorpicker2`

- package `com/android/tools/idea/ui/resourcechooser/colorpicker2` is planed to upstream to Intellij,
all of components in this package should NOT depend on the Studio components.

- package `com/android/tools/idea/ui/resourcechooser/colorpicker2/internal` is used to placed new color picker
components of Android Studio. This package will NOT be upstreamed to Intellij.

- After we complete the upstream process, all components in `com/android/tools/idea/ui/resourcechooser/colorpicker2`
will be removed. And `com/android/tools/idea/ui/resourcechooser/colorpicker2/internal` will be renamed as
`com/android/tools/idea/ui/resourcechooser/colorpicker`.

The purpose of this is to make the upstream smoothly. We know which files are going to upstream and which are not.
After we merged Intellij, we just need to change the import path of classes which use new Color Picker.
