/*-
 * #%L
 * HAPI FHIR Storage api
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
package ca.uhn.fhir.jpa.subscription.triggering;

import ca.uhn.fhir.rest.api.server.RequestDetails;
import jakarta.annotation.Nullable;
import org.hl7.fhir.instance.model.api.IBaseParameters;
import org.hl7.fhir.instance.model.api.IIdType;
import org.hl7.fhir.instance.model.api.IPrimitiveType;

import java.util.List;

public interface ISubscriptionTriggeringSvc {

	IBaseParameters triggerSubscription(
			@Nullable List<IPrimitiveType<String>> theResourceIds,
			@Nullable List<IPrimitiveType<String>> theSearchUrls,
			@Nullable IIdType theSubscriptionId,
			RequestDetails theRequestDetails);

	@Deprecated(forRemoval = true)
	/**
	 * Use {@link ISubscriptionTriggeringSvc#triggerSubscription(List, List, IIdType, RequestDetails)} instead.
	 * This implementation uses a SystemRequestDetails for All Partitions, as the previous behaviour did.
	 */
	IBaseParameters triggerSubscription(
			@Nullable List<IPrimitiveType<String>> theResourceIds,
			@Nullable List<IPrimitiveType<String>> theSearchUrls,
			@Nullable IIdType theSubscriptionId);

	void runDeliveryPass();
}
