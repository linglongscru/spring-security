/*
 * Copyright 2012-2017 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.security.oauth2.oidc.client.authentication;

import org.springframework.security.jwt.Jwt;
import org.springframework.security.jwt.JwtDecoder;
import org.springframework.security.oauth2.client.authentication.AuthorizationCodeAuthenticationToken;
import org.springframework.security.oauth2.client.authentication.AuthorizationGrantAuthenticator;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationException;
import org.springframework.security.oauth2.client.authentication.OAuth2ClientAuthenticationToken;
import org.springframework.security.oauth2.client.authentication.jwt.JwtDecoderRegistry;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.web.AuthorizationGrantTokenExchanger;
import org.springframework.security.oauth2.core.AccessToken;
import org.springframework.security.oauth2.core.endpoint.TokenResponse;
import org.springframework.security.oauth2.oidc.core.IdToken;
import org.springframework.security.oauth2.oidc.core.endpoint.OidcParameter;
import org.springframework.util.Assert;

/**
 * An implementation of an {@link AuthorizationGrantAuthenticator} that
 * <i>&quot;authenticates&quot;</i> an <i>authorization code grant</i> credential
 * against an OpenID Connect 1.0 Provider's <i>Token Endpoint</i>.
 *
 * @author Joe Grandja
 * @since 5.0
 */
public class OidcAuthorizationCodeAuthenticator implements AuthorizationGrantAuthenticator<AuthorizationCodeAuthenticationToken> {
	private final AuthorizationGrantTokenExchanger<AuthorizationCodeAuthenticationToken> authorizationCodeTokenExchanger;
	private final JwtDecoderRegistry jwtDecoderRegistry;

	public OidcAuthorizationCodeAuthenticator(
		AuthorizationGrantTokenExchanger<AuthorizationCodeAuthenticationToken> authorizationCodeTokenExchanger,
		JwtDecoderRegistry jwtDecoderRegistry) {

		Assert.notNull(authorizationCodeTokenExchanger, "authorizationCodeTokenExchanger cannot be null");
		Assert.notNull(jwtDecoderRegistry, "jwtDecoderRegistry cannot be null");
		this.authorizationCodeTokenExchanger = authorizationCodeTokenExchanger;
		this.jwtDecoderRegistry = jwtDecoderRegistry;
	}

	@Override
	public OAuth2ClientAuthenticationToken authenticate(
		AuthorizationCodeAuthenticationToken authorizationCodeAuthentication) throws OAuth2AuthenticationException {

		// Section 3.1.2.1 Authentication Request - http://openid.net/specs/openid-connect-core-1_0.html#AuthRequest
		// scope
		// 		REQUIRED. OpenID Connect requests MUST contain the "openid" scope value.
		//		If the openid scope value is not present, the behavior is entirely unspecified.
		if (!authorizationCodeAuthentication.getAuthorizationRequest().getScope().contains("openid")) {
			return null;
		}

		ClientRegistration clientRegistration = authorizationCodeAuthentication.getClientRegistration();

		TokenResponse tokenResponse =
			this.authorizationCodeTokenExchanger.exchange(authorizationCodeAuthentication);

		AccessToken accessToken = new AccessToken(tokenResponse.getTokenType(),
			tokenResponse.getTokenValue(), tokenResponse.getIssuedAt(),
			tokenResponse.getExpiresAt(), tokenResponse.getScope());

		if (!tokenResponse.getAdditionalParameters().containsKey(OidcParameter.ID_TOKEN)) {
			throw new IllegalArgumentException(
				"Missing (required) ID Token in Token Response for Client Registration: '" + clientRegistration.getRegistrationId() + "'");
		}

		JwtDecoder jwtDecoder = this.jwtDecoderRegistry.getJwtDecoder(clientRegistration);
		if (jwtDecoder == null) {
			throw new IllegalArgumentException("Unable to find a registered JwtDecoder for Client Registration: '" + clientRegistration.getRegistrationId() +
				"'. Check to ensure you have configured the JwkSet URI.");
		}
		Jwt jwt = jwtDecoder.decode((String)tokenResponse.getAdditionalParameters().get(OidcParameter.ID_TOKEN));
		IdToken idToken = new IdToken(jwt.getTokenValue(), jwt.getIssuedAt(), jwt.getExpiresAt(), jwt.getClaims());

		OidcClientAuthenticationToken oidcClientAuthentication =
			new OidcClientAuthenticationToken(clientRegistration, accessToken, idToken);
		oidcClientAuthentication.setDetails(authorizationCodeAuthentication.getDetails());

		return oidcClientAuthentication;
	}
}
