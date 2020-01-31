# Sample Data UI sanity Manual Test

Simple test
----
1. Create a new basic project with an `Empty Activity`
1. Open the `res/layouts/activity_main.xml` file and go
   to the design tab
1. Click the "Hello World" TextView element
    #### Expected results
    - The wrench action target displays below the TextView
    ![Wrench Option][wrench_option]
1. Click the wrench icon
1. In the popup select an element other than `"None"`
    #### Expected results
    - The text displayed in the TextView changes from "Hello World" to something else
1. Go to the Attributes Panel and click on the three dots ... next to the tools `text` attribute (not the regular text attribute)
    ![Tools text attribute][tools_text]
    #### Expected results
    - The resource picker displays and displays a list of elements to select. The top header reads `"Sample data"`
1. Click the first element after the `"Sample data"` header (Usually `"cities"`)
    #### Expected results
    - The right side of the resource picker displays a preview with multiple cities
    ![TextView picker list][picker_text_list]



1. Drag a new RecyclerView item from the Palette
1. Click on the RecyclerView element
    #### Expected results
    - The wrench action target displays below the RecyclerView
1. Click the wrench icon
1. Click the forward and back arrows and make sure that the template in the background changes
    ![RecyclerView email state][rv_email]
1. Set the number of elements to different values and check that it affects the preview in the background
1. Click the arrows again until the template says `"Default"` and click outside of the popup and restore the number of elements to 10
1. Verify that the preview is back to the original state
    ![RecyclerView original state][rv_original_state]

[wrench_option]: res/sample-data-ui/wrench_option.png
[tools_text]: res/sample-data-ui/tools_text.png
[picker_text_list]: res/sample-data-ui/picker_text_list.png
[rv_email]: res/sample-data-ui/rv_email.png
[rv_original_state]: res/sample-data-ui/rv_original_state.png

