package test.pkg;
class Test {
   public void oldName() {
       <warning descr="This error has a quickfix which edits parent method name instead">renameMethodNameInstead()</warning>;
       <warning descr="This error has a quickfix which edits something in a separate build.gradle file instead">updateBuild<caret>Gradle()</warning>;
   }
   private void renameMethodNameInstead() {
   }
   private void updateBuildGradle() {
       String x = <warning descr="This code mentions `lint`: **Congratulations**">"Say hello, lint!"</warning>;
   }
}
