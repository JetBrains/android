# AndroidX migration Manual Test

Simple test
----
1. Create a new basic project with an `Empty Activity`
1. Open the `app/build.gradle` file
1. Click `Refactor/Migrate to AndroidX...`

    ![Menu option][menu]

    #### Expected results
    - The refactoring preview must show
    ![Do Refactor Screen][do_refactor]
    - It will list all the references to the old `android.support` artifacts, packages and classes.

1. Click `Do Refactor`
    ##### Expected results
    - Refactoring will execute. The project does not contain references to any of the old `android.support` classes.
    - Project compiles (this requires the actual androidx dependencies to be available)


[menu]: res/androidx-refactoring/menu.png
[do_refactor]: res/androidx-refactoring/do-refactor.png
