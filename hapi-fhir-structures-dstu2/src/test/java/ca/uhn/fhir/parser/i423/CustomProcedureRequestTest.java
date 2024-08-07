package ca.uhn.fhir.parser.i423;


import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.model.primitive.IntegerDt;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

public class CustomProcedureRequestTest {

	private static final org.slf4j.Logger ourLog = org.slf4j.LoggerFactory.getLogger(CustomProcedureRequestTest.class);
	
	@Test
	public void testCreate() {
		FhirContext ctx = FhirContext.forDstu2();
		CustomProcedureRequest procedureRequest = new CustomProcedureRequest();
		CustomTimingDt timingDt = new CustomTimingDt();
		CustomTimingDt._Repeat repeat = new CustomTimingDt._Repeat();
		repeat._setFrequency(new IntegerDt(2));
		timingDt._setRepeat(repeat);
		procedureRequest._setScheduled(timingDt);

		String encoded = ctx.newXmlParser().setPrettyPrint(true).encodeResourceToString(procedureRequest);
		ourLog.info(encoded);
		
		//@formatter:off
		assertThat(encoded).containsSubsequence(
			"<ProcedureRequest xmlns=\"http://hl7.org/fhir\">",
				"<meta>",
					"<profile value=\"http://test/\"/>",
				"</meta>",
				"<scheduledTiming>",
					"<repeat>",
						"<frequency value=\"2\"/>",
					"</repeat>",
				"</scheduledTiming>",
			"</ProcedureRequest>");
		//@formatter:on
	}
	
}
