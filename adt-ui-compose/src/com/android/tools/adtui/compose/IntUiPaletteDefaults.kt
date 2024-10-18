/*
 * Copyright (C) 2024 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.tools.adtui.compose

/**
 * The default RGB values of IntelliJ Int UI's color palette. Used as typed fallbacks for
 * theme lookups.
 */
@Suppress("unused") // Just a reference, we don't need to actually use all these
object IntUiPaletteDefaults {

  // Derived from platform/platform-resources/src/themes/expUI/expUI_dark.theme.json
  object Dark {
    const val Gray1 = 0x1E1F22
    const val Gray2 = 0x2B2D30
    const val Gray3 = 0x393B40
    const val Gray4 = 0x43454A
    const val Gray5 = 0x4E5157
    const val Gray6 = 0x5A5D63
    const val Gray7 = 0x6F737A
    const val Gray8 = 0x868A91
    const val Gray9 = 0x9DA0A8
    const val Gray10 = 0xB4B8BF
    const val Gray11 = 0xCED0D6
    const val Gray12 = 0xDFE1E5
    const val Gray13 = 0xF0F1F2
    const val Gray14 = 0xFFFFFF

    const val Blue1 = 0x25324D
    const val Blue2 = 0x2E436E
    const val Blue3 = 0x35538F
    const val Blue4 = 0x375FAD
    const val Blue5 = 0x366ACE
    const val Blue6 = 0x3574F0
    const val Blue7 = 0x467FF2
    const val Blue8 = 0x548AF7
    const val Blue9 = 0x6B9BFA
    const val Blue10 = 0x83ACFC
    const val Blue11 = 0x99BBFF

    const val Green1 = 0x253627
    const val Green2 = 0x375239
    const val Green3 = 0x436946
    const val Green4 = 0x4E8052
    const val Green5 = 0x57965C
    const val Green6 = 0x5FAD65
    const val Green7 = 0x73BD79
    const val Green8 = 0x89CC8E
    const val Green9 = 0xA0DBA5
    const val Green10 = 0xB9EBBD
    const val Green11 = 0xD4FAD7

    const val Yellow1 = 0x3D3223
    const val Yellow2 = 0x5E4D33
    const val Yellow3 = 0x826A41
    const val Yellow4 = 0x9E814A
    const val Yellow5 = 0xBA9752
    const val Yellow6 = 0xD6AE58
    const val Yellow7 = 0xF2C55C
    const val Yellow8 = 0xF5D273
    const val Yellow9 = 0xF7DE8B
    const val Yellow10 = 0xFCEBA4
    const val Yellow11 = 0xFFF6BD

    const val Red1 = 0x402929
    const val Red2 = 0x5E3838
    const val Red3 = 0x7A4343
    const val Red4 = 0x9C4E4E
    const val Red5 = 0xBD5757
    const val Red6 = 0xDB5C5C
    const val Red7 = 0xE37774
    const val Red8 = 0xEB938D
    const val Red9 = 0xF2B1AA
    const val Red10 = 0xF7CCC6
    const val Red11 = 0xFAE3DE

    const val Orange1 = 0x45322B
    const val Orange2 = 0x614438
    const val Orange3 = 0x825845
    const val Orange4 = 0xA36B4E
    const val Orange5 = 0xC27A53
    const val Orange6 = 0xE08855
    const val Orange7 = 0xE5986C
    const val Orange8 = 0xF0AC81
    const val Orange9 = 0xF5BD98
    const val Orange10 = 0xFACEAF
    const val Orange11 = 0xFFDFC7

    const val Purple1 = 0x2F2936
    const val Purple2 = 0x433358
    const val Purple3 = 0x583D7A
    const val Purple4 = 0x6C469C
    const val Purple5 = 0x8150BE
    const val Purple6 = 0x955AE0
    const val Purple7 = 0xA571E6
    const val Purple8 = 0xB589EC
    const val Purple9 = 0xC4A0F3
    const val Purple10 = 0xD4B8F9
    const val Purple11 = 0xE4CEFF

