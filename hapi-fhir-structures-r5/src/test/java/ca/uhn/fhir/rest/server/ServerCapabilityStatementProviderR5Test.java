
package ca.uhn.fhir.rest.server;

import static org.hamcrest.Matchers.containsString;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.model.api.annotation.ResourceDef;
import ca.uhn.fhir.rest.annotation.IdParam;
import ca.uhn.fhir.rest.annotation.Read;
import ca.uhn.fhir.rest.annotation.Search;
import ca.uhn.fhir.rest.api.server.RequestDetails;
import ca.uhn.fhir.rest.server.servlet.ServletRequestDetails;
import java.util.List;
import javax.servlet.ServletConfig;
import javax.servlet.http.HttpServletRequest;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r5.hapi.rest.server.ServerCapabilityStatementProvider;
import org.hl7.fhir.r5.model.CapabilityStatement;
import org.hl7.fhir.r5.model.CapabilityStatement.CapabilityStatementRestResourceComponent;
import org.hl7.fhir.r5.model.IdType;
import org.hl7.fhir.r5.model.Patient;
import org.junit.Test;

public class ServerCapabilityStatementProviderR5Test {

    private static final org.slf4j.Logger ourLog = org.slf4j.LoggerFactory.getLogger(ServerCapabilityStatementProviderR5Test.class);

    private static FhirContext ourCtx;

	static {
		ourCtx = FhirContext.forR5();
	}

    private HttpServletRequest createHttpServletRequest() {
		HttpServletRequest req = mock(HttpServletRequest.class);
		when(req.getRequestURI()).thenReturn("/FhirStorm/fhir/Patient/_search");
		when(req.getServletPath()).thenReturn("/fhir");
		when(req.getRequestURL()).thenReturn(new StringBuffer().append("http://fhirstorm.dyndns.org:8080/FhirStorm/fhir/Patient/_search"));
		when(req.getContextPath()).thenReturn("/FhirStorm");
		return req;
	}

    private ServletConfig createServletConfig() {
		ServletConfig sc = mock(ServletConfig.class);
		when(sc.getServletContext()).thenReturn(null);
		return sc;
	}

    private RequestDetails createRequestDetails(RestfulServer theServer) {
		ServletRequestDetails retVal = new ServletRequestDetails(null);
		retVal.setServer(theServer);
		return retVal;
	}

    @Test
    public void testProfiledResourceStructureDefinitionLinks() throws Exception {
        RestfulServer rs = new RestfulServer(ourCtx);
        rs.setResourceProviders(new ProfiledPatientProvider(), new MultipleProfilesPatientProvider());

        ServerCapabilityStatementProvider sc = new ServerCapabilityStatementProvider();
        rs.setServerConformanceProvider(sc);

        rs.init(createServletConfig());

        CapabilityStatement conformance = sc.getServerConformance(createHttpServletRequest(), createRequestDetails(rs));
        ourLog.info(ourCtx.newXmlParser().setPrettyPrint(true).encodeResourceToString(conformance));

        List<CapabilityStatementRestResourceComponent> resources = conformance.getRestFirstRep().getResource();
        CapabilityStatementRestResourceComponent patientResource = resources.stream()
            .filter(resource -> "Patient".equals(resource.getType()))
            .findFirst().get();
        assertThat(patientResource.getProfile(), containsString(PATIENT_SUB));
    }

    public static class ProfiledPatientProvider implements IResourceProvider {

    @Override
    public Class<? extends IBaseResource> getResourceType() {
      return PatientSubSub2.class;
    }

    @Search
    public List<PatientSubSub2> find() {
      return null;
    }
  }

  public static class MultipleProfilesPatientProvider implements IResourceProvider {

    @Override
    public Class<? extends IBaseResource> getResourceType() {
      return PatientSubSub.class;
    }

    @Read(type = PatientTripleSub.class)
    public PatientTripleSub read(@IdParam IdType theId) {
      return null;
    }

  }

  public static final String PATIENT_SUB = "PatientSub";
  public static final String PATIENT_SUB_SUB = "PatientSubSub";
  public static final String PATIENT_SUB_SUB_2 = "PatientSubSub2";
  public static final String PATIENT_TRIPLE_SUB = "PatientTripleSub";

  @ResourceDef(id = PATIENT_SUB)
  public static class PatientSub extends Patient {}
  
  @ResourceDef(id = PATIENT_SUB_SUB)
  public static class PatientSubSub extends PatientSub {}

  @ResourceDef(id = PATIENT_SUB_SUB_2)
  public static class PatientSubSub2 extends PatientSub {}

  @ResourceDef(id = PATIENT_TRIPLE_SUB)
  public static class PatientTripleSub extends PatientSubSub {}

}
