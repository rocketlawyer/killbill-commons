/*
 * Copyright 2014 Groupon, Inc
 * Copyright 2014 The Billing Project, LLC
 *
 * The Billing Project licenses this file to you under the Apache License, version 2.0
 * (the "License"); you may not use this file except in compliance with the
 * License.  You may obtain a copy of the License at:
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

package org.killbill.xmlloader;

import java.io.InputStream;
import java.net.URL;
import java.util.Properties;

import org.testng.Assert;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

public class TestUriAccessor {

    private URL guavaUrl = null;
    private String guavaVersion = null;

    @BeforeClass(groups = "fast")
    public void setUp() throws Exception {
        guavaUrl = com.google.common.base.Preconditions.class.getProtectionDomain().getCodeSource().getLocation();
        Assert.assertNotNull(guavaUrl);
        final String guavaPomProperties = "jar:" + guavaUrl + "!/META-INF/maven/com.google.guava/guava/pom.properties";
        try (final InputStream inputStream = UriAccessor.accessUri(guavaPomProperties)) {
            Assert.assertNotNull(inputStream);
            final Properties properties = new Properties();
            properties.load(inputStream);
            guavaVersion = properties.getProperty("version");
        }
        Assert.assertNotNull(guavaVersion);
    }

    @Test(groups = "fast")
    public void testAccessJar() throws Exception {
        final InputStream inputStream = UriAccessor.accessUri(guavaUrl.toString());
        Assert.assertNotNull(inputStream);
    }

    @Test(groups = "fast", description = "https://github.com/killbill/killbill/issues/226")
    public void testAccessFileInJar() throws Exception {
        final String guavaPomProperties = "jar:" + guavaUrl.toString() + "!/META-INF/maven/com.google.guava/guava/pom.properties";
        final InputStream inputStream = UriAccessor.accessUri(guavaPomProperties);
        Assert.assertNotNull(inputStream);

        final Properties properties = new Properties();
        properties.load(inputStream);
        Assert.assertEquals(properties.getProperty("version"), guavaVersion);
        Assert.assertEquals(properties.getProperty("groupId"), "com.google.guava");
        Assert.assertEquals(properties.getProperty("artifactId"), "guava");
    }
}
