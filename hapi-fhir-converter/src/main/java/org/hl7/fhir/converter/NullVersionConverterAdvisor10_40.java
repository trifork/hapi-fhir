/*
 * #%L
 * HAPI FHIR - Converter
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
package org.hl7.fhir.converter;

import jakarta.annotation.Nullable;
import org.hl7.fhir.convertors.advisors.impl.BaseAdvisor_10_40;
import org.hl7.fhir.exceptions.FHIRException;
import org.hl7.fhir.r4.model.CodeSystem;
import org.hl7.fhir.r4.model.ValueSet;

public class NullVersionConverterAdvisor10_40 extends BaseAdvisor_10_40 {

	@Nullable
	@Override
	public CodeSystem getCodeSystem(@Nullable ValueSet theValueSet) throws FHIRException {
		return null;
	}

	@Override
	public void handleCodeSystem(@Nullable CodeSystem theCodeSystem, @Nullable ValueSet theValueSet)
			throws FHIRException {
		// nothing
	}
}
