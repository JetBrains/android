package com.example.composemultipreviewsample

import androidx.compose.ui.tooling.preview.Preview

@Preview(fontScale = 0.5f, showBackground = true)
@Preview(fontScale = 1f, showBackground = true)
@Preview(fontScale = 1.5f, showBackground = true)
annotation class FontScales(){}

@Preview(backgroundColor = 0xFF00FFFF, showBackground = true)
@Preview(backgroundColor = 0xFF00FF00, showBackground = true)
@Preview(backgroundColor = 0xFFFF00FF, showBackground = true)
annotation class BackgroundColors(){}

@FontScales
@BackgroundColors
annotation class FontsAndColors() {}

@CyclicAnnotation2
@Preview(backgroundColor = 0x00FFFF00, showBackground = true, name = "cyclic-1")
annotation class CyclicAnnotation1() {}

@CyclicAnnotation3
@Preview(backgroundColor = 0x00FFFF00, showBackground = true, name = "cyclic-2")
annotation class CyclicAnnotation2() {}

@CyclicAnnotation1
@Preview(backgroundColor = 0x00FFFF00, showBackground = true, name = "cyclic-3")
annotation class CyclicAnnotation3() {}