## Material2 : Palette & Property Inspector

First, create a new empty project with "API 28" or "API P" as the min SDK level.
Next, open "activity_main.xml" in design

Next, choosing any of the following M2 components from the palette, should offer to add the material library to the project (hit cancel every time):
 - TextInputLayout
 - ChipGroup
 - Chip
 - FloatingActionButton
 - AppBarLayout
 - BottomAppBar
 - NavigationView
 - BottomNavigationView
 - TabLayout
 - TabItem
Next, make sure all the above components is on the palette and has an icon

Next, Add one of the above components to activity_main.xml
Next, change the AppTheme to "Theme.MaterialComponents" in values/styles.xml
 - make sure the library was added and a sync was initiated (the user has to accept)
 - make sure there are properties in the inspector for each component

Next, drop a "Button" on activity_main.xml
 - make sure the button has extra properties including "cornerRadius"

## Second test scenario ##

First, create a new empty project with "API 28" or "API P" as the min SDK level.
Next, open "activity_main.xml" in design
Next, In project structure dialog: Add a dependency on "com.android.support:design"

Next, choosing any of the following components from the palette, should NOT offer to add the material library to the project:
 - TextInputLayout
 - FloatingActionButton
 - AppBarLayout
 - NavigationView
 - TabLayout
 - TabItem

None of the following components should be available on the palette:
 - ChipGroup
 - Chip
 - BottomAppBar
 - BottomNavigationView
