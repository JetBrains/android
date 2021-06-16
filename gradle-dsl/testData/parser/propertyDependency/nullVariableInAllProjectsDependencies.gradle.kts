val dep = null
if (true) {
  dep = "hello:kotlin:2.0"
}

allprojects {
  if (!plugins.hasPlugin("java")) return@allprojects
  dependencies {
    compile(dep)
  }
}
