## Layout Editor Drag and Drop Test

+ First, create a new empty project with empty activity.
+ Next, copy (./complicated.xml) to app/res/layout folder in the Studio project.
+ Next, open complicated.xml in design mode
+ Next, wait for the design into display. It should looks like ![screenshot step0.png]
+ Next, drag "FIRST" button into ConstraintLayout whichs has **pink** background. Observe the "FIRST" button is dragged into it. It should looks like ![screenshot step1.png] but the position of FIRST button depends on where the mouse released.
+ Next, drag "SECOND" button into LinearLayout(vertical) whichs has **purple** background. Observe the "SECOND" button is append at the end of it. It should looks like ![screenshot step2.png]
+ Next, drag the only Image into LinearLayout(vertical) whichs has **green** background. Observe the Image is append at the end of it and become smaller. It should looks like ![screenshot step3.png]
+ Next, drag "FORTH" button into FrameLayout which has **light blue** background. Observe the "FORTH" button is dragged into it.It should looks like ![screenshot step4.png]
+ Next, drag "FIFTH" TextView to root layout which is LinearLayout(horizontal) with *white* background. Observe the "FIFTH" TextView is append at the end of it. It should looks like ![screenshot step5.png]
+ Next, drag a Button from Palette and place in the ConstraintLayout which has **pink** background. Observe the position is same as where mouse is released. It should looks like ![screenshot step6.png]


[screenshot step0.png]: res/layout-editor-drag-and-drop/screenshots/step0.png
[screenshot step1.png]: res/layout-editor-drag-and-drop/screenshots/step1.png
[screenshot step2.png]: res/layout-editor-drag-and-drop/screenshots/step2.png
[screenshot step3.png]: res/layout-editor-drag-and-drop/screenshots/step3.png
[screenshot step4.png]: res/layout-editor-drag-and-drop/screenshots/step4.png
[screenshot step5.png]: res/layout-editor-drag-and-drop/screenshots/step5.png
[screenshot step6.png]: res/layout-editor-drag-and-drop/screenshots/step6.png