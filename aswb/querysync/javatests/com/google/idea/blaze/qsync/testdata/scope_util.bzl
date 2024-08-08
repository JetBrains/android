"""Utility code for generating the scope for a genquery that uses the ":*" query syntax.

In a genquery rule, bazel requires that you specify a scope defined thus:

> The scope of the query. The query is not allowed to touch targets outside the
> transitive closure of these targets.

The macros herein generate the scope for various standard rule kinds.
"""

def scopeForJavaPackage(blaze_package):
    label = Label(blaze_package)
    return [
        label,
        "//" + label.package + ":BUILD",
        "//" + label.package + ":lib" + label.name + ".jar",
        "//" + label.package + ":lib" + label.name + "-src.jar",
    ]

def scopeForAndroidPackage(blaze_package):
    label = Label(blaze_package)
    return scopeForJavaPackage(blaze_package) + [
        "//" + label.package + ":" + label.name + ".aar",
    ]

def scopeForAndroidPackageWithResources(blaze_package):
    label = Label(blaze_package)
    return scopeForAndroidPackage(blaze_package) + [
        "//" + label.package + ":" + label.name + ".aar",
        "//" + label.package + ":" + label.name + ".srcjar",
        "//" + label.package + ":" + label.name + "_resources.jar",
        "//" + label.package + ":" + label.name + "_symbols/R.txt",
    ]

def scopeForAndroidBinary(blaze_package):
    label = Label(blaze_package)
    return [
        label,
        "//" + label.package + ":" + label.name + ".apk",
        "//" + label.package + ":" + label.name + "_deploy.jar",
        "//" + label.package + ":" + label.name + "_unsigned.apk",
    ]

def scopeForCcPackage(blaze_package):
    label = Label(blaze_package)
    return [
        label,
        "//" + label.package + ":BUILD",
    ]
