android {
  productFlavors {
    getByName("flavor1") {
      setApplicationId("com.example.myapplication.flavor1")
      isDefault = false
    }
    getByName("flavor2") {
      setApplicationId("com.example.myapplication.flavor2")
      isDefault = true
    }
  }
}
