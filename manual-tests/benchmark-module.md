# Benchmark module template manual test

Simple Java test
----
1. Create a new basic Java project with an `Empty Activity`
1. Right-click your project or module and select New > Module.
1. Select benchmark module and click next.

    ![Benchmark module][icon]
    
    #### Expected results
    - A configure module dialog should pop up.
    - It should have the default language set to Java.
    - The minApi dropdown should not offer options below the minimum supported api level, 14.
    
1. Enter a module name, a minApi, choose Java as the language and click finish.

    #### Expected results
    - A new module is created with the correct name, min api and latest compile / target api set.
    - It should not add kotlin to the project.
    - It should include proguard rules which are enabled by default in the module's `build.gradle`.
    - It should include an AndroidManifest which turns off debuggability during android tests.
    - It should be configured to use the AndroidBenchmarkRunner when running instrumented tests.
    - It should have the androidx.benchmark plugin applied.

1. Run the sample benchmark under `androidTest` named `ExampleBenchmark.java`
    ##### Expected results
    - The sample benchmark should run without any additional configuration as an instrumented test
    and report results similar to the image below.
    
    ![Sample output][output]

Simple Kotlin test
----
1. Create a new basic Kotlin project with an `Empty Activity`
1. Right-click your project or module and select New > Module.
1. Select benchmark module and click next.

    ![Benchmark module][icon]
    
    #### Expected results
    - A configure module dialog should pop up.
    - It should have the default language set to Kotlin.
    - The minApi dropdown should not offer options below the minimum supported api level, 14.
    
1. Enter a module name, a minApi, choose Kotlin as the language and click finish.

    #### Expected results
    - A new module is created with the correct name, min api and latest compile / target api set.
    - It should include proguard rules which are enabled by default in the module's `build.gradle`.
    - It should include an AndroidManifest which turns off debuggability during android tests.
    - It should be configured to use the AndroidBenchmarkRunner when running instrumented tests.
    - It should have the androidx.benchmark plugin applied.

1. Run the sample benchmark under `androidTest` named `ExampleBenchmark.kt`
    ##### Expected results
    - The sample benchmark should run without any additional configuration as an instrumented test
    and report results similar to the image below.
    
    ![Sample output][output]

Simple Java project with Kotlin test
----
1. Create a new basic Java project with an `Empty Activity`
1. Right-click your project or module and select New > Module.
1. Select benchmark module and click next.

    ![Benchmark module][icon]
    
    #### Expected results
    - A configure module dialog should pop up.
    - It should have the default language set to Java.
    - The minApi dropdown should not offer options below the minimum supported api level, 14.
    
1. Enter a module name, a minApi, choose Kotlin as the language and click finish.

    #### Expected results
    - A new module is created with the correct name, min api and latest compile / target api set.
    - It should add kotlin to the project.
    - It should include proguard rules which are enabled by default in the module's `build.gradle`.
    - It should include an AndroidManifest which turns off debuggability during android tests.
    - It should be configured to use the AndroidBenchmarkRunner when running instrumented tests.
    - It should have the androidx.benchmark plugin applied.

1. Run the sample benchmark under `androidTest` named `ExampleBenchmark.kt`
    ##### Expected results
    - The sample benchmark should run without any additional configuration as an instrumented test
    and report results similar to the image below.
    
    ![Sample output][output]

Helpful Links
---
https://developer.android.com/studio/profile/benchmark

[icon]: res/benchmark-module/benchmark-icon.png
[output]: res/benchmark-module/benchmark-output.png
