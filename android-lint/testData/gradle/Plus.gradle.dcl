androidApplication {
    compileSdkVersion(19)
    buildToolsVersion("19.0.1")
}

declarativeDependencies {
  compile(<warning descr="Avoid using + in version numbers; can lead to unpredictable and unrepeatable builds (com.android.support:appcompat-v7:+)">"com.android.support:appcompat-v7:+"</warning>)
}
