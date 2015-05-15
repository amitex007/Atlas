/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
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

import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.PropertiesConfiguration;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.security.alias.CredentialProvider;
import org.apache.hadoop.security.alias.CredentialProviderFactory;
import org.mortbay.jetty.Connector;
import org.mortbay.jetty.security.SslSocketConnector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

/**
 * This is a jetty server which requires client auth via certificates.
 */
public class SecureEmbeddedServer extends EmbeddedServer {

    public static final String KEYSTORE_FILE_KEY = "keystore.file";
    public static final String DEFAULT_KEYSTORE_FILE_LOCATION = "target/metadata.keystore";
    public static final String KEYSTORE_PASSWORD_KEY = "keystore.password";
    public static final String TRUSTSTORE_FILE_KEY = "truststore.file";
    public static final String DEFATULT_TRUSTORE_FILE_LOCATION = "target/metadata.keystore";
    public static final String TRUSTSTORE_PASSWORD_KEY = "truststore.password";
    public static final String SERVER_CERT_PASSWORD_KEY = "password";
    public static final String CLIENT_AUTH_KEY = "client.auth.enabled";
    public static final String CERT_STORES_CREDENTIAL_PROVIDER_PATH = "cert.stores.credential.provider.path";

    private static final Logger LOG = LoggerFactory.getLogger(SecureEmbeddedServer.class);

    public SecureEmbeddedServer(int port, String path) throws IOException {
        super(port, path);
    }

    protected Connector getConnector(int port) throws IOException {
        PropertiesConfiguration config = getConfiguration();

        SslSocketConnector connector = new SslSocketConnector();
        connector.setPort(port);
        connector.setHost("0.0.0.0");
        connector.setKeystore(config.getString(KEYSTORE_FILE_KEY,
                System.getProperty(KEYSTORE_FILE_KEY, DEFAULT_KEYSTORE_FILE_LOCATION)));
        connector.setKeyPassword(getPassword(config, KEYSTORE_PASSWORD_KEY));
        connector.setTruststore(config.getString(TRUSTSTORE_FILE_KEY,
                System.getProperty(TRUSTSTORE_FILE_KEY, DEFATULT_TRUSTORE_FILE_LOCATION)));
        connector.setTrustPassword(getPassword(config, TRUSTSTORE_PASSWORD_KEY));
        connector.setPassword(getPassword(config, SERVER_CERT_PASSWORD_KEY));
        connector.setWantClientAuth(config.getBoolean(CLIENT_AUTH_KEY, Boolean.getBoolean(CLIENT_AUTH_KEY)));
        return connector;
    }

    /**
     * Retrieves a password from a configured credential provider or prompts for the password and stores it in the
     * configured credential provider.
     * @param config application configuration
     * @param key the key/alias for the password.
     * @return  the password.
     * @throws IOException
     */
    private String getPassword(PropertiesConfiguration config, String key) throws IOException {

        String password = null;

        String provider = config.getString(CERT_STORES_CREDENTIAL_PROVIDER_PATH);
        if (provider != null) {
            LOG.info("Attempting to retrieve password from configured credential provider path");
            Configuration c = new Configuration();
            c.set(CredentialProviderFactory.CREDENTIAL_PROVIDER_PATH, provider);
            CredentialProvider credentialProvider =
                    CredentialProviderFactory.getProviders(c).get(0);
            CredentialProvider.CredentialEntry entry = credentialProvider.getCredentialEntry(key);
            if (entry == null) {
                throw new IOException(String.format("No credential entry found for %s. " +
                        "Please create an entry in the configured credential provider", key));
            } else {
                password = String.valueOf(entry.getCredential());
            }

        } else {
            throw new IOException("No credential provider path configured for storage of certificate store passwords");
        }

        return password;
    }

    /**
     * Returns the application configuration.
     * @return
     */
    protected PropertiesConfiguration getConfiguration() {
        try {
            return new PropertiesConfiguration("application.properties");
        } catch (ConfigurationException e) {
            throw new RuntimeException("Unable to load configuration: application.properties");
        }
    }
}
