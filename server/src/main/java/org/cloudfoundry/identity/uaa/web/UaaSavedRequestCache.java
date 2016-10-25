/*
 * ****************************************************************************
 *     Cloud Foundry
 *     Copyright (c) [2009-2016] Pivotal Software, Inc. All Rights Reserved.
 *
 *     This product is licensed to you under the Apache License, Version 2.0 (the "License").
 *     You may not use this product except in compliance with the License.
 *
 *     This product includes a number of subcomponents with
 *     separate copyright notices and license terms. Your use of these
 *     subcomponents is subject to the terms and conditions of the
 *     subcomponent's license, as noted in the LICENSE file.
 * ****************************************************************************
 */

package org.cloudfoundry.identity.uaa.web;


import org.springframework.http.HttpMethod;
import org.springframework.security.web.savedrequest.HttpSessionRequestCache;
import org.springframework.security.web.savedrequest.SavedRequest;
import org.springframework.util.StringUtils;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.cloudfoundry.identity.uaa.web.UaaSavedRequestAwareAuthenticationSuccessHandler.FORM_REDIRECT_PARAMETER;
import static org.cloudfoundry.identity.uaa.web.UaaSavedRequestAwareAuthenticationSuccessHandler.SAVED_REQUEST_SESSION_ATTRIBUTE;

public class UaaSavedRequestCache extends HttpSessionRequestCache {

    @Override
    public void saveRequest(HttpServletRequest request, HttpServletResponse response) {
        if (shouldSaveFormRedirectParameter(request)) {
            saveFormParameter(request);
        } else {
            super.saveRequest(request, response);
        }
    }

    public void saveFormParameter(HttpServletRequest request) {
        HttpSession session = request.getSession(true);
        session.setAttribute(SAVED_REQUEST_SESSION_ATTRIBUTE, new ClientRedirectSavedRequest(request.getParameter(FORM_REDIRECT_PARAMETER)));
    }

    public boolean shouldSaveFormRedirectParameter(HttpServletRequest request) {
        String formRedirect = request.getParameter(FORM_REDIRECT_PARAMETER);
        if (StringUtils.isEmpty(formRedirect)) {
            return false;
        }

        if (request.getParameter(FORM_REDIRECT_PARAMETER)==null) {
            return false;
        }

        if (request.getSession(false)!=null &&
            request.getSession(false).getAttribute(SAVED_REQUEST_SESSION_ATTRIBUTE)!=null) {
            return false;
        }

        return true;

    }

    public static class ClientRedirectSavedRequest implements SavedRequest {

        private final String redirectUrl;

        public ClientRedirectSavedRequest(String redirectUrl) {
            this.redirectUrl = redirectUrl;
        }

        @Override
        public String getRedirectUrl() {
            return redirectUrl;
        }

        @Override
        public List<Cookie> getCookies() {
            return Collections.emptyList();
        }

        @Override
        public String getMethod() {
            return HttpMethod.GET.name();
        }

        @Override
        public List<String> getHeaderValues(String name) {
            return Collections.emptyList();
        }

        @Override
        public Collection<String> getHeaderNames() {
            return Collections.emptyList();
        }

        @Override
        public List<Locale> getLocales() {
            return Collections.emptyList();
        }

        @Override
        public String[] getParameterValues(String name) {
            return new String[0];
        }

        @Override
        public Map<String, String[]> getParameterMap() {
            return Collections.emptyMap();
        }
    }
}
