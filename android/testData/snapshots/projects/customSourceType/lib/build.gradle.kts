plugins {
  id("com.android.library")
}

/**
 * This would normally be done by a plugin that also adds support
 * for compiling the new custom source type, but for this test only
 * the registration matters.
 */
androidComponents {
  registerSourceType("toml")
}

android {
  namespace = "com.example.lib"
  compileSdk = 33
}
