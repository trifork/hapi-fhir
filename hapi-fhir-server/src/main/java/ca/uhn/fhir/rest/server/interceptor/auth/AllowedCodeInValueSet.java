/*-
 * #%L
 * HAPI FHIR - Server Framework
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
package ca.uhn.fhir.rest.server.interceptor.auth;

import jakarta.annotation.Nonnull;

import static org.apache.commons.lang3.StringUtils.isNotBlank;

class AllowedCodeInValueSet {
	private final String myResourceName;
	private final String mySearchParameterName;
	private final String myValueSetUrl;
	private final boolean myNegate;

	public AllowedCodeInValueSet(
			@Nonnull String theResourceName,
			@Nonnull String theSearchParameterName,
			@Nonnull String theValueSetUrl,
			boolean theNegate) {
		assert isNotBlank(theResourceName);
		assert isNotBlank(theSearchParameterName);
		assert isNotBlank(theValueSetUrl);

		myResourceName = theResourceName;
		mySearchParameterName = theSearchParameterName;
		myValueSetUrl = theValueSetUrl;
		myNegate = theNegate;
	}

	public String getResourceName() {
		return myResourceName;
	}

	public String getSearchParameterName() {
		return mySearchParameterName;
	}

	public String getValueSetUrl() {
		return myValueSetUrl;
	}

	public boolean isNegate() {
		return myNegate;
	}
}
