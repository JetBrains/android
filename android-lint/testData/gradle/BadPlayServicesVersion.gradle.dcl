declarativeDependencies {
    compile("com.google.android.gms:play-services:4.4.+")
    compile(<error descr="Version `5.2.08` should not be used; the app can not be published with this version. Use version `12.0.1` instead.">"com.google.android.gms:<caret>play-services:5.2.08"</error>)
}
