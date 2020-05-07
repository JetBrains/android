include(":app")
rootProject.name = "My Application"

include(":libs")
project(":libs").setProjectDir(file("/tmp/libs"))
