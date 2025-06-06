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
package ca.uhn.fhir.jpa.util;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.context.RuntimeResourceDefinition;
import ca.uhn.fhir.context.RuntimeSearchParam;
import ca.uhn.fhir.i18n.Msg;
import ca.uhn.fhir.jpa.searchparam.extractor.BaseSearchParamExtractor;
import ca.uhn.fhir.jpa.searchparam.extractor.ISearchParamExtractor;
import ca.uhn.fhir.rest.api.RestSearchParameterTypeEnum;
import ca.uhn.fhir.rest.server.exceptions.MethodNotAllowedException;
import jakarta.annotation.Nonnull;
import org.apache.commons.lang3.StringUtils;
import org.hl7.fhir.instance.model.api.IBaseReference;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.IdType;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static ca.uhn.fhir.util.ObjectUtil.castIfInstanceof;
import static org.apache.commons.lang3.StringUtils.isBlank;

public class ResourceCompartmentUtil {

	/**
	 * Extract, if exists, the patient compartment identity of the received resource.
	 * It must be invoked in patient compartment mode.
	 * @param theResource             the resource to which extract the patient compartment identity
	 * @param theFhirContext          the active FhirContext
	 * @param theSearchParamExtractor the configured search parameter extractor
	 * @return the optional patient compartment identifier
	 * @throws MethodNotAllowedException if received resource is of type "Patient" and ID is not assigned.
	 */
	public static Optional<String> getPatientCompartmentIdentity(
			IBaseResource theResource, FhirContext theFhirContext, ISearchParamExtractor theSearchParamExtractor) {
		if (theResource == null) {
			// The resource may be null in mass ingestion mode
			return Optional.empty();
		}

		RuntimeResourceDefinition resourceDef = theFhirContext.getResourceDefinition(theResource);
		List<RuntimeSearchParam> patientCompartmentSps =
				ResourceCompartmentUtil.getPatientCompartmentSearchParams(resourceDef);
		if (patientCompartmentSps.isEmpty()) {
			return Optional.empty();
		}

		if (resourceDef.getName().equals("Patient")) {
			String compartmentIdentity = theResource.getIdElement().getIdPart();
			if (isBlank(compartmentIdentity)) {
				throw new MethodNotAllowedException(
						Msg.code(2475)
								+ "Patient resource IDs must be client-assigned in patient compartment mode, or server id strategy must be UUID");
			}
			return Optional.of(compartmentIdentity);
		}

		return getResourceCompartment("Patient", theResource, patientCompartmentSps, theSearchParamExtractor);
	}

	/**
	 * Extracts and returns an optional compartment of the received resource
	 * @param theCompartmentName     the name of the compartment
	 * @param theResource            source resource which compartment is extracted
	 * @param theCompartmentSps      the RuntimeSearchParam list involving the searched compartment
	 * @param mySearchParamExtractor the ISearchParamExtractor to be used to extract the parameter values
	 * @return optional compartment of the received resource
	 */
	public static Optional<String> getResourceCompartment(
			String theCompartmentName,
			IBaseResource theResource,
			List<RuntimeSearchParam> theCompartmentSps,
			ISearchParamExtractor mySearchParamExtractor) {
		// TODO KHS consolidate with FhirTerser.getCompartmentOwnersForResource()
		return getResourceCompartmentReferences(theResource, theCompartmentSps, mySearchParamExtractor)
				.map(t -> t.getReferenceElement().getValue())
				.map(IdType::new)
				.filter(t -> theCompartmentName.equals(
						t.getResourceType())) // assume the compartment name matches the resource type
				.map(IdType::getIdPart)
				.filter(StringUtils::isNotBlank)
				.findFirst();
	}

	@Nonnull
	public static Stream<IBaseReference> getResourceCompartmentReferences(
			IBaseResource theResource,
			List<RuntimeSearchParam> theCompartmentSps,
			ISearchParamExtractor mySearchParamExtractor) {
		return theCompartmentSps.stream()
				.flatMap(param -> Arrays.stream(BaseSearchParamExtractor.splitPathsR4(param.getPath())))
				.filter(StringUtils::isNotBlank)
				.flatMap(path -> mySearchParamExtractor.getPathValueExtractor(theResource, path).get().stream())
				.flatMap(base -> castIfInstanceof(base, IBaseReference.class).stream());
	}

	/**
	 * Returns a {@code RuntimeSearchParam} list with the parameters extracted from the received
	 * {@code RuntimeResourceDefinition}, which are of type REFERENCE and have a membership compartment
	 * for "Patient" resource
	 * @param resourceDef the RuntimeResourceDefinition providing the RuntimeSearchParam list
	 * @return the RuntimeSearchParam filtered list
	 */
	@Nonnull
	public static List<RuntimeSearchParam> getPatientCompartmentSearchParams(RuntimeResourceDefinition resourceDef) {
		return resourceDef.getSearchParams().stream()
				.filter(param -> param.getParamType() == RestSearchParameterTypeEnum.REFERENCE)
				.filter(param -> param.getProvidesMembershipInCompartments() != null
						&& param.getProvidesMembershipInCompartments().contains("Patient"))
				.collect(Collectors.toList());
	}
}
