buildscript {
  val kotlinVersion by extra("1.2.3")
}

plugins {
  id("com.android.application") version "7.1.0" apply false
}
val foo by extra(1)
