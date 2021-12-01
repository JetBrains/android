allprojects {
  val libs = rootProject.project.libs
  repositories {
    maven {
      url = uri("dexguard/Dexguard-${libs.versions.dexguard.runtime.get()}/lib")
    }
  }
}