android {
  buildTypes {
    create("xyz") {
      manifestPlaceholders = mutableMapOf("activityLabel1" to "newName1", "activityLabel2" to "newName2")
    }
  }
}
