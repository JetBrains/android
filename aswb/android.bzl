load("@rules_android//rules:rules.bzl", _aar_import = "aar_import", _android_binary = "android_binary", _android_library = "android_library")

def aar_import(**kwargs):
    _aar_import(**kwargs)

def android_library(**kwargs):
    _android_library(**kwargs)

def android_binary(**kwargs):
    _android_binary(**kwargs)
