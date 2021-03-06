package com.republicate.modality.webapp.auth;

/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

import com.republicate.modality.webapp.ModalityFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URLDecoder;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;


/**
 * <p>Authentication filter skeleton.</p>
 * <p>The filter should map all protected resources URIs. It can also map a wider range, provided
 * you have configured either the <code>auth.protected</code> or the <code>auth.public</code> URI regex or overridden
 * the <code>isProtectedURI(uri)</code> method.</p>
 * <p>Configuration parameters:</p>
 * <ul>
 * <li><code>auth.realm</code> - authentication realm</li>
 * <li><code>auth.protected</code> - regular expression meant to match all resources URIs which are to be protected ;
 * by default all mapped resources are protected.
 *</ul>
 * <p>Configuration parameters can be specified in the <code>modality.properties</code> file, or as configure-param or context-param in the <code>web.xml</code> webapp descriptor.</p>
 * @param <USER> User class
 */

public abstract class BaseAuthFilter<USER> extends ModalityFilter
{
    // CB TODO - it could just implement Filter and hold a WebappModalityConfig,
    // so that it's totally independent form Model, but it would requires some refactoring
    // since children classes may require the WebappModalityConfig to be a WebappModelProvider
    // (or they could hold their own WebappModelProvider).

    protected static Logger logger = LoggerFactory.getLogger("auth");

    // config parameters keys

    public static final String REALM = "auth.realm";
    public static final String PROTECTED_RESOURCES = "auth.protected";
    public static final String PUBLIC_RESOURCES = "auth.public";

    protected boolean isProtectedURI(String uri)
    {
        if (protectedResources != null)
        {
            return protectedResources.matcher(uri).matches();
        }
        else if (publicResources != null)
        {
            return !publicResources.matcher(uri).matches();
        }
        else
        {
            return true;
        }
    }

    protected void processPublicRequest(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws IOException, ServletException
    {
        filterChain.doFilter(request, response);
    }

    protected USER getAuthentifiedUser(HttpServletRequest request) throws ServletException
    {
        return authenticate(request);
    }

    protected abstract USER authenticate(HttpServletRequest request) throws ServletException;

    protected void processProtectedRequest(USER logged, HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws IOException, ServletException
    {
        if (logger.isDebugEnabled())
        {
            // CB TODO - other uris logging should decode uris
            String characterEncoding = Optional.ofNullable(request.getCharacterEncoding()).orElse("utf-8");
            logger.debug("user '{}' going towards {}", displayUser(logged), URLDecoder.decode(request.getRequestURI(), characterEncoding));
        }
        filterChain.doFilter(request, response);
    }

    protected void processForbiddenRequest(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws IOException, ServletException
    {
        logger.debug("unauthorized request towards {}", request.getRequestURI());
        response.sendError(HttpServletResponse.SC_FORBIDDEN);
    }

    /* Filter interface */

    /**
     * <p>Filter initialization.</p>
     * <p>Child classes should start with <code>super.configure(filterConfig);</code>.</p>
     * <p>Once parent has been initialized, <code>getModelProvider()</code> returns a JeeConfig.</p>
     * @param filterConfig filter config
     * @throws ServletException in case of error
     */
    @Override
    public void init(FilterConfig filterConfig) throws ServletException
    {
        super.init(filterConfig);
        realm = Optional.ofNullable(findConfigParameter(REALM)).orElse("");
        String protectedResourcesPattern = findConfigParameter(PROTECTED_RESOURCES);
        if (protectedResourcesPattern != null)
        {
            try
            {
                protectedResources = Pattern.compile(protectedResourcesPattern);
            }
            catch (PatternSyntaxException pse)
            {
                throw new ServletException("could not configure protected resources pattern", pse);
            }
        }
        String publicResourcesPattern = findConfigParameter(PUBLIC_RESOURCES);
        if (publicResourcesPattern != null)
        {
            if (protectedResourcesPattern != null)
            {
                throw new ServletException("configure auth.protected or auth.public, but not both");
            }
            try
            {
                publicResources = Pattern.compile(publicResourcesPattern);
            }
            catch (PatternSyntaxException pse)
            {
                throw new ServletException("could not configure protected resources pattern", pse);
            }
        }
    }

    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain chain) throws IOException, ServletException
    {
        HttpServletRequest request = (HttpServletRequest)servletRequest;
        HttpServletResponse response = (HttpServletResponse)servletResponse;

        if (isProtectedURI(request.getRequestURI()))
        {
            USER user = getAuthentifiedUser(request);
            if (user == null)
            {
                processForbiddenRequest(request, response, chain);
            }
            else
            {
                processProtectedRequest(user, request, response, chain);
            }
        }
        else
        {
            processPublicRequest(request, response, chain);
        }
    }

    @Override
    public void destroy()
    {

    }

    /**
     * <p>What to display in the logs for a user, like in the logs.</p>
     * <p>A child class can change it to return something like
     * <code>return user.getString('login');</code>.</p>
     * @param user target user
     * @return string representation for the user
     */
    protected String displayUser(USER user)
    {
        // user.getString
        return String.valueOf(user);
    }

    protected final String getRealm()
    {
        return realm;
    }

    private String realm = null;
    private Pattern protectedResources = null;
    private Pattern publicResources = null;

    // for oauth
    // ...
}
