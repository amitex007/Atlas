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
package org.apache.hadoop.metadata.web.listeners;

import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.PropertiesConfiguration;
import org.apache.commons.io.FileUtils;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.CommonConfigurationKeysPublic;
import org.apache.hadoop.metadata.web.BaseSecurityTest;
import org.apache.hadoop.security.UserGroupInformation;
import org.apache.hadoop.util.Shell;
import org.testng.annotations.Test;

import java.io.File;

/**
 *
 */
public class LoginProcessorIT extends BaseSecurityTest {

    protected static final String kerberosRule =
            "RULE:[1:$1@$0](.*@EXAMPLE.COM)s/@.*//\nDEFAULT";

    @Test
    public void testDefaultSimpleLogin() throws Exception {
        LoginProcessor processor = new LoginProcessor() {
            @Override
            protected PropertiesConfiguration getPropertiesConfiguration() throws ConfigurationException {
                return new PropertiesConfiguration();
            }
        };
        processor.login();

        assert UserGroupInformation.getCurrentUser() != null;
        assert !UserGroupInformation.isLoginKeytabBased();
        assert !UserGroupInformation.isSecurityEnabled();
    }

    @Test
    public void testKerberosLogin() throws Exception {
        final File keytab = setupKDCAndPrincipals();

        LoginProcessor processor = new LoginProcessor() {
            @Override
            protected PropertiesConfiguration getPropertiesConfiguration() throws ConfigurationException {
                PropertiesConfiguration config = new PropertiesConfiguration();
                config.setProperty("metadata.authentication.method", "kerberos");
                config.setProperty("metadata.authentication.principal", "dgi@EXAMPLE.COM");
                config.setProperty("metadata.authentication.keytab", keytab.getAbsolutePath());
                return config;
            }

            @Override
            protected Configuration getHadoopConfiguration() {
                Configuration config = new Configuration(false);
                config.set(CommonConfigurationKeysPublic.HADOOP_SECURITY_AUTHENTICATION, "kerberos");
                config.setBoolean(CommonConfigurationKeysPublic.HADOOP_SECURITY_AUTHORIZATION, true);
                config.set(CommonConfigurationKeysPublic.HADOOP_SECURITY_AUTH_TO_LOCAL, kerberosRule);

                return config;
            }

            @Override
            protected boolean isHadoopCluster() {
                return true;
            }
        };
        processor.login();

        assert UserGroupInformation.getLoginUser().getShortUserName().endsWith("dgi");
        assert UserGroupInformation.getCurrentUser() != null;
        assert UserGroupInformation.isLoginKeytabBased();
        assert UserGroupInformation.isSecurityEnabled();

        kdc.stop();

    }

    private File setupKDCAndPrincipals() throws Exception {
        // set up the KDC
        File kdcWorkDir = startKDC();

        assert kdc.getRealm() != null;

        File keytabFile = createKeytab(kdc, kdcWorkDir, "dgi", "dgi.keytab");
        String dgiServerPrincipal = Shell.WINDOWS ? "dgi/127.0.0.1" : "dgi/localhost";

        StringBuilder jaas = new StringBuilder(1024);
        jaas.append(createJAASEntry("Client", "dgi", keytabFile));
        jaas.append(createJAASEntry("Server", dgiServerPrincipal, keytabFile));

        File jaasFile = new File(kdcWorkDir, "jaas.txt");
        FileUtils.write(jaasFile, jaas.toString());
        bindJVMtoJAASFile(jaasFile);

        return keytabFile;
    }

}
