## Layout Property Panel Test

+ First, create a new empty project with appcompat
+ Next, open activity_main.xml in design mode
+ Next, wait for the design to display
+ Next, set the id of the TextView to "textView1"
+ Next, drop a Button from the palette on to the design, set the id of the button to "button1"

### Font family editor
1. Select the TextView in the component tree
1. Find the textSize attribute and specify "36sp"
1. Find the fontFamily attribute in the common attributes section
1. Select from the android fonts: "sans-serif-black", "cursive", "sans-serif" and verify font change
1. Select "More fonts..." from dropdown
  1. In the dialog find "Eagle Lake"
  1. Select "Create Downloadable font"
  1. Click OK
1. Verify that:
  * the font in the designer changes to the selected font
  * the current value in the drop down says "@font/eagle_lake"
  * the current value is in the list of known project fonts by opening the dropdown
  * the "pill" changes to solid black (indicating a resource reference is chosen)
  * the manifest file was updated to include a meta-data section for preloaded fonts
  * the font resource folder now has a eagle_lake.xml file
  * the values folder now has 2 added files: font_certs.xml and preloaded_fonts.xml
  * the attribute "fontFamily" now is shown in "declared attributes"
  * the project can compile
1. Select "More fonts..." from dropdown again
  1. In the dialog find "sarina"
  1. Select "Add font to project"
  1. Click OK
1. Verify that:
  * the font in the designer changed to the selected font
  * the current value in the drop down says "@font/sarina"
  * the current value is in the list of known project fonts by opening the dropdown after "eagle_lake"
  * the font resource folder now has a sarina.ttf file
  * the project can compile
1. Type in the fontFamily text field: "cursive"
1. Verify that:
  * the font in the designer changed to the selected font
  * the selected value in the drop down list is now "cursive"

### Editing id of a View
1. Select the button in the component tree
1. Search for "constraintBottom_toBottomOf" and select "@id/textView1"
1. Select the TextView in the component tree
1. Verify that the id has no resource selector button (it should appear in 3 sections)
1. Select the id and change the value to "caption" and press `<`enter`>`
1. Verify that a dialog appears with choices: "No", "Preview", "Cancel", "Yes"
1. Verify that choosing "Cancel" does nothing, and "Yes" causes the id to change
1. Verify that the "constraintBottom_toBottomOf" of the Button is now: "@id/caption"

### Accessibility: Navigate using the tab key
1. Select the TextView in the component tree
1. Click on the search icon in the header of the attributes panel
1. Press the TAB key repeatedly
1. Verify that after ignoring controls in the header
1. Each editor in the property inspector is visited
1. Each control of an editor is visited i.e.
* the left color swatch for a color control
* the editor of a field that can be edited
* the resource "pill"
1. The control will scroll into view if it is required to see it.
1. Press shift-TAB key repeatedly
1. Verify that each of the above controls are visited backwards and are being scrolled into view.

### Accessibility: Navigate using the up/down arrow keys in a table
It should be possible to navigate to and edit attributes without using the mouse.

1. Select the TextView in the component tree
1. Click on the name of the first attribute in the "all attributes" section
1. Notice the first row in the "declared" table is selected but the editor doesn't have focus
1. Use down arrow to navigate to the row with the "alpha" attribute
  1. Type: "0.8" (without the quotes) and `<`enter`>`
  1. Verify that alpha is now present in "declared attributes"
  1. Type `<`esc`>` to exit into table navigation mode
1. Use down arrow to navigate to the row with the "autoLink" flag attribute
  1. Type `<`enter`>` or `<`space`>` - this should expand the flag attribute such that all flags are shown
  1. Type `<`enter`>` or `<`space`>` again - this should open the flag popup editor.
  1. Type `<`esc`>` to close the popup editor
  1. Type `<`esc`>` again to exit into table navigation mode
  1. Use down arrow to navigate to "email"
  1. Type `<`space`>` to select true for the email flag.
  1. Verify that the autoLink value is updated to "email" and autoLink is now present in "declared attributes"
1. Use down arrow to navigate to the row with the "background" color attribute
  1. Type `<`enter`>` - this should select the color picker icon
  1. Type `<`enter`>` again - this should open the color popup picker
  1. Type `<`esc`>` to close the popup editor
  1. Verify that selection stays on the background attribute
  1. Type `<`enter`>` again - this should select the color picker icon
  1. Type `<`tab`>` twice - this should select the resource picker icon
  1. Type `<`enter`>` - this should open the resource picker dialog
  1. Verify that both drawable and color resources are available in the dialog
  1. Type `<`esc`>` to close the resource picker dialog
  1. Type `<`esc`>` again to exit into table navigation mode
1. Use down arrow to navigate to the row with the "drawableBottom" attribute
  1. Type `<`enter`>` - this should select the drawable picker icon
  1. Type `<`enter`>` again - this should open the resource picker dialog
  1. Verify that both drawable and color resources are available in the dialog
  1. Type `<`esc`>` to close the resource picker dialog
  1. Type `<`esc`>` again to exit into table navigation mode
1. Use down arrow to navigate to the row with the "drawableTintMode" attribute
  1. Type `<`alt`>` down arrow to open the drop down
  1. Use arrow buttons to select "src_over"
  1. Verify "drawableTintMode" is now present in "declared attributes"

### Flag editor scrolls if there are too many items
1. Select the TextView in the component tree
1. Change the screen resolution to 1024x640 or lower.
1. Find inputType property in "All Attributes"
1. Click on the flag icon
1. Verify that there is a vertical scrollbar in the flags popup
