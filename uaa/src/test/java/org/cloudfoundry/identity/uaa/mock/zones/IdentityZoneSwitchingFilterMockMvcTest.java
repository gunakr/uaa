/*******************************************************************************
 *     Cloud Foundry
 *     Copyright (c) [2009-2014] Pivotal Software, Inc. All Rights Reserved.
 *
 *     This product is licensed to you under the Apache License, Version 2.0 (the "License").
 *     You may not use this product except in compliance with the License.
 *
 *     This product includes a number of subcomponents with
 *     separate copyright notices and license terms. Your use of these
 *     subcomponents is subject to the terms and conditions of the
 *     subcomponent's license, as noted in the LICENSE file.
 *******************************************************************************/
package org.cloudfoundry.identity.uaa.mock.zones;

import java.util.Arrays;
import java.util.UUID;

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.lang.RandomStringUtils;
import org.cloudfoundry.identity.uaa.mock.util.MockMvcUtils;
import org.cloudfoundry.identity.uaa.scim.ScimGroup;
import org.cloudfoundry.identity.uaa.scim.ScimGroupMember;
import org.cloudfoundry.identity.uaa.scim.ScimUser;
import org.cloudfoundry.identity.uaa.test.TestClient;
import org.cloudfoundry.identity.uaa.test.YamlServletProfileInitializerContextInitializer;
import org.cloudfoundry.identity.uaa.util.SetServerNameRequestPostProcessor;
import org.cloudfoundry.identity.uaa.zone.IdentityZone;
import org.cloudfoundry.identity.uaa.zone.IdentityZoneSwitchingFilter;
import org.codehaus.jackson.map.ObjectMapper;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.springframework.mock.web.MockServletContext;
import org.springframework.security.oauth2.provider.client.BaseClientDetails;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultMatcher;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.support.XmlWebApplicationContext;

public class IdentityZoneSwitchingFilterMockMvcTest {

    private static XmlWebApplicationContext webApplicationContext;
    private static MockMvc mockMvc;
    private static TestClient testClient;
    private static String identityToken;
    private static String adminToken;

    @BeforeClass
    public static void setUp() throws Exception {
        webApplicationContext = new XmlWebApplicationContext();
        webApplicationContext.setServletContext(new MockServletContext());
        new YamlServletProfileInitializerContextInitializer().initializeContext(webApplicationContext, "uaa.yml,login.yml");
        webApplicationContext.setConfigLocation("file:./src/main/webapp/WEB-INF/spring-servlet.xml");
        webApplicationContext.refresh();
        FilterChainProxy springSecurityFilterChain = webApplicationContext.getBean("springSecurityFilterChain", FilterChainProxy.class);

        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).addFilter(springSecurityFilterChain)
                .build();

        testClient = new TestClient(mockMvc);
        identityToken = testClient.getClientCredentialsOAuthAccessToken(
                "identity",
                "identitysecret",
                "zones.create,scim.zones");

        adminToken = testClient.getClientCredentialsOAuthAccessToken(
            "admin",
            "adminsecret",
            "");
    }

    @AfterClass
    public static void tearDown() throws Exception {
        webApplicationContext.close();
    }

    @Test
    public void testSwitchingZones() throws Exception {

        final String zoneId = createZone(identityToken);
        String zoneAdminToken = MockMvcUtils.utils().getZoneAdminToken(mockMvc,adminToken, zoneId);
        // Using Identity Client, authenticate in originating Zone
        // - Create Client using X-Identity-Zone-Id header in new Zone
        final String clientId = UUID.randomUUID().toString();
        BaseClientDetails client = new BaseClientDetails(clientId, null, null, "client_credentials", null);
        client.setClientSecret("secret");
        mockMvc.perform(post("/oauth/clients")
                .header(IdentityZoneSwitchingFilter.HEADER, zoneId)
                .header("Authorization", "Bearer " + zoneAdminToken)
                .accept(APPLICATION_JSON)
                .contentType(APPLICATION_JSON)
                .content(new ObjectMapper().writeValueAsString(client)))
            .andExpect(status().isCreated());

        // Authenticate with new Client in new Zone
        mockMvc.perform(get("/oauth/token?grant_type=client_credentials")
                .header("Authorization", "Basic "
                        + new String(Base64.encodeBase64((client.getClientId() + ":" + client.getClientSecret()).getBytes())))
                .with(new SetServerNameRequestPostProcessor(zoneId + ".localhost")))
                .andExpect(status().isOk());
    }

    @Test
    public void testSwitchingToNonExistentZone() throws Exception {
        createClientInOtherZone(identityToken, "i-do-not-exist", status().isForbidden());
    }

    @Test
    public void testSwitchingZonesWithoutAuthority() throws Exception {
        String identityTokenWithoutZonesAdmin = testClient.getClientCredentialsOAuthAccessToken("identity","identitysecret","zones.create,scim.zones");
        final String zoneId = createZone(identityTokenWithoutZonesAdmin);
        createClientInOtherZone(identityTokenWithoutZonesAdmin, zoneId, status().isForbidden());
    }

    @Test
    public void testSwitchingZonesWithAUser() throws Exception {
        final String zoneId = createZone(identityToken);
        String adminToken = testClient.getClientCredentialsOAuthAccessToken("admin","adminsecret","scim.write");
        // Create a User
        String username = RandomStringUtils.randomAlphabetic(8) + "@example.com";
        ScimUser user = new ScimUser();
        user.setUserName(username);
        user.addEmail(username);
        user.setPassword("secret");
        user.setVerified(true);
        user.setZoneId(IdentityZone.getUaa().getId());
        ScimUser createdUser = MockMvcUtils.utils().createUser(mockMvc, adminToken, user);
        // Create the zones.<zone_id>.admin Group
        // Add User to the zones.<zone_id>.admin Group
        ScimGroup group = new ScimGroup("zones." + zoneId + ".admin");
        group.setMembers(Arrays.asList(new ScimGroupMember(createdUser.getId())));
        group = MockMvcUtils.utils().createGroup(mockMvc, adminToken, group);
        // Add User to the clients.create Group
        String userToken = testClient.getUserOAuthAccessToken("identity", "identitysecret", createdUser.getUserName(), "secret", "zones." + zoneId + ".admin");
        createClientInOtherZone(userToken, zoneId, status().isCreated());
    }

    private String createZone(String accessToken) throws Exception {
        return MockMvcUtils.utils().createZoneUsingWebRequest(mockMvc, accessToken).getId();
    }

    private void createClientInOtherZone(String accessToken, String zoneId, ResultMatcher statusMatcher) throws Exception {
        final String clientId = UUID.randomUUID().toString();
        BaseClientDetails client = new BaseClientDetails(clientId, null, null, "client_credentials", null);
        client.setClientSecret("secret");
        mockMvc.perform(post("/oauth/clients")
            .header(IdentityZoneSwitchingFilter.HEADER, zoneId)
            .header("Authorization", "Bearer " + accessToken)
            .accept(APPLICATION_JSON)
            .contentType(APPLICATION_JSON)
            .content(new ObjectMapper().writeValueAsString(client)))
            .andExpect(statusMatcher);
    }
}
