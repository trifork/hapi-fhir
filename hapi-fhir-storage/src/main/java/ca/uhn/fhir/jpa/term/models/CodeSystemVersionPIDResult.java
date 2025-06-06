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
package ca.uhn.fhir.jpa.term.models;

import ca.uhn.fhir.model.api.IModelJson;
import com.fasterxml.jackson.annotation.JsonProperty;

public class CodeSystemVersionPIDResult implements IModelJson {

	@JsonProperty("partitionId")
	private Integer myPartitionId;

	@JsonProperty("codeSystemVersionPID")
	private long myCodeSystemVersionPID;

	public Integer getPartitionId() {
		return myPartitionId;
	}

	public void setPartitionId(Integer thePartitionId) {
		myPartitionId = thePartitionId;
	}

	public long getCodeSystemVersionPID() {
		return myCodeSystemVersionPID;
	}

	public void setCodeSystemVersionPID(long theCodeSystemVersionPID) {
		myCodeSystemVersionPID = theCodeSystemVersionPID;
	}
}
