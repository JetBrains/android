android {
  buildTypes {
    named("release").applicationIdSuffix = ".one"
    getByName("debug").applicationIdSuffix = ".two"
    create("one").applicationIdSuffix = ".three"
    register("two").applicationIdSuffix = ".four"
    maybeCreate("three").applicationIdSuffix = ".five"

    nonsense("ninety nine").applicationIdSuffix = ".oneHundredAndOne"
  }
}
