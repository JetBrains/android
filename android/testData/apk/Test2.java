
import java.util.Collections;
import java.util.List;

// javac -source 1.6 -target 1.6 Test2.java
// java -jar proguard.jar @Test2.pro -libraryjars ../../../../../../prebuilts/studio/jdk/linux/jre/lib/rt.jar -injars Test2.class:AnotherClass.class:TestSubclass.class::RemovedSubclass.class -outjars Test2.jar
// dx --dex --output=Test2.dex Test2.jar
// rm AnotherClass.class Test2.class TestSubclass.class RemovedSubclass.class Test2.jar
public class Test2 {

  private int aField;
  private AnotherClass aClassField = new AnotherClass();

  public Integer get() {
    return 42;
  }

  public List<Boolean> getList() {
    return Collections.emptyList();
  }
}

//to be removed by Proguard
class RemovedSubclass extends Test2 {
  public List<Boolean> getAnotherList(){
    return getList();
  }
}

class TestSubclass extends Test2 {
  //this method calls a superclass method
  //to generate a method reference without a method definition
  //in TestSubclass
  public List<Boolean> getAnotherList(){
    return getList();
  }
}


class AnotherClass {

  public Boolean aBooleanField;

  AnotherClass(){
    aBooleanField = true;
  }

  AnotherClass(int a, TestSubclass b){

  }
}