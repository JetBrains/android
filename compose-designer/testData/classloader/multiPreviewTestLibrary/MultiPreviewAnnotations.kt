package com.example.mytestlibrary

import androidx.compose.ui.tooling.preview.Preview

@Preview(
  name = "test name",
  group = "test group",
  showBackground = true,
  backgroundColor = 0xFF00FF00,
  device = "id:pixel_5",
)
@MultiPreviewWith2Previews
annotation class MyMultiPreviewFromMyLibrary

@Preview(
  name = "preview 1",
  group = "group 1-2",
  showBackground = true,
  backgroundColor = 0xFF00FF01,
  device = "id:pixel_5",
)
@Preview(
  name = "preview 2",
  group = "group 1-2",
  showBackground = true,
  backgroundColor = 0xFF00FF02,
  device = "id:pixel_5",
)
annotation class MultiPreviewWith2Previews
