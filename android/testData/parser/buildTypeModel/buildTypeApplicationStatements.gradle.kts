android {
  buildTypes {
    create("xyz") {
    }
  }
}
android.buildTypes.getByName("xyz").applicationIdSuffix("mySuffix")
android.buildTypes.getByName("xyz").buildConfigField("abcd", "efgh", "ijkl")
android.buildTypes.getByName("xyz").consumerProguardFiles("proguard-android.txt", "proguard-rules.pro")
android.buildTypes.getByName("xyz").debuggable(true)
android.buildTypes.getByName("xyz").embedMicroApp(true)
android.buildTypes.getByName("xyz").jniDebuggable(true)
android.buildTypes.getByName("xyz").manifestPlaceholders = mapOf("activityLabel1" to "defaultName1", "activityLabel2" to "defaultName2")
android.buildTypes.getByName("xyz").minifyEnabled(true)
android.buildTypes.getByName("xyz").multiDexEnabled(true)
android.buildTypes.getByName("xyz").proguardFiles("proguard-android.txt", "proguard-rules.pro")
android.buildTypes.getByName("xyz").pseudoLocalesEnabled(true)
android.buildTypes.getByName("xyz").renderscriptDebuggable(true)
android.buildTypes.getByName("xyz").renderscriptOptimLevel(1)
android.buildTypes.getByName("xyz").resValue("mnop", "qrst", "uvwx")
android.buildTypes.getByName("xyz").shrinkResources(true)
android.buildTypes.getByName("xyz").testCoverageEnabled(true)
android.buildTypes.getByName("xyz").useJack(true)
android.buildTypes.getByName("xyz").versionNameSuffix("abc")
android.buildTypes.getByName("xyz").zipAlignEnabled(true)
