package ca.uhn.fhir.validation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class ResultSeverityEnumTest {

	@Test
	public void testOrdinals() {
		assertEquals(0, ResultSeverityEnum.INFORMATION.ordinal());
		assertEquals(1, ResultSeverityEnum.WARNING.ordinal());
		assertEquals(2, ResultSeverityEnum.ERROR.ordinal());
		assertEquals(3, ResultSeverityEnum.FATAL.ordinal());
		
	}
	
}
