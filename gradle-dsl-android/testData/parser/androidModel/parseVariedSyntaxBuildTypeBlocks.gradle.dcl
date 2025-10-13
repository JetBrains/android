androidApp {
  buildTypes {
    buildType("one") { }
    `buildType`("two") { }
    buildType("""three""") { }
    `buildType`("""four""") { }
    buildType("f\u0069ve") { }
    `buildType`("\u0073i\u0078") { }
  }
}