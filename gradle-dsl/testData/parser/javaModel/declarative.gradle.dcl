javaApplication {
  javaVersion = 17
  mainClass = "com.example.App"

  dependencies {
    implementation("com.google.guava:guava:32.1.3-jre")
    implementation(project(":java-util"))
    implementation("com.google.guava:guava:32.1.3-jre")
  }

  testing {
    // test on 21
    testJavaVersion = 21

    dependencies {
      implementation("org.junit.jupiter:junit-jupiter:5.10.2")
    }
  }
}