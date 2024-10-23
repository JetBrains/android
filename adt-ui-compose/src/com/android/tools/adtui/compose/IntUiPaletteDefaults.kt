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
    const val Gray1 = 0xFF1E1F22
    const val Gray2 = 0xFF2B2D30
    const val Gray3 = 0xFF393B40
    const val Gray4 = 0xFF43454A
    const val Gray5 = 0xFF4E5157
    const val Gray6 = 0xFF5A5D63
    const val Gray7 = 0xFF6F737A
    const val Gray8 = 0xFF868A91
    const val Gray9 = 0xFF9DA0A8
    const val Gray10 = 0xFFB4B8BF
    const val Gray11 = 0xFFCED0D6
    const val Gray12 = 0xFFDFE1E5
    const val Gray13 = 0xFFF0F1F2
    const val Gray14 = 0xFFFFFFFF

    const val Blue1 = 0xFF25324D
    const val Blue2 = 0xFF2E436E
    const val Blue3 = 0xFF35538F
    const val Blue4 = 0xFF375FAD
    const val Blue5 = 0xFF366ACE
    const val Blue6 = 0xFF3574F0
    const val Blue7 = 0xFF467FF2
    const val Blue8 = 0xFF548AF7
    const val Blue9 = 0xFF6B9BFA
    const val Blue10 = 0xFF83ACFC
    const val Blue11 = 0xFF99BBFF

    const val Green1 = 0xFF253627
    const val Green2 = 0xFF273828
    const val Green3 = 0xFF375239
    const val Green4 = 0xFF436946
    const val Green5 = 0xFF4E8052
    const val Green6 = 0xFF57965C
    const val Green7 = 0xFF5FAD65
    const val Green8 = 0xFF73BD79
    const val Green9 = 0xFF89CC8E
    const val Green10 = 0xFFA0DBA5
    const val Green11 = 0xFFB9EBBD
    const val Green12 = 0xFFD4FAD7

    const val Yellow1 = 0xFF3D3223
    const val Yellow2 = 0xFF5E4D33
    const val Yellow3 = 0xFF826A41
    const val Yellow4 = 0xFF9E814A
    const val Yellow5 = 0xFFBA9752
    const val Yellow6 = 0xFFD6AE58
    const val Yellow7 = 0xFFF2C55C
    const val Yellow8 = 0xFFF5D273
    const val Yellow9 = 0xFFF7DE8B
    const val Yellow10 = 0xFFFCEBA4
    const val Yellow11 = 0xFFFFF6BD

    const val Red1 = 0xFF402929
    const val Red2 = 0xFF472B2B
    const val Red3 = 0xFF5E3838
    const val Red4 = 0xFF7A4343
    const val Red5 = 0xFF9C4E4E
    const val Red6 = 0xFFBD5757
    const val Red7 = 0xFFDB5C5C
    const val Red8 = 0xFFE37774
    const val Red9 = 0xFFEB938D
    const val Red10 = 0xFFF2B1AA
    const val Red11 = 0xFFF7CCC6
    const val Red12 = 0xFFFAE3DE

    const val Orange1 = 0xFF45322B
    const val Orange2 = 0xFF614438
    const val Orange3 = 0xFF825845
    const val Orange4 = 0xFFA36B4E
    const val Orange5 = 0xFFC27A53
    const val Orange6 = 0xFFE08855
    const val Orange7 = 0xFFE5986C
    const val Orange8 = 0xFFF0AC81
    const val Orange9 = 0xFFF5BD98
    const val Orange10 = 0xFFFACEAF
    const val Orange11 = 0xFFFFDFC7

    const val Purple1 = 0xFF2F2936
    const val Purple2 = 0xFF3B3147
    const val Purple3 = 0xFF433358
    const val Purple4 = 0xFF583D7A
    const val Purple5 = 0xFF6C469C
    const val Purple6 = 0xFF8150BE
    const val Purple7 = 0xFF955AE0
    const val Purple8 = 0xFFA571E6
    const val Purple9 = 0xFFB589EC
    const val Purple10 = 0xFFC4A0F3
    const val Purple11 = 0xFFD4B8F9
    const val Purple12 = 0xFFE4CEFF

    const val Teal1 = 0xFF1D3838
    const val Teal2 = 0xFF1D3D3B
    const val Teal3 = 0xFF1E4D4A
    const val Teal4 = 0xFF20635D
    const val Teal5 = 0xFF21786F
    const val Teal6 = 0xFF238E82
    const val Teal7 = 0xFF24A394
    const val Teal8 = 0xFF42B1A4
    const val Teal9 = 0xFF60C0B5
    const val Teal10 = 0xFF7DCEC5
    const val Teal11 = 0xFF9BDDD6
    const val Teal12 = 0xFFB9EBE6
  }

  // Derived from platform/platform-resources/src/themes/expUI/expUI_light.theme.json
  object Light {
    const val Gray1 = 0xFF000000
    const val Gray2 = 0xFF27282E
    const val Gray3 = 0xFF383A42
    const val Gray4 = 0xFF494B57
    const val Gray5 = 0xFF5A5D6B
    const val Gray6 = 0xFF6C707E
    const val Gray7 = 0xFF818594
    const val Gray8 = 0xFFA8ADBD
    const val Gray9 = 0xFFC9CCD6
    const val Gray10 = 0xFFD3D5DB
    const val Gray11 = 0xFFDFE1E5
    const val Gray12 = 0xFFEBECF0
    const val Gray13 = 0xFFF7F8FA
    const val Gray14 = 0xFFFFFFFF

    const val Blue1 = 0xFF2E55A3
    const val Blue2 = 0xFF315FBD
    const val Blue3 = 0xFF3369D6
    const val Blue4 = 0xFF3574F0
    const val Blue5 = 0xFF4682FA
    const val Blue6 = 0xFF588CF3
    const val Blue7 = 0xFF709CF5
    const val Blue8 = 0xFF88ADF7
    const val Blue9 = 0xFFA0BDF8
    const val Blue10 = 0xFFC2D6FC
    const val Blue11 = 0xFFD4E2FF
    const val Blue12 = 0xFFEDF3FF
    const val Blue13 = 0xFFF5F8FE

    const val Green1 = 0xFF1E6B33
    const val Green2 = 0xFF1F7536
    const val Green3 = 0xFF1F8039
    const val Green4 = 0xFF208A3C
    const val Green5 = 0xFF369650
    const val Green6 = 0xFF55A76A
    const val Green7 = 0xFF89C398
    const val Green8 = 0xFFAFDBB8
    const val Green9 = 0xFFC5E5CC
    const val Green10 = 0xFFE3F7E7
    const val Green11 = 0xFFF2FCF3

    const val Yellow1 = 0xFFA46704
    const val Yellow2 = 0xFFC27D04
    const val Yellow3 = 0xFFDF9303
    const val Yellow4 = 0xFFFFAF0F
    const val Yellow5 = 0xFFFDBD3D
    const val Yellow6 = 0xFFFED277
    const val Yellow7 = 0xFFFEE6B1
    const val Yellow8 = 0xFFFFF1D1
    const val Yellow9 = 0xFFFFF5DB
    const val Yellow10 = 0xFFFFFAEB

    const val Red1 = 0xFFAD2B38
    const val Red2 = 0xFFBC303E
    const val Red3 = 0xFFCC3645
    const val Red4 = 0xFFDB3B4B
    const val Red5 = 0xFFE55765
    const val Red6 = 0xFFE46A76
    const val Red7 = 0xFFED99A1
    const val Red8 = 0xFFF2B6BB
    const val Red9 = 0xFFFAD4D8
    const val Red10 = 0xFFFFEBEC
    const val Red11 = 0xFFFFF2F3
    const val Red12 = 0xFFFFF7F7

    const val Orange1 = 0xFFA14916
    const val Orange2 = 0xFFB85516
    const val Orange3 = 0xFFCE6117
    const val Orange4 = 0xFFE56D17
    const val Orange5 = 0xFFEC8F4C
    const val Orange6 = 0xFFF2B181
    const val Orange7 = 0xFFF9D2B6
    const val Orange8 = 0xFFFFEFE3
    const val Orange9 = 0xFFFFF4EB

    const val Teal1 = 0xFF096A6E
    const val Teal2 = 0xFF077A7F
    const val Teal3 = 0xFF058B90
    const val Teal4 = 0xFF039BA1
    const val Teal5 = 0xFF3FB3B8
    const val Teal6 = 0xFF7BCCCF
    const val Teal7 = 0xFFB6E4E5
    const val Teal8 = 0xFFDAF4F5
    const val Teal9 = 0xFFF2FCFC

    const val Purple1 = 0xFF55339C
    const val Purple2 = 0xFF643CB8
    const val Purple3 = 0xFF7444D4
    const val Purple4 = 0xFF834DF0
    const val Purple5 = 0xFFA177F4
    const val Purple6 = 0xFFBFA1F8
    const val Purple7 = 0xFFDCCBFB
    const val Purple8 = 0xFFEFE5FF
    const val Purple9 = 0xFFF5EDFF
    const val Purple10 = 0xFFFAF5FF
  }
}
