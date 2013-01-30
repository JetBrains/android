package org.jetbrains.jps.android;

import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.containers.HashMap;
import com.intellij.util.containers.HashSet;
import org.jetbrains.android.util.AndroidBuildTestingManager;
import org.jetbrains.annotations.NotNull;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.StringWriter;
import java.nio.charset.Charset;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author Eugene.Kudelevsky
 */
abstract class AndroidBuildTestingCommandExecutor implements AndroidBuildTestingManager.MyCommandExecutor {

  private volatile StringWriter myStringWriter = new StringWriter();
  private final Map<String, Pattern> myPathPatterns = new HashMap<String, Pattern>();

  private final Set<String> myCheckedJars = new HashSet<String>();

  public void addPathPrefix(@NotNull String id, @NotNull String prefix) {
    myPathPatterns.put(id, Pattern.compile("(" + FileUtil.toSystemIndependentName(prefix) + ").*"));
  }

  public void addRegexPathPattern(@NotNull String id, @NotNull String regex) {
    myPathPatterns.put(id, Pattern.compile("(" + regex + ")"));
  }

  public void addRegexPathPatternPrefix(@NotNull String id, @NotNull String regex) {
    myPathPatterns.put(id, Pattern.compile("(" + regex + ").*"));
  }

  @NotNull
  @Override
  public Process createProcess(@NotNull String[] args, @NotNull Map<? extends String, ? extends String> environment) {
    final String[] argsToLog = processArgs(args);
    logString(StringUtil.join(argsToLog, "\n"));

    if (environment.size() > 0) {
      final StringBuilder envBuilder = new StringBuilder();

      for (Map.Entry<? extends String, ? extends String> entry : environment.entrySet()) {
        if (envBuilder.length() > 0) {
          envBuilder.append(", ");
        }
        final String value = progessArg(entry.getValue());
        envBuilder.append(entry.getKey()).append("=").append(value);
      }
      logString("\nenv: " + envBuilder.toString());
    }
    logString("\n\n");
    try {
      return doCreateProcess(args, environment);
    }
    catch (RuntimeException e) {
      throw e;
    }
    catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public void log(@NotNull String s) {
    final String[] args = s.split("\\n");
    logString(StringUtil.join(processArgs(args), "\n"));
    logString("\n\n");
  }

  @Override
  public void checkJarContent(@NotNull String jarId, @NotNull String jarPath) {
    doCheckJar(jarId, jarPath);
    myCheckedJars.add(jarId);
  }

  protected void doCheckJar(@NotNull String jarId, @NotNull String jarPath) {
  }

  private synchronized void logString(String s) {
    myStringWriter.write(s);
  }

  private String[] processArgs(String[] args) {
    final String[] result = new String[args.length];

    for (int i = 0; i < result.length; i++) {
      result[i] = progessArg(args[i]);
    }
    return result;
  }

  private String progessArg(String arg) {
    String s = FileUtil.toSystemIndependentName(arg);

    for (Map.Entry<String, Pattern> entry : myPathPatterns.entrySet()) {
      final Pattern prefixPattern = entry.getValue();
      final String id = entry.getKey();
      final Matcher matcher = prefixPattern.matcher(s);

      if (matcher.matches()) {
        s = "$" + id + "$" + s.substring(matcher.group(1).length());
      }
    }
    return s;
  }

  @NotNull
  protected abstract Process doCreateProcess(@NotNull String[] args, @NotNull Map<? extends String, ? extends String> environment)
    throws Exception;

  @NotNull
  public synchronized String getLog() {
    return myStringWriter.toString();
  }

  public synchronized void clear() {
    myStringWriter = new StringWriter();
    myCheckedJars.clear();
  }

  @NotNull
  protected Set<String> getCheckedJars() {
    return myCheckedJars;
  }

  protected static class MyProcess extends Process {

    private final int myExitValue;
    private final String myOutputText;
    private final String myErrorText;

    protected MyProcess(int exitValue, @NotNull String outputText, @NotNull String errorText) {
      myExitValue = exitValue;
      myOutputText = outputText;
      myErrorText = errorText;
    }

    @Override
    public OutputStream getOutputStream() {
      throw new UnsupportedOperationException();
    }

    @Override
    public InputStream getInputStream() {
      return stringToInputStream(myOutputText);
    }

    @Override
    public InputStream getErrorStream() {
      return stringToInputStream(myErrorText);
    }

    private static ByteArrayInputStream stringToInputStream(String s) {
      return new ByteArrayInputStream(s.getBytes(Charset.defaultCharset()));
    }

    @Override
    public int waitFor() throws InterruptedException {
      return exitValue();
    }

    @Override
    public int exitValue() {
      return myExitValue;
    }

    @Override
    public void destroy() {
    }
  }
}
