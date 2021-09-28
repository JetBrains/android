package test.pkg;

public class AuthDemo {
  private static final String AUTH_NAME = "<warning descr="Possible credential leak">http://user:pwd@www.google.com</warning>";
  private static final String AUTH_IP = "<warning descr="Possible credential leak">scheme://user:pwd@127.0.0.1</warning>:8000";
  private static final String AUTH_NO_LEAK = "scheme://user:%s@www.google.com";
}