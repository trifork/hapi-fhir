/*-
 * #%L
 * HAPI FHIR - CDS Hooks
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
package ca.uhn.hapi.fhir.cdshooks.svc.prefetch;

import ca.uhn.fhir.i18n.Msg;
import ca.uhn.fhir.interceptor.api.HookParams;
import ca.uhn.fhir.interceptor.api.IInterceptorBroadcaster;
import ca.uhn.fhir.interceptor.api.Pointcut;
import ca.uhn.fhir.rest.api.server.cdshooks.CdsServiceRequestJson;
import ca.uhn.fhir.rest.server.exceptions.BaseServerResponseException;
import ca.uhn.fhir.rest.server.exceptions.PreconditionFailedException;
import ca.uhn.hapi.fhir.cdshooks.api.CdsResolutionStrategyEnum;
import ca.uhn.hapi.fhir.cdshooks.api.ICdsHooksDaoAuthorizationSvc;
import ca.uhn.hapi.fhir.cdshooks.api.ICdsServiceMethod;
import ca.uhn.hapi.fhir.cdshooks.api.json.CdsServiceJson;
import ca.uhn.hapi.fhir.cdshooks.api.json.prefetch.CdsHookPrefetchPointcutContextJson;
import jakarta.annotation.Nullable;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.Set;

@Service
public class CdsPrefetchSvc {
	private static final Logger ourLog = LoggerFactory.getLogger(CdsPrefetchSvc.class);
	private final CdsResolutionStrategySvc myCdsResolutionStrategySvc;
	private final CdsPrefetchDaoSvc myResourcePrefetchDao;
	private final CdsPrefetchFhirClientSvc myResourcePrefetchFhirClient;
	private final ICdsHooksDaoAuthorizationSvc myCdsHooksDaoAuthorizationSvc;

	@Nullable
	private final IInterceptorBroadcaster myInterceptorBroadcaster;

	public CdsPrefetchSvc(
			CdsResolutionStrategySvc theCdsResolutionStrategySvc,
			CdsPrefetchDaoSvc theResourcePrefetchDao,
			CdsPrefetchFhirClientSvc theResourcePrefetchFhirClient,
			ICdsHooksDaoAuthorizationSvc theCdsHooksDaoAuthorizationSvc,
			@Nullable IInterceptorBroadcaster theInterceptorBroadcaster) {
		myCdsResolutionStrategySvc = theCdsResolutionStrategySvc;
		myResourcePrefetchDao = theResourcePrefetchDao;
		myResourcePrefetchFhirClient = theResourcePrefetchFhirClient;
		myCdsHooksDaoAuthorizationSvc = theCdsHooksDaoAuthorizationSvc;
		myInterceptorBroadcaster = theInterceptorBroadcaster;
	}

	public void augmentRequest(CdsServiceRequestJson theCdsServiceRequestJson, ICdsServiceMethod theServiceMethod) {
		CdsServiceJson serviceSpec = theServiceMethod.getCdsServiceJson();
		Set<String> missingPrefetch = findMissingPrefetch(serviceSpec, theCdsServiceRequestJson);
		if (missingPrefetch.isEmpty()) {
			return;
		}
		Set<CdsResolutionStrategyEnum> strategies =
				myCdsResolutionStrategySvc.determineResolutionStrategy(theServiceMethod, theCdsServiceRequestJson);
		String serviceId = theServiceMethod.getCdsServiceJson().getId();
		try {
			fetchMissingPrefetchElements(theCdsServiceRequestJson, serviceSpec, missingPrefetch, strategies);
		} catch (BaseServerResponseException e) {
			// Per the CDS Hooks specification
			throw new PreconditionFailedException(Msg.code(2385) + "Unable to fetch missing resource(s) with key(s) "
					+ missingPrefetch + " for CDS Hooks service " + serviceId + ": " + e.getMessage());
		}
	}

	private void fetchMissingPrefetchElements(
			CdsServiceRequestJson theCdsServiceRequestJson,
			CdsServiceJson theServiceSpec,
			Set<String> theMissingPrefetch,
			Set<CdsResolutionStrategyEnum> theStrategies) {
		for (String key : theMissingPrefetch) {
			String template = theServiceSpec.getPrefetch().get(key);
			CdsResolutionStrategyEnum source = theServiceSpec.getSource().get(key);
			if (!theStrategies.contains(source)) {
				throw new PreconditionFailedException(
						Msg.code(2386) + "Unable to fetch missing resource(s) with source " + source);
			}
			if (source == CdsResolutionStrategyEnum.NONE) {
				if (theStrategies.contains(CdsResolutionStrategyEnum.FHIR_CLIENT)) {
					source = CdsResolutionStrategyEnum.FHIR_CLIENT;
				} else if (theStrategies.contains(CdsResolutionStrategyEnum.SERVICE)) {
					source = CdsResolutionStrategyEnum.SERVICE;
				} else if (theStrategies.contains(CdsResolutionStrategyEnum.DAO)) {
					source = CdsResolutionStrategyEnum.DAO;
				} else {
					// Per the CDS Hooks specification
					throw new PreconditionFailedException(
							Msg.code(2387) + "Unable to fetch missing resource(s) with source " + source);
				}
			}

			if (source == CdsResolutionStrategyEnum.SERVICE) {
				// The service will manage missing prefetch elements
				continue;
			}
			String url = PrefetchTemplateUtil.substituteTemplate(
					template, theCdsServiceRequestJson.getContext(), myResourcePrefetchDao.getFhirContext());
			ourLog.info("missing: {}.  Fetching with {}", theMissingPrefetch, url);
			IBaseResource resource;

			CdsHookPrefetchPointcutContextJson cdsHookPrefetchContext = new CdsHookPrefetchPointcutContextJson();
			cdsHookPrefetchContext.setTemplate(template);
			cdsHookPrefetchContext.setQuery(url);
			cdsHookPrefetchContext.setCdsResolutionStrategy(source);

			callCdsPrefetchRequestHooks(cdsHookPrefetchContext, theCdsServiceRequestJson);

			try {
				if (source == CdsResolutionStrategyEnum.FHIR_CLIENT) {
					resource = myResourcePrefetchFhirClient.resourceFromUrl(theCdsServiceRequestJson, url);
				} else if (source == CdsResolutionStrategyEnum.DAO) {
					resource = getResourceFromDaoWithPermissionCheck(url);
				} else {
					// should never happen
					throw new IllegalStateException(Msg.code(2388) + "Unexpected strategy " + theStrategies);
				}
			} catch (Exception e) {
				callCdsPrefetchFailedHooks(cdsHookPrefetchContext, theCdsServiceRequestJson, e);
				throw e;
			}

			callCdsPrefetchResponseHooks(cdsHookPrefetchContext, theCdsServiceRequestJson, resource);

			theCdsServiceRequestJson.addPrefetch(key, resource);
		}
	}

	private IBaseResource getResourceFromDaoWithPermissionCheck(String theUrl) {
		IBaseResource resource;
		resource = myResourcePrefetchDao.resourceFromUrl(theUrl);
		myCdsHooksDaoAuthorizationSvc.authorizePreShow(resource);
		return resource;
	}

	public Set<String> findMissingPrefetch(
			CdsServiceJson theServiceSpec, CdsServiceRequestJson theCdsServiceRequestJson) {
		Set<String> expectedPrefetchKeys = theServiceSpec.getPrefetch().keySet();
		Set<String> actualPrefetchKeys = theCdsServiceRequestJson.getPrefetchKeys();
		Set<String> retval = new HashSet<>(expectedPrefetchKeys);
		retval.removeAll(actualPrefetchKeys);
		return retval;
	}

	private void callCdsPrefetchRequestHooks(
			CdsHookPrefetchPointcutContextJson theCdsHookPrefetchContext,
			CdsServiceRequestJson theCdsServiceRequestJson) {
		if (myInterceptorBroadcaster != null && myInterceptorBroadcaster.hasHooks(Pointcut.CDS_HOOK_PREFETCH_REQUEST)) {
			HookParams params = new HookParams();
			params.add(CdsHookPrefetchPointcutContextJson.class, theCdsHookPrefetchContext);
			params.add(CdsServiceRequestJson.class, theCdsServiceRequestJson);
			myInterceptorBroadcaster.callHooks(Pointcut.CDS_HOOK_PREFETCH_REQUEST, params);
		}
	}

	private void callCdsPrefetchResponseHooks(
			CdsHookPrefetchPointcutContextJson theCdsHookPrefetchContext,
			CdsServiceRequestJson theCdsServiceRequestJson,
			IBaseResource theResource) {
		if (myInterceptorBroadcaster != null
				&& myInterceptorBroadcaster.hasHooks(Pointcut.CDS_HOOK_PREFETCH_RESPONSE)) {
			HookParams params = new HookParams();
			params.add(CdsHookPrefetchPointcutContextJson.class, theCdsHookPrefetchContext);
			params.add(CdsServiceRequestJson.class, theCdsServiceRequestJson);
			params.add(IBaseResource.class, theResource);

			myInterceptorBroadcaster.callHooks(Pointcut.CDS_HOOK_PREFETCH_RESPONSE, params);
		}
	}

	private void callCdsPrefetchFailedHooks(
			CdsHookPrefetchPointcutContextJson theCdsHookPrefetchContext,
			CdsServiceRequestJson theCdsServiceRequestJson,
			Exception theException) {
		if (myInterceptorBroadcaster != null && myInterceptorBroadcaster.hasHooks(Pointcut.CDS_HOOK_PREFETCH_FAILED)) {
			HookParams params = new HookParams();
			params.add(CdsHookPrefetchPointcutContextJson.class, theCdsHookPrefetchContext);
			params.add(CdsServiceRequestJson.class, theCdsServiceRequestJson);
			params.add(Exception.class, theException);

			myInterceptorBroadcaster.callHooks(Pointcut.CDS_HOOK_PREFETCH_FAILED, params);
		}
	}
}
