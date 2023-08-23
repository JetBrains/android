package com.google.samples.apps.kmp

import androidx.compose.runtime.Composable


@Composable
fun HelloApp(viewModel: HelloViewModel) {
    viewModel.logHello()
}