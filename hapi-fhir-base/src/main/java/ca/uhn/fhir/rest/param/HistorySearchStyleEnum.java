package ca.uhn.fhir.rest.param;

/*
 * #%L
 * HAPI FHIR - Core Library
 * %%
 * Copyright (C) 2014 - 2022 Smile CDR, Inc.
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

import ca.uhn.fhir.rest.api.Constants;

import java.util.Arrays;

public enum HistorySearchStyleEnum {
	AT(Constants.PARAM_AT),
	SINCE(Constants.PARAM_SINCE),
	COUNT(Constants.PARAM_COUNT);

	public String getValue() {
		return myValue;
	}

	private final String myValue;

	HistorySearchStyleEnum(String theValue) {
		this.myValue = theValue;
	}

	public static HistorySearchStyleEnum parse(String value){
		return Arrays.stream(HistorySearchStyleEnum.values())
			.filter(type -> type.myValue.equals(value)).findAny().orElse(null);
	}

	public boolean isAt(){
		return this == HistorySearchStyleEnum.AT;
	}
}