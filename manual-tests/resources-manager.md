# Test specs for the Resource Manager.

## Import resources

### Drag and Drop

**Steps**
1. Create a new project, wait for the gradle sync to finish.
2. Open the external file explorer to the "res/resources-manager" directory.
3. In Android Studio, open the "Resources" tool window.
4. Drag and drop the whole directory onto the tool window.
   - A dialog name "Import drawables" should appear
   - In the header the label "10 resources ready to be imported should appear"
   -  In the dialog, 3 section should appear:
      + icon_category_food_raster (5 items)
      + icon_category_food (1 item)
      + icon_category_entertainment (4 items)

   - In the section "icon_category_food_raster, each item should be populated
    with one row of 2 Comboboxes, the first one (Qualifier Type) showing "Density" and the second
    one (Value) being one the following value and different for each item:

      + XX-High Density
      + X-High Density
      + XXX-High Density
      + High Density
      + Medium Density

      ![food]

   - In the section "icon_category_entertainment" 
      + One of the item should be named icon_categoy_entertainement.xml (XML is
        the important part)

5. For the item name "icon_category_entertainment.png", change the Qualifier Type to "Locale",
   and the values to "Afar" and "ET"

   ![entertainment]

6. Click Import.
   - The new resources should appear as shown in the picture below.
   ![checkimport]
7. In the android project tool window, under app/res/drawable, the exact following hierarchy should be visible
  ![import_hierachy]

[food]: res/resources-manager/screenshots/import_dialog_raster_food.png
[entertainment]: res/resources-manager/screenshots/category_entertainment.png
[checkimport]: res/resources-manager/screenshots/check_result_dnd_import.png
[import_hierachy]: res/resources-manager/screenshots/import_hierarchy.png
n
## Use resources

### Open a drawable
   
   1. Reuse the project from the step above with the imported images.
       - (If the step above failed, just ensure that you have 2 png files
        named icon_category_entertainment.png), with one in the 
        res/drawable directory and the other in res/drawable-xhdpi)
   
   2. Open the "Resource Manager" tool window.
   3. Click on the "Drawable" tab.
       - A drawable named icon_category_entertainment should be showing like in
   
       ![checkimport]
   
   4. Double click on it.
      -  This following view should appear:
   
       ![drawable_tab_l2]
   
   5. Double click on "default".
      - A new editor with the image "icon_category_entertainment.xml" should open.
   6. Click the left arrow  "<--".
      - The view from step 3 should appear.

### Open a layout

1. Create a new project wih an empty activity OR reuse the one created above.
2. Open the "Resource Manager" tool window.
3. Click on the "Layout" tab.
    - A layout named activity_main should be showing

    ![layout_tab]

4. Double click on it.
   - A new editor with the file "activity_main.xml" should open.

5. Click the left arrow  "<--".
   - The view from step 3 should appear.
   
   
### Open in Resource Manager

  1. From the project tree, right click on the activity_main.xml file
  2. Check that "Show In Resource Manager" option appears and click 
  on it
   ![open_in]

  3. The resource manager should open with the activity_main file selected
(The layout tab might not show as selected but this is a known issue)
   ![layout_tab]

### Create an Image View

1. Create a new project wih an empty activity OR reuse the one created above.
2. Open the "activity_main.xml" file, with the "Design" tab selected (if not already open).
3. Open the "Resources" tool window.
4. Ensure the "Drawable" tab is selected.
5. Drag "ic_launcher_background" from the "Resources" tool window onto the Layout Editor.
   -  A new image view displaying "ic_launcher_background" should be created.
6. On the layout editor, switch to the "Text" tab to display the XML editor.
7. On the "Resource" window, right click on ic_launcher_foreground.
8. Select _Copy_ .
9. On the XML editor, move the cursor on the ImageView.
10. Right click, and click paste.
  - the _src_ (or _scrCompat_) attribute should have been changed to  _"@drawable/ic_launcher_foreground"_

[layout_tab]: res/resources-manager/screenshots/layout_tab.png
[drawable_tab_l2]: res/resources-manager/screenshots/drawable_tab_l2.png
[open_in]: res/resources-manager/screenshots/open_in_res_manag.png
