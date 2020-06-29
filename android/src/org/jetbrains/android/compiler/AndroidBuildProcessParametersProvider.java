package org.jetbrains.android.compiler;

import com.android.prefs.AndroidLocation;
import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.internal.InternalFutureFailureAccess;
import com.google.gson.Gson;
import com.intellij.compiler.server.BuildProcessParametersProvider;
import com.intellij.openapi.application.PathManager;
import com.sun.istack.XMLStreamReaderToContentHandler;
import com.sun.xml.bind.v2.runtime.JAXBContextImpl;
import java.util.List;
import javax.activation.DataSource;
import javax.xml.bind.JAXBContext;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.gradle.tooling.BuildException;
import org.jetbrains.annotations.NotNull;

public class AndroidBuildProcessParametersProvider extends BuildProcessParametersProvider {
  @NotNull
  @Override
  public List<String> getClassPath() {
    return ImmutableList.of(PathManager.getJarPathForClass(Gson.class),

                            // guava
                            PathManager.getJarPathForClass(ImmutableList.class),    // guava
                            PathManager.getJarPathForClass(InternalFutureFailureAccess.class), // guava/failureaccess (for apkzlib)

                            // bouncycastle (for apksig)
                            PathManager.getJarPathForClass(BouncyCastleProvider.class),  // bcprov
                            PathManager.getJarPathForClass(X509CertificateHolder.class), // bcpkix

                            //JAXB for studio.android.sdktools.sdklib on Java 11
                            PathManager.getJarPathForClass(JAXBContext.class),                        // jaxb-api
                            PathManager.getJarPathForClass(JAXBContextImpl.class),                    // jaxb-runtime
                            PathManager.getJarPathForClass(XMLStreamReaderToContentHandler.class),    // istack-commons-runtime
                            PathManager.getJarPathForClass(DataSource.class),                         // javaee.jar


                            PathManager.getJarPathForClass(AndroidLocation.class),
                            PathManager.getJarPathForClass(BuildException.class)); // gradle tooling
  }
}
