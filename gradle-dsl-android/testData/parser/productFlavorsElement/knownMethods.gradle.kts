android {
  productFlavors.add("foo")
  productFlavors.findAll { it.name.size > 0 }
  productFlavors.forEach { }
  productFlavors.remove("foo")
  productFlavors.removeIf { it.name.size > 0 }
  productFlavors.withType { }
  productFlavors.whenObjectAdded { }
  productFlavors.whenObjectRemoved { }
}
