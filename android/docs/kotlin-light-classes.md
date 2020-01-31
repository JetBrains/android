Kotlin light-classes
===

Note: this documentation has a good chance of becoming obsolete as JetBrains makes changes to the Kotlin plugin.
For example, Kotlin plugin version 1.3.50 is enabling "ultra-light" classes by default, which might change some of the performance
characteristics discussed here.

Background
---
When the IDE uses the Kotlin compiler to parse Kotlin files, the result is a bunch of KtElements (e.g., KtClass, KtConstructor, etc.).
However, KtElements do not really implement the full PSI interface. For example, KtClass does not implement PsiClass, and KtFunction does
not implement PsiMethod.

Still, lots of code in the platform depends on resolving to and using PsiClass, PsiMethod, etc. So, to make this work, the Kotlin plugin
wraps KtElements with KtLightElements (also known as Kotlin light-classes). KtLightClass implements PsiClass, KtLightMethod implements
PsiMethod, etc.

The package name for Kotlin light-classes is telling: “org.jetbrains.kotlin.asJava”

Focusing on KtLightClass for a moment, there are basically two ways that it answers queries against the PsiClass interface:

1. If it’s easy to do, it just retrieves the information from the underlying KtElement. For example, see
   KtLightClassForSourceDeclaration.isInterface()

2. Otherwise, it delegates to the Kotlin compiler to create the Java stub for the class, and then uses _that_ to answer the query. (Remember:
   the Kotlin compiler already has to be able to create Java stubs when compiling a mixed Java/Kotlin project in order for Kotlin classes
   to be visible to javac. So, Kotlin light-classes can delegate to the compiler for convenience.) For an example of this, see
   KtLightClassForSourceDeclaration.getSupers(), which delegates to its `clsDelegate`.

Q: Why are light-classes so slow?
---
For queries of type (1), Kotlin light-classes are fast. However, when delegating to the compiler to answer queries of type (2), things get
slow. For example, when KtLightClassForSourceDeclaration delegates to its `clsDelegate`, then the Kotlin compiler has to generate the Java
stub for that class. This results in calls to things like forceResolveAllContents() and kotlin.codegen.ClassBodyCodegen.generateBridges(),
which can take around 50 ms per class in my manual testing. This is why, over time, JetBrains has tried to reduce the number of times it
delegates to the compiler:

    c00fbb236f7cab7e9a256c8c4c3fa55f105b106b
    don't perform full resolve and stub building for isInheritor() checks
    https://youtrack.jetbrains.com/issue/KT-8656

    d74a989d9340e16662bc6b14e7c222d337db115c
    Tweak light classes to avoid computing stubs on certain api calls

    3cb38e7f02b3612d9f0741d0e70b3b39a57f86b2
    Implement getLanguageLevel for FakeFileForLightClass
    https://youtrack.jetbrains.com/issue/KT-12006

    daef8a0eed08502c0aea5f14e4d1459cf8c74666
    Light classes in IDE: Make light class delegate construction a two step process

Sometimes, though, they go back to delegating to the compiler if it helps avoid complexity:

    19db4304bd616cbc9b3abfdc60fbead6f04d7826
    Use clsDelegate to calculate hasModifierProperty("final") for light psi
    https://youtrack.jetbrains.com/issue/KT-17857

That one actually slowed down class inheritor search quite a bit, which is why we filed KT-33250

Q: How can I identity when light-classes are being slow?
---
If you’re looking at a freeze report and it contains a large stack with methods like `getJavaFileStub` or `getClsDelegate`, then it’s very
likely that Kotlin light-classes are to blame for the slowdown.

Q: Are Kotlin light-classes cached?
---
Yes, but the caches are invalidated quite frequently. In the past (and still in 1.3.41) they were invalidated on every PSI change, because
the platform deprecated the out-of-code-block modification tracker. More recently it seems that JetBrains is working on an out-of-code-block
modification tracker specifically for Kotlin; see KotlinModificationTrackerService.

Anyway, one interesting effect of these cache invalidations is that you very frequently get fresh instances of KtLightElements. This means
that any user data stored in that PSI is lost! Here’s a really good example of JetBrains trying to fix a performance issue caused by this:

    6ac345df516b38b4bf5ee1300626c936079f2672
    Caching `KtLightClassForSourceDeclaration` (to keep user data longer)
    to make their UserData survive for longer, because otherwise a new LightClass with empty UserData comes to Spring every time, but Spring stores a lot of important things in UserData
    https://youtrack.jetbrains.com/issue/KT-21701

So, be careful when caching things inside PsiElements that might have come from Kotlin.

Q: What are Kotlin ultra-light classes?
---
Kotlin ultra-light classes were designed in direct response to the slowness of Kotlin light-classes. Their functionality is identical to
light-classes, but the idea is that they don’t delegate to the Kotlin compiler to generate Java stubs. To be honest I don’t know why it’s
another layer (as opposed to just improving the original light-classes), but alas. The first commit is at:

    ebc998d710ae275d9d91d2e53446612aec33fe86
    add ultra-light classes/members that work without backend in simple cases

If successful, ultra-light classes might significantly improve Kotlin IDE support. This especially impacts queries that we do from the
Android plugin, such as class inheritor searches, etc. In KT-33250, for example, I found that delegating to the Kotlin compiler was making
class inheritor search 10x slower than it could be.

At the moment, ultra-light classes are only generated for classes which are “not too complex”
(see UltraLightSupport.isTooComplexForUltraLightGeneration()). Over time JetBrains seems to be supporting more and more class complexity.

Kotlin ultra-light classes will be enabled by default starting in Kotlin 1.3.50:

    1b5f72bd599a3725c8b2bf3b27cd8bd49cde7987
    Enable ultra-light classes by default
    https://youtrack.jetbrains.com/issue/KT-29267
