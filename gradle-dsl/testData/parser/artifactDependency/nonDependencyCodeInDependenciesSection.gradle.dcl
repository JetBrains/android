androidApp {
  dependenciesDcl {
    compile("com.android.support:appcompat-v7:22.1.1")
    runtime("com.google.guava:guava:18.0")
    apply("com.test.xyz")
    testCompile("org.hibernate:hibernate:3.1")
  }
}