    const val Teal1 = 0x1D3838
    const val Teal2 = 0x1E4D4A
    const val Teal3 = 0x20635D
    const val Teal4 = 0x21786F
    const val Teal5 = 0x238E82
    const val Teal6 = 0x24A394
    const val Teal7 = 0x42B1A4
    const val Teal8 = 0x60C0B5
    const val Teal9 = 0x7DCEC5
    const val Teal10 = 0x9BDDD6
    const val Teal11 = 0xB9EBE6
  }

  // Derived from platform/platform-resources/src/themes/expUI/expUI_light.theme.json
  object Light {
    const val Gray1 = 0x000000
    const val Gray2 = 0x27282E
    const val Gray3 = 0x383A42
    const val Gray4 = 0x494B57
    const val Gray5 = 0x5A5D6B
    const val Gray6 = 0x6C707E
    const val Gray7 = 0x818594
    const val Gray8 = 0xA8ADBD
    const val Gray9 = 0xC9CCD6
    const val Gray10 = 0xD3D5DB
    const val Gray11 = 0xDFE1E5
    const val Gray12 = 0xEBECF0
    const val Gray13 = 0xF7F8FA
    const val Gray14 = 0xFFFFFF
    const val windowsPopupBorder = 0xB9BDC9

    const val Blue1 = 0x2E55A3
    const val Blue2 = 0x315FBD
    const val Blue3 = 0x3369D6
    const val Blue4 = 0x3574F0
    const val Blue5 = 0x4682FA
    const val Blue6 = 0x588CF3
    const val Blue7 = 0x709CF5
    const val Blue8 = 0x88ADF7
    const val Blue9 = 0xA0BDF8
    const val Blue10 = 0xC2D6FC
    const val Blue11 = 0xD4E2FF
    const val Blue12 = 0xEDF3FF
    const val Blue13 = 0xF5F8FE

    const val Green1 = 0x1E6B33
    const val Green2 = 0x1F7536
    const val Green3 = 0x1F8039
    const val Green4 = 0x208A3C
    const val Green5 = 0x369650
    const val Green6 = 0x55A76A
    const val Green7 = 0x89C398
    const val Green8 = 0xAFDBB8
    const val Green9 = 0xC5E5CC
    const val Green10 = 0xE6F7E9
    const val Green11 = 0xF2FCF3

    const val Yellow1 = 0xA46704
    const val Yellow2 = 0xC27D04
    const val Yellow3 = 0xDF9303
    const val Yellow4 = 0xFFAF0F
    const val Yellow5 = 0xFDBD3D
    const val Yellow6 = 0xFED277
    const val Yellow7 = 0xFEE6B1
    const val Yellow8 = 0xFFF1D1
    const val Yellow9 = 0xFFF6DE
    const val Yellow10 = 0xFFFAEB

    const val Red1 = 0xAD2B38
    const val Red2 = 0xBC303E
    const val Red3 = 0xCC3645
    const val Red4 = 0xDB3B4B
    const val Red5 = 0xE55765
    const val Red6 = 0xE46A76
    const val Red7 = 0xED99A1
    const val Red8 = 0xF2B6BB
    const val Red9 = 0xFAD4D8
    const val Red10 = 0xFFF2F3
    const val Red11 = 0xFFF7F7

    const val Orange1 = 0xA14916
    const val Orange2 = 0xB85516
    const val Orange3 = 0xCE6117
    const val Orange4 = 0xE56D17
    const val Orange5 = 0xEC8F4C
    const val Orange6 = 0xF2B181
    const val Orange7 = 0xF9D2B6
    const val Orange8 = 0xFCE6D6
    const val Orange9 = 0xFFF4EB

    const val Teal1 = 0x096A6E
    const val Teal2 = 0x077A7F
    const val Teal3 = 0x058B90
    const val Teal4 = 0x039BA1
    const val Teal5 = 0x3FB3B8
    const val Teal6 = 0x7BCCCF
    const val Teal7 = 0xB6E4E5
    const val Teal8 = 0xDAF4F5
    const val Teal9 = 0xF2FCFC

    const val Purple1 = 0x55339C
    const val Purple2 = 0x643CB8
    const val Purple3 = 0x7444D4
    const val Purple4 = 0x834DF0
    const val Purple5 = 0xA177F4
    const val Purple6 = 0xBFA1F8
    const val Purple7 = 0xDCCBFB
    const val Purple8 = 0xEFE5FF
    const val Purple9 = 0xFAF5FF
  }
}
