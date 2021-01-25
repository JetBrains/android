val parentVar1 = true
val parentProperty1 by extra("hello")

android {
  val androidVar = false
  buildTypes {
    create("foo") {
      isDebuggable = false
    }
    // this is a comment
  }

  val androidProperty by extra("foo")
}
