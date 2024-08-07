package ca.uhn.fhir.rest.server.interceptor.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.context.api.AddProfileTagEnum;
import ca.uhn.fhir.interceptor.api.HookParams;
import ca.uhn.fhir.interceptor.api.IInterceptorBroadcaster;
import ca.uhn.fhir.interceptor.api.Pointcut;
import ca.uhn.fhir.rest.annotation.GraphQL;
import ca.uhn.fhir.rest.annotation.GraphQLQueryUrl;
import ca.uhn.fhir.rest.annotation.History;
import ca.uhn.fhir.rest.annotation.IdParam;
import ca.uhn.fhir.rest.annotation.Operation;
import ca.uhn.fhir.rest.annotation.OperationParam;
import ca.uhn.fhir.rest.annotation.Transaction;
import ca.uhn.fhir.rest.annotation.TransactionParam;
import ca.uhn.fhir.rest.api.Constants;
import ca.uhn.fhir.rest.api.server.RequestDetails;
import ca.uhn.fhir.rest.server.FifoMemoryPagingProvider;
import ca.uhn.fhir.rest.server.servlet.ServletRequestDetails;
import ca.uhn.fhir.test.utilities.HttpClientExtension;
import ca.uhn.fhir.test.utilities.server.RestfulServerExtension;
import ca.uhn.fhir.util.TestUtil;
import org.apache.commons.io.IOUtils;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.hl7.fhir.dstu3.model.Binary;
import org.hl7.fhir.dstu3.model.Bundle;
import org.hl7.fhir.dstu3.model.CarePlan;
import org.hl7.fhir.dstu3.model.DiagnosticReport;
import org.hl7.fhir.dstu3.model.IdType;
import org.hl7.fhir.dstu3.model.Observation;
import org.hl7.fhir.dstu3.model.Organization;
import org.hl7.fhir.dstu3.model.Parameters;
import org.hl7.fhir.dstu3.model.Patient;
import org.hl7.fhir.dstu3.model.Reference;
import org.hl7.fhir.dstu3.model.Resource;
import org.hl7.fhir.dstu3.model.ResourceType;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.instance.model.api.IIdType;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class AuthorizationInterceptorDstu3Test {

	private static final FhirContext ourCtx = FhirContext.forDstu3Cached();
	private static List<Resource> ourReturn;
	private static List<IBaseResource> ourDeleted;

	@RegisterExtension
	private RestfulServerExtension ourServer  = new RestfulServerExtension(ourCtx)
		 .registerProvider(new PlainProvider())
		 .withPagingProvider(new FifoMemoryPagingProvider(100));

	@RegisterExtension
	private HttpClientExtension ourClient = new HttpClientExtension();

	@BeforeEach
	public void before() {
		ourCtx.setAddProfileTagWhenEncoding(AddProfileTagEnum.NEVER);
		ourServer.getInterceptorService().unregisterAllInterceptors();
		ourServer.getRestfulServer().setTenantIdentificationStrategy(null);
		ourReturn = null;
		ourDeleted = null;
	}

	private Resource createCarePlan(Integer theId, String theSubjectId) {
		CarePlan retVal = new CarePlan();
		if (theId != null) {
			retVal.setId(new IdType("CarePlan", (long) theId));
		}
		retVal.setSubject(new Reference("Patient/" + theSubjectId));
		return retVal;
	}

	private Resource createDiagnosticReport(Integer theId, String theSubjectId) {
		DiagnosticReport retVal = new DiagnosticReport();
		if (theId != null) {
			retVal.setId(new IdType("DiagnosticReport", (long) theId));
		}
		retVal.getCode().setText("OBS");
		retVal.setSubject(new Reference(theSubjectId));
		return retVal;
	}

	private HttpEntity createFhirResourceEntity(IBaseResource theResource) {
		String out = ourCtx.newJsonParser().encodeResourceToString(theResource);
		return new StringEntity(out, ContentType.create(Constants.CT_FHIR_JSON, "UTF-8"));
	}

	private Resource createObservation(Integer theId, String theSubjectId) {
		Observation retVal = new Observation();
		if (theId != null) {
			retVal.setId(new IdType("Observation", (long) theId));
		}
		retVal.getCode().setText("OBS");
		retVal.setSubject(new Reference(theSubjectId));

		if (theSubjectId != null && theSubjectId.startsWith("#")) {
			Patient p = new Patient();
			p.setId(theSubjectId);
			p.setActive(true);
			retVal.addContained(p);
		}

		return retVal;
	}

	private Organization createOrganization(int theIndex) {
		Organization retVal = new Organization();
		retVal.setId("" + theIndex);
		retVal.setName("Org " + theIndex);
		return retVal;
	}

	private Resource createPatient(Integer theId) {
		Patient retVal = new Patient();
		if (theId != null) {
			retVal.setId(new IdType("Patient", (long) theId));
		}
		retVal.addName().setFamily("FAM");
		return retVal;
	}

	private Resource createPatient(Integer theId, int theVersion) {
		Resource retVal = createPatient(theId);
		retVal.setId(retVal.getIdElement().withVersion(Integer.toString(theVersion)));
		return retVal;
	}

	private Bundle createTransactionWithPlaceholdersResponseBundle() {
		Bundle output = new Bundle();
		output.setType(Bundle.BundleType.TRANSACTIONRESPONSE);
		output.addEntry()
			.setResource(new Patient().setActive(true)) // don't give this an ID
			.getResponse().setLocation("/Patient/1");
		return output;
	}

	private String extractResponseAndClose(HttpResponse status) throws IOException {
		if (status.getEntity() == null) {
			return null;
		}
		String responseContent;
		responseContent = IOUtils.toString(status.getEntity().getContent(), StandardCharsets.UTF_8);
		IOUtils.closeQuietly(status.getEntity().getContent());
		return responseContent;
	}


	@Test
	public void testTransactionWithPatch() throws IOException {

		ourServer.registerInterceptor(new AuthorizationInterceptor(PolicyEnum.DENY) {
			@Override
			public List<IAuthRule> buildRuleList(RequestDetails theRequestDetails) {
				return new RuleBuilder()
					.allow("transactions").transaction().withAnyOperation().andApplyNormalRules().andThen()
					.allow("read patient").patch().allRequests().andThen()
					.allow().write().resourcesOfType("Patient").withAnyId().andThen()
					.build();
			}
		});

		// Payload
		Binary binary = new Binary();
		binary.setContent("{}".getBytes(Charset.defaultCharset()));
		binary.setContentType(Constants.CT_JSON_PATCH);

		// Request is a transaction with 1 search
		Bundle requestBundle = new Bundle();
		requestBundle.setType(Bundle.BundleType.TRANSACTION);
		requestBundle.addEntry()
			.setResource(binary)
			.getRequest()
			.setUrl(ResourceType.Patient.name() + "/123");

		/*
		 * Response is a transaction response containing the search results
		 */
		Bundle responseBundle = new Bundle();
		responseBundle.setType(Bundle.BundleType.TRANSACTION);
		ourReturn = Collections.singletonList(responseBundle);

		HttpPost httpPost = new HttpPost(ourServer.getBaseUrl());
		httpPost.setEntity(createFhirResourceEntity(requestBundle));
		CloseableHttpResponse status = ourClient.execute(httpPost);
		String resp = extractResponseAndClose(status);
		assertEquals(200, status.getStatusLine().getStatusCode());

	}


	public static class PlainProvider {

		@History()
		public List<Resource> history() {
			return (ourReturn);
		}

		@Operation(name = "opName", idempotent = true)
		public Parameters operation() {
			return (Parameters) new Parameters().setId("1");
		}

		@Operation(name = "process-message", idempotent = true)
		public Parameters processMessage(@OperationParam(name = "content") Bundle theInput) {
			return (Parameters) new Parameters().setId("1");
		}

		@GraphQL
		public String processGraphQlRequest(ServletRequestDetails theRequestDetails, @IdParam IIdType theId, @GraphQLQueryUrl String theQuery) {
			return "{'foo':'bar'}";
		}

		@Transaction()
		public Bundle search(ServletRequestDetails theRequestDetails, IInterceptorBroadcaster theInterceptorBroadcaster, @TransactionParam Bundle theInput) {
			if (ourDeleted != null) {
				for (IBaseResource next : ourDeleted) {
					HookParams params = new HookParams()
						.add(IBaseResource.class, next)
						.add(RequestDetails.class, theRequestDetails)
						.add(ServletRequestDetails.class, theRequestDetails);
					theInterceptorBroadcaster.callHooks(Pointcut.STORAGE_PRESTORAGE_RESOURCE_DELETED, params);
				}
			}
			return (Bundle) ourReturn.get(0);
		}

	}

	@AfterAll
	public static void afterClassClearContext() throws Exception {
		TestUtil.randomizeLocaleAndTimezone();
	}


}
