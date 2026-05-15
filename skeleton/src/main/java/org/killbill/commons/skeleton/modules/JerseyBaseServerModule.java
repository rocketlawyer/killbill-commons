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

package org.killbill.commons.skeleton.modules;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import javax.inject.Singleton;
import javax.servlet.Filter;
import javax.servlet.http.HttpServlet;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Joiner;
import com.google.common.base.MoreObjects;
import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import org.glassfish.jersey.logging.LoggingFeature;
import org.glassfish.jersey.servlet.ServletContainer;
import org.killbill.commons.skeleton.jersey.GuiceJerseyBridgeListener;

public class JerseyBaseServerModule extends BaseServerModule {

    private static final Joiner joiner = Joiner.on(";");

    /**
     * Jersey 2 servlet init parameter for explicit provider classes (filters, features, etc.).
     */
    public static final String JERSEY_SERVER_PROVIDER_CLASSNAMES = "jersey.config.server.provider.classnames";

    /**
     * Jersey 2 servlet init parameter for package scanning.
     */
    public static final String JERSEY_SERVER_PROVIDER_PACKAGES = "jersey.config.server.provider.packages";

    /**
     * Jersey 1.x init parameter name, still accepted as input via {@link BaseServerModuleBuilder#addJerseyParam}
     * and translated into {@link #JERSEY_SERVER_PROVIDER_CLASSNAMES}.
     */
    @VisibleForTesting
    static final String JERSEY_CONTAINER_REQUEST_FILTERS = "com.sun.jersey.spi.container.ContainerRequestFilters";

    /**
     * Jersey 1.x init parameter name, still accepted as input via {@link BaseServerModuleBuilder#addJerseyParam}
     * and merged into {@link #JERSEY_SERVER_PROVIDER_CLASSNAMES}.
     */
    @VisibleForTesting
    static final String JERSEY_CONTAINER_RESPONSE_FILTERS = "com.sun.jersey.spi.container.ContainerResponseFilters";

    /**
     * Jersey 1.x init parameter name, still accepted as input via {@link BaseServerModuleBuilder#addJerseyParam}
     * and translated into {@link LoggingFeature#LOGGING_FEATURE_VERBOSITY}.
     */
    @VisibleForTesting
    static final String JERSEY_DISABLE_ENTITYLOGGING = "com.sun.jersey.config.feature.logging.DisableEntitylogging";

    private final ImmutableMap.Builder<String, String> jerseyParams;

    public JerseyBaseServerModule(final Map<String, ArrayList<Entry<Class<? extends Filter>, Map<String, String>>>> filters,
                                  final Map<String, ArrayList<Entry<Class<? extends Filter>, Map<String, String>>>> filtersRegex,
                                  final Map<String, Class<? extends HttpServlet>> servlets,
                                  final Map<String, Class<? extends HttpServlet>> servletsRegex,
                                  final Map<String, Class<? extends HttpServlet>> jaxrsServlets,
                                  final Map<String, Class<? extends HttpServlet>> jaxrsServletsRegex,
                                  final String jaxrsUriPattern,
                                  final Collection<String> jaxrsResources,
                                  final List<String> jerseyFilters,
                                  final Map<String, String> jerseyParams) {
        super(filters, filtersRegex, servlets, servletsRegex, jaxrsServlets, jaxrsServletsRegex, jaxrsUriPattern, jaxrsResources);

        String manuallySpecifiedRequestFilters = Strings.nullToEmpty(jerseyParams.remove(JERSEY_CONTAINER_REQUEST_FILTERS));
        String manuallySpecifiedResponseFilters = Strings.nullToEmpty(jerseyParams.remove(JERSEY_CONTAINER_RESPONSE_FILTERS));
        if (!jerseyFilters.isEmpty()) {
            if (!manuallySpecifiedRequestFilters.isEmpty()) {
                manuallySpecifiedRequestFilters += ";";
            }
            if (!manuallySpecifiedResponseFilters.isEmpty()) {
                manuallySpecifiedResponseFilters += ";";
            }
        }
        final String containerRequestFilters = manuallySpecifiedRequestFilters + joiner.join(jerseyFilters);
        final String containerResponseFilters = manuallySpecifiedResponseFilters + joiner.join(Lists.reverse(jerseyFilters));

        this.jerseyParams = new ImmutableMap.Builder<String, String>();
        String providerClassNames = mergeSemicolonListsToCommaClassnames(containerRequestFilters, containerResponseFilters);
        providerClassNames = appendProviderClassname(providerClassNames, GuiceJerseyBridgeListener.class.getName());
        if (!providerClassNames.isEmpty()) {
            this.jerseyParams.put(JERSEY_SERVER_PROVIDER_CLASSNAMES, providerClassNames);
        }

        final String disableEntityLogging = MoreObjects.firstNonNull(Strings.emptyToNull(jerseyParams.remove(JERSEY_DISABLE_ENTITYLOGGING)), "true");
        final LoggingFeature.Verbosity verbosity = Boolean.parseBoolean(disableEntityLogging)
                                                     ? LoggingFeature.Verbosity.HEADERS_ONLY
                                                     : LoggingFeature.Verbosity.PAYLOAD_ANY;
        this.jerseyParams.put(LoggingFeature.LOGGING_FEATURE_VERBOSITY, verbosity.name())
                         .putAll(jerseyParams);
    }

    private static String mergeSemicolonListsToCommaClassnames(final String requestList, final String responseList) {
        final LinkedHashSet<String> ordered = new LinkedHashSet<String>();
        for (final String part : Splitter.on(';').omitEmptyStrings().trimResults().split(Strings.nullToEmpty(requestList))) {
            ordered.add(part);
        }
        for (final String part : Splitter.on(';').omitEmptyStrings().trimResults().split(Strings.nullToEmpty(responseList))) {
            ordered.add(part);
        }
        return Joiner.on(",").join(ordered);
    }

    /**
     * Appends a provider class for Jersey to register, preserving order and skipping duplicates.
     */
    private static String appendProviderClassname(final String commaSeparatedClassnames, final String classname) {
        final LinkedHashSet<String> ordered = new LinkedHashSet<String>();
        for (final String part : Splitter.on(',').omitEmptyStrings().trimResults().split(Strings.nullToEmpty(commaSeparatedClassnames))) {
            ordered.add(part);
        }
        ordered.add(classname);
        return Joiner.on(",").join(ordered);
    }

    @Override
    protected void configureResources() {
        bind(ServletContainer.class).in(Singleton.class);

        for (final String urlPattern : jaxrsServlets.keySet()) {
            serve(urlPattern).with(jaxrsServlets.get(urlPattern), jerseyParams.build());
        }

        for (final String urlPattern : jaxrsServletsRegex.keySet()) {
            serveRegex(urlPattern).with(jaxrsServletsRegex.get(urlPattern), jerseyParams.build());
        }

        // Catch-all resources
        if (!jaxrsResources.isEmpty()) {
            jerseyParams.put(JERSEY_SERVER_PROVIDER_PACKAGES, joiner.join(jaxrsResources));
            serveRegex(jaxrsUriPattern).with(ServletContainer.class, jerseyParams.build());
        }
    }

    @VisibleForTesting
    ImmutableMap.Builder<String, String> getJerseyParams() {
        return jerseyParams;
    }
}
