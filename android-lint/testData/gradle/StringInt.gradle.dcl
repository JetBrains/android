androidApplication {
    <error descr="Use an integer rather than a string here (replace \"19\" with just 19)">compileSdkVersion("19")</error>
    buildToolsVersion("19.0.1")
    defaultConfig {
        <error descr="Use an integer rather than a string here (replace \"8\" with just 8)">minSdkVersion("8")</error>
        <error descr="Use an integer rather than a string here (replace \"16\" with just 16)">targetSdkVersion("16")</error>
    }
}
