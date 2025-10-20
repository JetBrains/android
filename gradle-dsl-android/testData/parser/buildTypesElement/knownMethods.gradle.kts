android {
  buildTypes.add("foo")
  buildTypes.findAll { it.name.size > 0 }
  buildTypes.forEach { }
  buildTypes.remove("foo")
  buildTypes.removeIf { it.name.size > 0 }
  buildTypes.withType { }
  buildTypes.whenObjectAdded { }
  buildTypes.whenObjectRemoved { }
}
