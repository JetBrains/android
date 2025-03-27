androidApp {
  buildTypes {
    buildType("dotted.buildtype") {
    }
  }

  dependenciesDcl{
    `dotted.buildtypeImplementation`("com.android.support:appcompat-v7:+")
  }
}