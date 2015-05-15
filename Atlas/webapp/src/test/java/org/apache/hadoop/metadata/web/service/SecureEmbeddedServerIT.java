/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.hadoop.metadata.web.service;

import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.WebResource;
import com.sun.jersey.api.client.config.DefaultClientConfig;
import org.apache.commons.configuration.PropertiesConfiguration;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.metadata.web.resources.*;
import org.apache.hadoop.security.alias.CredentialProvider;
import org.apache.hadoop.security.alias.CredentialProviderFactory;
import org.apache.hadoop.security.alias.JavaKeyStoreProvider;
import org.mortbay.jetty.webapp.WebAppContext;
import org.testng.Assert;
import org.testng.TestListenerAdapter;
import org.testng.TestNG;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import javax.ws.rs.core.UriBuilder;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.util.List;

/**
 *
 */
public class SecureEmbeddedServerIT {


    private SecureEmbeddedServer secureEmbeddedServer;
    private String providerUrl;
    private Path jksPath;
    protected WebResource service;

    static {
        //for localhost testing only
        javax.net.ssl.HttpsURLConnection.setDefaultHostnameVerifier(
                new javax.net.ssl.HostnameVerifier(){

                    public boolean verify(String hostname,
                                          javax.net.ssl.SSLSession sslSession) {
                        if (hostname.equals("localhost")) {
                            return true;
                        }
                        return false;
                    }
                });
        System.setProperty("javax.net.ssl.trustStore", SecureEmbeddedServer.DEFAULT_KEYSTORE_FILE_LOCATION);
        System.setProperty("javax.net.ssl.trustStorePassword", "keypass");
        System.setProperty("javax.net.ssl.trustStoreType", "JKS");


    }

    @BeforeClass
    public void setupServerURI () throws Exception {
        BaseResourceIT.baseUrl = "https://localhost:21443";
    }

    @AfterClass
    public void resetServerURI() throws Exception {
        BaseResourceIT.baseUrl = "http://localhost:21000";
    }

    @BeforeMethod
    public void setup() throws Exception {
        jksPath = new Path(Files.createTempDirectory("tempproviders").toString(), "test.jks");
        providerUrl = JavaKeyStoreProvider.SCHEME_NAME + "://file" + jksPath.toUri();

        String baseUrl = "https://localhost:21443/";

        DefaultClientConfig config = new DefaultClientConfig();
        Client client = Client.create(config);
        client.resource(UriBuilder.fromUri(baseUrl).build());

        service = client.resource(UriBuilder.fromUri(baseUrl).build());
    }

    @Test
    public void testNoConfiguredCredentialProvider() throws Exception {

        try {
            secureEmbeddedServer = new SecureEmbeddedServer(21443, "webapp/target/metadata-governance");
            WebAppContext webapp = new WebAppContext();
            webapp.setContextPath("/");
            webapp.setWar(System.getProperty("user.dir") + getWarPath());
            secureEmbeddedServer.server.setHandler(webapp);

            secureEmbeddedServer.server.start();

            Assert.fail("Should have thrown an exception");
        } catch (IOException e) {
            assert e.getMessage().equals("No credential provider path configured for storage of certificate store passwords");
        } finally {
            secureEmbeddedServer.server.stop();
        }
    }

    @Test
    public void testServerConfiguredUsingCredentialProvider() throws Exception {
        // setup the configuration
        final PropertiesConfiguration configuration = new PropertiesConfiguration();
        configuration.setProperty(SecureEmbeddedServer.CERT_STORES_CREDENTIAL_PROVIDER_PATH, providerUrl);
        // setup the credential provider
        setupCredentials();

        try {
            secureEmbeddedServer = new SecureEmbeddedServer(21443, "webapp/target/metadata-governance") {
                @Override
                protected PropertiesConfiguration getConfiguration() {
                    return configuration;
                }
            };
            WebAppContext webapp = new WebAppContext();
            webapp.setContextPath("/");
            webapp.setWar(System.getProperty("user.dir") + getWarPath());
            secureEmbeddedServer.server.setHandler(webapp);

            secureEmbeddedServer.server.start();

            URL url = new URL("https://localhost:21443/");
            HttpURLConnection connection = (HttpURLConnection)url.openConnection();
            connection.setRequestMethod("GET");
            connection.connect();

            // test to see whether server is up and root page can be served
            assert connection.getResponseCode() == 200;
        } finally {
            secureEmbeddedServer.server.stop();
        }

    }

