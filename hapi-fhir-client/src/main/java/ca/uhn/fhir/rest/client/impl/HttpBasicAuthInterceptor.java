/*
 * #%L
 * HAPI FHIR - Client Framework
 * %%
 * Copyright (C) 2014 - 2024 Smile CDR, Inc.
 * %%
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
 * #L%
 */
package ca.uhn.fhir.rest.client.impl;

import org.apache.hc.client5.http.auth.AuthCache;
import org.apache.hc.client5.http.auth.AuthScope;
import org.apache.hc.client5.http.auth.Credentials;
import org.apache.hc.client5.http.auth.UsernamePasswordCredentials;
import org.apache.hc.client5.http.impl.auth.BasicAuthCache;
import org.apache.hc.client5.http.impl.auth.BasicCredentialsProvider;
import org.apache.hc.client5.http.impl.auth.BasicScheme;
import org.apache.hc.client5.http.protocol.HttpClientContext;
import org.apache.hc.core5.http.EntityDetails;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.HttpRequestInterceptor;
import org.apache.hc.core5.http.protocol.HttpContext;

import java.io.IOException;
import java.net.URISyntaxException;

/**
 * Apache HTTPClient interceptor which adds basic auth
 *
 * @see ca.uhn.fhir.rest.client.interceptor.BasicAuthInterceptor A HAPI FHIR interceptor that is generally easier to use
 */
public class HttpBasicAuthInterceptor implements HttpRequestInterceptor {

	private String myUsername;
	private String myPassword;

	public HttpBasicAuthInterceptor(String theUsername, String thePassword) {
		super();
		myUsername = theUsername;
		myPassword = thePassword;
	}

	@Override
	public void process(final HttpRequest request, final EntityDetails entity, final HttpContext context) throws HttpException, IOException {
		HttpClientContext clientContext = HttpClientContext.castOrCreate(context);

		// Parse target host from the request
		HttpHost targetHost;
		try {
			String scheme = request.getUri().getScheme();
			String host = request.getUri().getHost();
			int port = request.getUri().getPort();

			if (host == null) {
				throw new HttpException("Unable to determine target host for the request.");
			}

			targetHost = new HttpHost(scheme, host, port);
		} catch (URISyntaxException e) {
			throw new RuntimeException(e);
		}

		AuthCache authCache = clientContext.getAuthCache();
		if (authCache == null) {
			authCache = new BasicAuthCache();
			clientContext.setAuthCache(authCache);
		}

		Credentials credentials = new UsernamePasswordCredentials(myUsername, myPassword.toCharArray());
		authCache.put(targetHost, new BasicScheme());

		BasicCredentialsProvider credentialsProvider = new BasicCredentialsProvider();
		credentialsProvider.setCredentials(new AuthScope(targetHost), credentials);
		clientContext.setCredentialsProvider(credentialsProvider);
	}
}
