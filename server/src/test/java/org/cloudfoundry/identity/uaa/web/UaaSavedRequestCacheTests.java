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

import org.junit.Before;
import org.junit.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.web.savedrequest.SavedRequest;
import org.springframework.security.web.util.matcher.RequestMatcher;

import javax.servlet.http.HttpSession;

import static org.cloudfoundry.identity.uaa.web.UaaSavedRequestAwareAuthenticationSuccessHandler.FORM_REDIRECT_PARAMETER;
import static org.cloudfoundry.identity.uaa.web.UaaSavedRequestAwareAuthenticationSuccessHandler.SAVED_REQUEST_SESSION_ATTRIBUTE;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.anyObject;
import static org.mockito.Matchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.http.HttpMethod.POST;

public class UaaSavedRequestCacheTests {

    private UaaSavedRequestCache cache;
    private RequestMatcher matcher;
    private UaaSavedRequestCache spy;
    private MockHttpSession session;
    private MockHttpServletRequest request;
    private String redirectUri;

    @Before
    public void setup() {
        cache = new UaaSavedRequestCache();
        matcher = mock(RequestMatcher.class);
        when(matcher.matches(anyObject())).thenReturn(false);
        cache.setRequestMatcher(matcher);
        spy = spy(cache);
        session = new MockHttpSession();
        request = new MockHttpServletRequest(POST.name(), "/login.do");
        redirectUri = "http://test";
    }


    @Test
    public void saveFormRedirectRequest() throws Exception {
        request.setSession(session);
        request.setParameter(FORM_REDIRECT_PARAMETER, "http://login");
        spy.saveRequest(request, new MockHttpServletResponse());
        verify(spy).saveFormParameter(request);
    }

    @Test
    public void do_not_save_form() throws Exception {
        request.setSession(session);
        spy.saveRequest(request, new MockHttpServletResponse());
        verify(spy, never()).saveFormParameter(request);
        verify(matcher, times(1)).matches(same(request));
    }


    @Test
    public void should_save_condition_works() {
        assertFalse(cache.shouldSaveFormRedirectParameter(request));
        request.setPathInfo("/login.do");
        assertFalse(cache.shouldSaveFormRedirectParameter(request));
        request.setParameter(FORM_REDIRECT_PARAMETER, redirectUri);
        assertTrue(cache.shouldSaveFormRedirectParameter(request));
        request.setSession(session);
        assertTrue(cache.shouldSaveFormRedirectParameter(request));
        UaaSavedRequestCache.ClientRedirectSavedRequest savedRequest = new UaaSavedRequestCache.ClientRedirectSavedRequest(redirectUri);
        session.setAttribute(SAVED_REQUEST_SESSION_ATTRIBUTE, savedRequest);
        assertFalse(cache.shouldSaveFormRedirectParameter(request));
    }

    @Test
    public void save_returns_correct_object() {
        request.setParameter(FORM_REDIRECT_PARAMETER, redirectUri);
        cache.saveFormParameter(request);
        HttpSession session = request.getSession(false);
        assertNotNull(session);
        SavedRequest savedRequest = (SavedRequest) session.getAttribute(SAVED_REQUEST_SESSION_ATTRIBUTE);
        assertNotNull(savedRequest);
        assertEquals(redirectUri, savedRequest.getRedirectUrl());
        assertEquals(GET.name(), savedRequest.getMethod());

    }


}