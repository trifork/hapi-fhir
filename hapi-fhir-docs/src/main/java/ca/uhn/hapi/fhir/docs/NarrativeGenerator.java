/*-
 * #%L
 * HAPI FHIR - Docs
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
package ca.uhn.hapi.fhir.docs;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.model.dstu2.resource.Patient;
import ca.uhn.fhir.narrative.CustomThymeleafNarrativeGenerator;

@SuppressWarnings("unused")
public class NarrativeGenerator {

	public void testGenerator() {

		// START SNIPPET: gen
		FhirContext ctx = FhirContext.forDstu2();
		String propFile = "classpath:/com/foo/customnarrative.properties";
		CustomThymeleafNarrativeGenerator gen = new CustomThymeleafNarrativeGenerator(propFile);

		Patient patient = new Patient();

		ctx.setNarrativeGenerator(gen);
		String output = ctx.newJsonParser().encodeResourceToString(patient);
		System.out.println(output);
		// END SNIPPET: gen

	}
}
