androidApp {
  dependenciesDcl {
    implementation(enforcedPlatform("group:name:3.1415"))
    implementation(platform("androidx.compose:compose-bom:2022.10.0"))
    implementation(enforcedPlatform("org.springframework:spring-framework-bom:5.1.9.RELEASE"))
  }
}