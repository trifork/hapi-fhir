/*-
 * #%L
 * HAPI FHIR Subscription Server
 * %%
 * Copyright (C) 2014 - 2025 Smile CDR, Inc.
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
package ca.uhn.fhir.jpa.topic;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.jpa.api.dao.DaoRegistry;
import ca.uhn.fhir.jpa.searchparam.matcher.SearchParamMatcher;
import ca.uhn.fhir.jpa.subscription.config.SubscriptionConfig;
import ca.uhn.fhir.jpa.subscription.submit.interceptor.validator.SubscriptionQueryValidator;
import ca.uhn.fhir.jpa.util.MemoryCacheService;
import ca.uhn.fhir.rest.server.util.ISearchParamRegistry;
import ca.uhn.hapi.converters.canonical.VersionCanonicalizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Lazy;

@Configuration
@Import(SubscriptionConfig.class)
public class SubscriptionTopicConfig {
	@Bean
	public SubscriptionTopicMatchingListener subscriptionTopicMatchingSubscriber(
			FhirContext theFhirContext, MemoryCacheService memoryCacheService) {
		switch (theFhirContext.getVersion().getVersion()) {
			case R4:
			case R5:
			case R4B:
				return new SubscriptionTopicMatchingListener(theFhirContext, memoryCacheService);
			default:
				return null;
		}
	}

	@Bean
	@Lazy
	public SubscriptionTopicRegistry subscriptionTopicRegistry() {
		return new SubscriptionTopicRegistry();
	}

	@Bean
	@Lazy
	public SubscriptionTopicSupport subscriptionTopicSupport(
			FhirContext theFhirContext, DaoRegistry theDaoRegistry, SearchParamMatcher theSearchParamMatcher) {
		return new SubscriptionTopicSupport(theFhirContext, theDaoRegistry, theSearchParamMatcher);
	}

	@Bean
	public ISubscriptionTopicLoader subscriptionTopicLoader(
			FhirContext theFhirContext,
			SubscriptionTopicRegistry theSubscriptionTopicRegistry,
			ISearchParamRegistry theSearchParamRegistry) {
		VersionCanonicalizer versionCanonicalizer = new VersionCanonicalizer(theFhirContext);
		switch (theFhirContext.getVersion().getVersion()) {
			case R4:
				return new R4SubscriptionTopicLoader(
						versionCanonicalizer, theSubscriptionTopicRegistry, theSearchParamRegistry);
			case R5:
			case R4B:
				return new SubscriptionTopicLoader(
						versionCanonicalizer, theSubscriptionTopicRegistry, theSearchParamRegistry);
			default:
				return null;
		}
	}

	@Bean
	public SubscriptionTopicRegisteringListener subscriptionTopicRegisteringSubscriber(FhirContext theFhirContext) {
		switch (theFhirContext.getVersion().getVersion()) {
			case R5:
			case R4B:
				return new SubscriptionTopicRegisteringListener();
			default:
				return null;
		}
	}

	@Bean
	public SubscriptionTopicValidatingInterceptor subscriptionTopicValidatingInterceptor(
			FhirContext theFhirContext, SubscriptionQueryValidator theSubscriptionQueryValidator) {
		switch (theFhirContext.getVersion().getVersion()) {
			case R5:
			case R4B:
				return new SubscriptionTopicValidatingInterceptor(theFhirContext, theSubscriptionQueryValidator);
			default:
				return null;
		}
	}
}
