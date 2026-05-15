/*
 * Copyright 2010-2014 Ning, Inc.
 *
 * Ning licenses this file to you under the Apache License, version 2.0
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

package org.killbill.commons.skeleton.jersey;

import javax.ws.rs.ext.Provider;

import org.glassfish.hk2.api.ServiceLocator;
import org.glassfish.jersey.inject.hk2.DelayedHk2InjectionManager;
import org.glassfish.jersey.inject.hk2.ImmediateHk2InjectionManager;
import org.glassfish.jersey.internal.inject.InjectionManager;
import org.glassfish.jersey.server.spi.Container;
import org.glassfish.jersey.server.spi.ContainerLifecycleListener;
import org.jvnet.hk2.guice.bridge.api.GuiceBridge;
import org.jvnet.hk2.guice.bridge.api.GuiceIntoHK2Bridge;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.jaxrs.json.JacksonJsonProvider;
import com.google.inject.ConfigurationException;
import com.google.inject.Injector;
/**
 * Links the Jersey/HK2 {@link InjectionManager} to the application {@link Injector} so that
 * HK2-backed Jersey can resolve types declared in Guice (e.g. {@code bind(Foo.class).to(Bar.class).asEagerSingleton()}).
 */
@Provider
public class GuiceJerseyBridgeListener implements ContainerLifecycleListener {

    private static final Logger log = LoggerFactory.getLogger(GuiceJerseyBridgeListener.class);

    private static volatile Injector guiceInjector;

    /**
     * Called from {@link org.killbill.commons.skeleton.listeners.GuiceServletContextListener} after the
     * {@link com.google.inject.servlet.GuiceServletContextListener} has stored the injector in the servlet context.
     */
    public static void cacheGuiceInjector(final Injector injector) {
        guiceInjector = injector;
    }

    @Override
    public void onStartup(final Container container) {
        final Injector injector = guiceInjector;
        if (injector == null) {
            throw new IllegalStateException(
                    "GuiceJerseyBridgeListener: Guice Injector not cached. Ensure GuiceServletContextListener.contextInitialized runs before Jersey starts.");
        }
        final InjectionManager injectionManager = container.getApplicationHandler().getInjectionManager();
        final ServiceLocator serviceLocator = serviceLocatorFrom(injectionManager);
        log.debug("Bridging HK2 ServiceLocator {} to Guice injector {}", serviceLocator, injector);
        GuiceBridge.getGuiceBridge().initializeGuiceBridge(serviceLocator);
        serviceLocator.getService(GuiceIntoHK2Bridge.class).bridgeGuiceInjector(injector);

        // Guice exposes JacksonJsonProvider (see JaxrsJacksonModule) but Jersey does not treat it as a
        // MessageBodyWriter until it is registered on the InjectionManager — without this, responses
        // with application/json fail with "MessageBodyWriter not found".
        try {
            final JacksonJsonProvider jacksonJsonProvider = injector.getInstance(JacksonJsonProvider.class);
            injectionManager.register(jacksonJsonProvider);
            log.debug("Registered Guice JacksonJsonProvider with Jersey InjectionManager");
        } catch (final ConfigurationException e) {
            log.debug("JacksonJsonProvider not bound in Guice, skipping JSON provider registration: {}", e.getMessage());
        }
    }
    @Override
    public void onReload(final Container container) {
        // Avoid re-running initializeGuiceBridge on the same HK2 ServiceLocator (version-sensitive).
    }

    @Override
    public void onShutdown(final Container container) {
        // HK2 / Jersey tear down the ServiceLocator; no explicit Guice bridge shutdown required.
    }

    private static ServiceLocator serviceLocatorFrom(final InjectionManager injectionManager) {
        if (injectionManager instanceof DelayedHk2InjectionManager) {
            return ((DelayedHk2InjectionManager) injectionManager).getServiceLocator();
        }
        if (injectionManager instanceof ImmediateHk2InjectionManager) {
            return ((ImmediateHk2InjectionManager) injectionManager).getServiceLocator();
        }
        throw new IllegalStateException(
                "GuiceJerseyBridgeListener requires HK2-backed InjectionManager (jersey-hk2). Got: "
                + (injectionManager == null ? "null" : injectionManager.getClass().getName()));
    }
}
