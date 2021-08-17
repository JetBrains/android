val fancy by extra(true)

android {
  dataBinding {
    isEnabled = fancy
  }
  viewBinding {
    isEnabled = fancy
  }
}
