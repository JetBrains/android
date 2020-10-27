plugins {
  id("com.android.application")
  kotlin("android")
}

val `a b c` by extra(1)

dependencies {
  val `**` by extra(2)
}

android {
  compileSdkVersion(28)
}
