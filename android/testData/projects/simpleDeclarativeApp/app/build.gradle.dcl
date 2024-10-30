androidApp {
    compileSdk = 34
    namespace = "org.gradle.experimental.android.app"
    defaultConfig {
      minSdk = 34
    }
    dependenciesDcl {
        api("com.google.guava:guava:19.0")
        api("com.android.support.constraint:constraint-layout:1.0.2")
        implementation("org.jetbrains.kotlin:kotlin-stdlib:1.9.22")
        testImplementation("junit:junit:4.12")
        androidTestImplementation("androidx.test:runner:1.4.0-alpha06")
        androidTestImplementation("androidx.test:rules:1.4.0-alpha06")
        androidTestImplementation("androidx.test.ext:junit:1.1.2")
        androidTestImplementation("org.jetbrains.kotlin:kotlin-stdlib:1.9.22")
    }
}