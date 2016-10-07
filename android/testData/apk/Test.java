import java.util.Collections;
import java.util.List;

// javac -source 1.6 -target 1.6 Test.java
// dx --dex --output=Test.dex Test.class
public class Test {
  public Integer get() {
    return 42;
  }

  public List<Boolean> getList() {
    return Collections.emptyList();
  }
}