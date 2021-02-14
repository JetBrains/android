# Overview

This module is to hold classes contributing data to JPS (JetBrains Project System) configuration 
files (i.e. `.idea/*`, `*.iml`, etc.). These classes are shared between IDE and JPS build process.
In fact this means that the module should not have dependencies on classes that are not availabe
in JPS build process (e.g. `org.jetbrains.android.facet.AndroidFacet`). Also, language level 
should match minimal supported language level for JPS build process (i.e. 1.8). 

This module does not contain JPS build plugin itself. JPS build plugin sources are available in 
[intellij.android.jpsBuildPlugin.jps](../jps-build-plugin/jps).