    @Test
    public void testMissingEntriesInCredentialProvider() throws Exception {
        // setup the configuration
        final PropertiesConfiguration configuration = new PropertiesConfiguration();
        configuration.setProperty(SecureEmbeddedServer.CERT_STORES_CREDENTIAL_PROVIDER_PATH, providerUrl);

        try {
            secureEmbeddedServer = new SecureEmbeddedServer(21443, "webapp/target/metadata-governance") {
                @Override
                protected PropertiesConfiguration getConfiguration() {
                    return configuration;
                }
            };
            Assert.fail("No entries should generate an exception");
        } catch (IOException e) {
            assert e.getMessage().startsWith("No credential entry found for");
        } finally {
            secureEmbeddedServer.server.stop();
        }

    }

    /**
     * Runs the existing webapp test cases, this time against the initiated secure server instance.
     * @throws Exception
     */
    @Test
    public void runOtherSuitesAgainstSecureServer() throws Exception {
        final PropertiesConfiguration configuration = new PropertiesConfiguration();
        configuration.setProperty(SecureEmbeddedServer.CERT_STORES_CREDENTIAL_PROVIDER_PATH, providerUrl);
        // setup the credential provider
        setupCredentials();

        try {
            secureEmbeddedServer = new SecureEmbeddedServer(21443, "webapp/target/metadata-governance") {
                @Override
                protected PropertiesConfiguration getConfiguration() {
                    return configuration;
                }
            };
            WebAppContext webapp = new WebAppContext();
            webapp.setContextPath("/");
            webapp.setWar(System.getProperty("user.dir") + getWarPath());
            secureEmbeddedServer.server.setHandler(webapp);

            secureEmbeddedServer.server.start();

            TestListenerAdapter tla = new TestListenerAdapter();
            TestNG testng = new TestNG();
            testng.setTestClasses(new Class[] { AdminJerseyResourceIT.class, EntityJerseyResourceIT.class,
                    MetadataDiscoveryJerseyResourceIT.class, RexsterGraphJerseyResourceIT.class, TypesJerseyResourceIT.class});
            testng.addListener(tla);
            testng.run();

        } finally {
            secureEmbeddedServer.server.stop();
        }

    }

    protected String getWarPath() {
        return String.format("/target/metadata-webapp-%s.war",
                System.getProperty("project.version", "0.1-incubating-SNAPSHOT"));
    }

    private void setupCredentials()
            throws Exception {
        Configuration conf = new Configuration(false);

        File file = new File(jksPath.toUri().getPath());
        file.delete();
        conf.set(CredentialProviderFactory.CREDENTIAL_PROVIDER_PATH, providerUrl);

        CredentialProvider provider =
                CredentialProviderFactory.getProviders(conf).get(0);

        // create new aliases
        try {

            char[] storepass = {'k', 'e', 'y', 'p', 'a', 's', 's'};
            provider.createCredentialEntry(
                    SecureEmbeddedServer.KEYSTORE_PASSWORD_KEY, storepass);

            char[] trustpass = {'k', 'e', 'y', 'p', 'a', 's', 's'};
            provider.createCredentialEntry(
                    SecureEmbeddedServer.TRUSTSTORE_PASSWORD_KEY, trustpass);

            char[] certpass = {'k', 'e', 'y', 'p', 'a', 's', 's'};
            provider.createCredentialEntry(
                    SecureEmbeddedServer.SERVER_CERT_PASSWORD_KEY, certpass);

            // write out so that it can be found in checks
            provider.flush();
        } catch (Exception e) {
            e.printStackTrace();
            throw e;
        }
    }

}
