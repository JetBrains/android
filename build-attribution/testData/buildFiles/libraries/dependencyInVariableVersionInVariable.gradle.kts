val kotlin_version by extra("1.5.31")
val kotlin_stdlib by extra("org.jetbrains.kotlin:kotlin-stdlib:${rootProject.extra["kotlin_version"]}")
dependencies {
    implementation(rootProject.extra["kotlin_stdlib"])
}
