package ca.uhn.fhir.jpa.provider.dstu3;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import ca.uhn.fhir.jpa.api.config.JpaStorageSettings;
import ca.uhn.fhir.jpa.api.dao.IFhirResourceDao;
import ca.uhn.fhir.jpa.dao.data.IResourceTableDao;
import ca.uhn.fhir.jpa.entity.TermCodeSystemVersion;
import ca.uhn.fhir.jpa.entity.TermConcept;
import ca.uhn.fhir.jpa.entity.TermConceptParentChildLink.RelationshipTypeEnum;
import ca.uhn.fhir.jpa.entity.TermValueSet;
import ca.uhn.fhir.jpa.entity.TermValueSetConcept;
import ca.uhn.fhir.jpa.entity.TermValueSetPreExpansionStatusEnum;
import ca.uhn.fhir.jpa.model.dao.JpaPid;
import ca.uhn.fhir.jpa.model.entity.ResourceTable;
import ca.uhn.fhir.jpa.term.api.ITermCodeSystemStorageSvc;
import ca.uhn.fhir.jpa.test.BaseJpaTest;
import ca.uhn.fhir.rest.server.exceptions.ResourceNotFoundException;
import ca.uhn.fhir.rest.server.exceptions.UnprocessableEntityException;
import ca.uhn.fhir.rest.server.servlet.ServletRequestDetails;
import org.hl7.fhir.dstu3.model.BooleanType;
import org.hl7.fhir.dstu3.model.Bundle;
import org.hl7.fhir.dstu3.model.CodeSystem;
import org.hl7.fhir.dstu3.model.CodeSystem.CodeSystemContentMode;
import org.hl7.fhir.dstu3.model.CodeSystem.ConceptDefinitionComponent;
import org.hl7.fhir.dstu3.model.CodeType;
import org.hl7.fhir.dstu3.model.CodeableConcept;
import org.hl7.fhir.dstu3.model.Coding;
import org.hl7.fhir.dstu3.model.IdType;
import org.hl7.fhir.dstu3.model.Parameters;
import org.hl7.fhir.dstu3.model.StringType;
import org.hl7.fhir.dstu3.model.UriType;
import org.hl7.fhir.dstu3.model.ValueSet;
import org.hl7.fhir.dstu3.model.ValueSet.ConceptSetComponent;
import org.hl7.fhir.dstu3.model.ValueSet.FilterOperator;
import org.hl7.fhir.dstu3.model.codesystems.HttpVerb;
import org.hl7.fhir.instance.model.api.IIdType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallbackWithoutResult;
import org.springframework.transaction.support.TransactionTemplate;

import jakarta.annotation.Nonnull;
import java.io.IOException;
import java.util.Optional;

import static ca.uhn.fhir.jpa.dao.dstu3.FhirResourceDaoDstu3TerminologyTest.URL_MY_CODE_SYSTEM;
import static ca.uhn.fhir.jpa.dao.dstu3.FhirResourceDaoDstu3TerminologyTest.URL_MY_VALUE_SET;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.fail;


public class ResourceProviderDstu3ValueSetVersionedTest extends BaseResourceProviderDstu3Test {

	private static final org.slf4j.Logger ourLog = org.slf4j.LoggerFactory.getLogger(ResourceProviderDstu3ValueSetVersionedTest.class);
	private IIdType myExtensionalCsId_v1;
	private IIdType myExtensionalCsId_v2;
	private IIdType myExtensionalVsId_v1;
	private IIdType myExtensionalVsId_v2;
	private IIdType myLocalValueSetId_v1;
	private IIdType myLocalValueSetId_v2;
	private JpaPid myExtensionalVsIdOnResourceTable_v1;
	private JpaPid myExtensionalVsIdOnResourceTable_v2;
	private ValueSet myLocalVs_v1;
	private ValueSet myLocalVs_v2;

	private void loadAndPersistCodeSystemAndValueSet() throws IOException {
		loadAndPersistCodeSystem();
		loadAndPersistValueSet();
	}

	private void loadAndPersistCodeSystemAndValueSetWithDesignations() throws IOException {
		loadAndPersistCodeSystemWithDesignations();
		loadAndPersistValueSet();
	}

	private void loadAndPersistCodeSystem() throws IOException {
		CodeSystem codeSystem = loadResourceFromClasspath(CodeSystem.class, "/extensional-case-3-cs.xml");
		persistCodeSystem(codeSystem);
	}

	private void loadAndPersistCodeSystemWithDesignations() throws IOException {
		CodeSystem codeSystem = loadResourceFromClasspath(CodeSystem.class, "/extensional-case-3-cs-with-designations.xml");
		persistCodeSystem(codeSystem);
	}

	private void persistCodeSystem(CodeSystem theCodeSystem) {
		theCodeSystem.setId("CodeSystem/cs1");
		theCodeSystem.setVersion("1");
		new TransactionTemplate(myTxManager).execute(new TransactionCallbackWithoutResult() {
			@Override
			protected void doInTransactionWithoutResult(@Nonnull TransactionStatus theStatus) {
				myExtensionalCsId_v1 = myCodeSystemDao.create(theCodeSystem, mySrd).getId().toUnqualifiedVersionless();
			}
		});
		myCodeSystemDao.readEntity(myExtensionalCsId_v1, null);

		theCodeSystem.setId("CodeSystem/cs2");
		theCodeSystem.setVersion("2");
		for(ConceptDefinitionComponent conceptDefinitionComponent : theCodeSystem.getConcept()) {
			conceptDefinitionComponent.setDisplay(conceptDefinitionComponent.getDisplay() + " v2");
		}
		new TransactionTemplate(myTxManager).execute(new TransactionCallbackWithoutResult() {
			@Override
			protected void doInTransactionWithoutResult(@Nonnull TransactionStatus theStatus) {
				myExtensionalCsId_v2 = myCodeSystemDao.create(theCodeSystem, mySrd).getId().toUnqualifiedVersionless();
			}
		});
		myCodeSystemDao.readEntity(myExtensionalCsId_v2, null);

	}

	private void loadAndPersistValueSet() throws IOException {
		ValueSet valueSet = loadResourceFromClasspath(ValueSet.class, "/extensional-case-3-vs.xml");
		valueSet.setVersion("1");
		valueSet.setId("ValueSet/vs1");
		valueSet.getCompose().getInclude().get(0).setVersion("1");
		myExtensionalVsId_v1 = persistSingleValueSet(valueSet, HttpVerb.POST);
		myExtensionalVsIdOnResourceTable_v1 = JpaPid.fromId(myValueSetDao.readEntity(myExtensionalVsId_v1, null).getIdDt().getIdPartAsLong());

		valueSet.setVersion("2");
		valueSet.setId("ValueSet/vs2");
		valueSet.getCompose().getInclude().get(0).setVersion("2");
		myExtensionalVsId_v2 = persistSingleValueSet(valueSet, HttpVerb.POST);
		myExtensionalVsIdOnResourceTable_v2 = JpaPid.fromId(myValueSetDao.readEntity(myExtensionalVsId_v2, null).getIdDt().getIdPartAsLong());

	}

	private IIdType persistSingleValueSet(ValueSet theValueSet, HttpVerb theVerb) {
		final IIdType[] vsId = new IIdType[1];
		switch (theVerb) {
			case GET:
				break;
			case POST:
				new TransactionTemplate(myTxManager).execute(new TransactionCallbackWithoutResult() {
					@Override
					protected void doInTransactionWithoutResult(@Nonnull TransactionStatus theStatus) {
						vsId[0] = myValueSetDao.create(theValueSet, mySrd).getId().toUnqualifiedVersionless();
					}
				});
				break;
			case PUT:
				new TransactionTemplate(myTxManager).execute(new TransactionCallbackWithoutResult() {
					@Override
					protected void doInTransactionWithoutResult(@Nonnull TransactionStatus theStatus) {
						vsId[0] = myValueSetDao.update(theValueSet, mySrd).getId().toUnqualifiedVersionless();
					}
				});
				break;
			case DELETE:
			case NULL:
			default:
				throw new IllegalArgumentException("HTTP verb is not supported: " + theVerb);
		}
		return vsId[0];
	}

	private String createExternalCs(String theCodeSystemVersion) {
		IFhirResourceDao<CodeSystem> codeSystemDao = myCodeSystemDao;
		IResourceTableDao resourceTableDao = myResourceTableDao;

		return createExternalCs(this, codeSystemDao, resourceTableDao, myTermCodeSystemStorageSvc, mySrd, theCodeSystemVersion).getUrl();
	}

	private void createExternalCsAndLocalVs() {
		String codeSystemUrl = createExternalCs("1");
		myLocalVs_v1 = createLocalVs(codeSystemUrl, "1");
		myLocalValueSetId_v1 = persistLocalVs(myLocalVs_v1);

		codeSystemUrl = createExternalCs("2");
		myLocalVs_v2 = createLocalVs(codeSystemUrl, "2");
		myLocalValueSetId_v2 = persistLocalVs(myLocalVs_v2);

	}

	private ValueSet createLocalVs(String theCodeSystemUrl, String theValueSetVersion) {
		ValueSet myLocalVs = new ValueSet();
		myLocalVs.setUrl(URL_MY_VALUE_SET);
		myLocalVs.setVersion(theValueSetVersion);
		ConceptSetComponent include = myLocalVs.getCompose().addInclude();
		include.setSystem(theCodeSystemUrl);
		include.setVersion(theValueSetVersion);
		include.addFilter().setProperty("concept").setOp(FilterOperator.DESCENDENTOF).setValue("ParentA");
		return myLocalVs;

	}

	private IIdType persistLocalVs(ValueSet theValueSet) {
		return myValueSetDao.create(theValueSet, mySrd).getId().toUnqualifiedVersionless();
	}

	@Test
	public void testExpandById() throws Exception {
		loadAndPersistCodeSystemAndValueSet();

		// Test with v1 of ValueSet
		Parameters respParam = myClient
			.operation()
			.onInstance(myExtensionalVsId_v1)
			.named("expand")
			.withNoParameters(Parameters.class)
			.execute();
		ValueSet expanded = (ValueSet) respParam.getParameter().get(0).getResource();

		String resp = myFhirContext.newXmlParser().setPrettyPrint(true).encodeResourceToString(expanded);
		ourLog.info(resp);
		assertThat(resp).contains("<ValueSet xmlns=\"http://hl7.org/fhir\">");
		assertThat(resp).contains("<expansion>");
		assertThat(resp).contains("<contains>");
		assertThat(resp).contains("<system value=\"http://acme.org\"/>");
		assertThat(resp).contains("<code value=\"8450-9\"/>");
		assertThat(resp).contains("<display value=\"Systolic blood pressure--expiration\"/>");
		assertThat(resp).contains("</contains>");
		assertThat(resp).contains("<contains>");
		assertThat(resp).contains("<system value=\"http://acme.org\"/>");
		assertThat(resp).contains("<code value=\"11378-7\"/>");
		assertThat(resp).contains("<display value=\"Systolic blood pressure at First encounter\"/>");
		assertThat(resp).contains("</contains>");
		assertThat(resp).contains("</expansion>");

		// Test with v2 of ValueSet
		respParam = myClient
			.operation()
			.onInstance(myExtensionalVsId_v2)
			.named("expand")
			.withNoParameters(Parameters.class)
			.execute();
		expanded = (ValueSet) respParam.getParameter().get(0).getResource();

		resp = myFhirContext.newXmlParser().setPrettyPrint(true).encodeResourceToString(expanded);
		ourLog.info(resp);
		assertThat(resp).contains("<ValueSet xmlns=\"http://hl7.org/fhir\">");
		assertThat(resp).contains("<expansion>");
		assertThat(resp).contains("<contains>");
		assertThat(resp).contains("<system value=\"http://acme.org\"/>");
		assertThat(resp).contains("<code value=\"8450-9\"/>");
		assertThat(resp).contains("<display value=\"Systolic blood pressure--expiration v2\"/>");
		assertThat(resp).contains("</contains>");
		assertThat(resp).contains("<contains>");
		assertThat(resp).contains("<system value=\"http://acme.org\"/>");
		assertThat(resp).contains("<code value=\"11378-7\"/>");
		assertThat(resp).contains("<display value=\"Systolic blood pressure at First encounter v2\"/>");
		assertThat(resp).contains("</contains>");
		assertThat(resp).contains("</expansion>");

	}

	@Test
	public void testExpandByIdWithPreExpansion() throws Exception {
		myStorageSettings.setPreExpandValueSets(true);

		loadAndPersistCodeSystemAndValueSet();
		myTermSvc.preExpandDeferredValueSetsToTerminologyTables();
		Slice<TermValueSet> page = runInTransaction(()->myTermValueSetDao.findByExpansionStatus(PageRequest.of(0, 10), TermValueSetPreExpansionStatusEnum.EXPANDED));
		assertThat(page.getContent()).hasSize(2);

		// Verify v1 ValueSet
		Parameters respParam = myClient
			.operation()
			.onInstance(myExtensionalVsId_v1)
			.named("expand")
			.withNoParameters(Parameters.class)
			.execute();
		ValueSet expanded = (ValueSet) respParam.getParameter().get(0).getResource();

		String resp = myFhirContext.newXmlParser().setPrettyPrint(true).encodeResourceToString(expanded);
		ourLog.info(resp);
		assertThat(resp).contains("<ValueSet xmlns=\"http://hl7.org/fhir\">");
		assertThat(resp).contains("<expansion>");
		assertThat(resp).contains("<contains>");
		assertThat(resp).contains("<system value=\"http://acme.org\"/>");
		assertThat(resp).contains("<code value=\"8450-9\"/>");
		assertThat(resp).contains("<display value=\"Systolic blood pressure--expiration\"/>");
		assertThat(resp).contains("</contains>");
		assertThat(resp).contains("<contains>");
		assertThat(resp).contains("<system value=\"http://acme.org\"/>");
		assertThat(resp).contains("<code value=\"11378-7\"/>");
		assertThat(resp).contains("<display value=\"Systolic blood pressure at First encounter\"/>");
		assertThat(resp).contains("</contains>");
		assertThat(resp).contains("</expansion>");

		// Verify v2 ValueSet
		respParam = myClient
			.operation()
			.onInstance(myExtensionalVsId_v2)
			.named("expand")
			.withNoParameters(Parameters.class)
			.execute();
		expanded = (ValueSet) respParam.getParameter().get(0).getResource();

		resp = myFhirContext.newXmlParser().setPrettyPrint(true).encodeResourceToString(expanded);
		ourLog.info(resp);
		assertThat(resp).contains("<ValueSet xmlns=\"http://hl7.org/fhir\">");
		assertThat(resp).contains("<expansion>");
		assertThat(resp).contains("<contains>");
		assertThat(resp).contains("<system value=\"http://acme.org\"/>");
		assertThat(resp).contains("<code value=\"8450-9\"/>");
		assertThat(resp).contains("<display value=\"Systolic blood pressure--expiration v2\"/>");
		assertThat(resp).contains("</contains>");
		assertThat(resp).contains("<contains>");
		assertThat(resp).contains("<system value=\"http://acme.org\"/>");
		assertThat(resp).contains("<code value=\"11378-7\"/>");
		assertThat(resp).contains("<display value=\"Systolic blood pressure at First encounter v2\"/>");
		assertThat(resp).contains("</contains>");
		assertThat(resp).contains("</expansion>");

	}

	@Test
	public void testExpandByIdWithFilter() throws Exception {
		loadAndPersistCodeSystemAndValueSet();

		// Verify ValueSet v1
		Parameters respParam = myClient
			.operation()
			.onInstance(myExtensionalVsId_v1)
			.named("expand")
			.withParameter(Parameters.class, "filter", new StringType("systolic"))
			.execute();
		ValueSet expanded = (ValueSet) respParam.getParameter().get(0).getResource();

		String resp = myFhirContext.newXmlParser().setPrettyPrint(true).encodeResourceToString(expanded);
		ourLog.info(resp);
		assertThat(resp).contains("<display value=\"Systolic blood pressure at First encounter\"/>");
		assertThat(resp).doesNotContain("\"Foo Code\"");

		// Verify ValueSet v2
		respParam = myClient
			.operation()
			.onInstance(myExtensionalVsId_v2)
			.named("expand")
			.withParameter(Parameters.class, "filter", new StringType("systolic"))
			.execute();
		expanded = (ValueSet) respParam.getParameter().get(0).getResource();

		resp = myFhirContext.newXmlParser().setPrettyPrint(true).encodeResourceToString(expanded);
		ourLog.info(resp);
		assertThat(resp).contains("<display value=\"Systolic blood pressure at First encounter v2\"/>");
		assertThat(resp).doesNotContain("\"Foo Code\"");

	}

	@Test
	public void testExpandByIdWithFilterWithPreExpansion() throws Exception {
		myStorageSettings.setPreExpandValueSets(true);

		loadAndPersistCodeSystemAndValueSet();
		myTermSvc.preExpandDeferredValueSetsToTerminologyTables();

		Slice<TermValueSet> page = runInTransaction(()->myTermValueSetDao.findByExpansionStatus(PageRequest.of(0, 10), TermValueSetPreExpansionStatusEnum.EXPANDED));
		assertThat(page.getContent()).hasSize(2);

		// Validate ValueSet v1
		Parameters respParam = myClient
			.operation()
			.onInstance(myExtensionalVsId_v1)
			.named("expand")
			.withParameter(Parameters.class, "filter", new StringType("systolic"))
			.execute();
		ValueSet expanded = (ValueSet) respParam.getParameter().get(0).getResource();

		String resp = myFhirContext.newXmlParser().setPrettyPrint(true).encodeResourceToString(expanded);
		ourLog.info(resp);
		assertThat(resp).contains("<display value=\"Systolic blood pressure at First encounter\"/>");
		assertThat(resp).doesNotContain("\"Foo Code\"");

		// Validate ValueSet v2
		respParam = myClient
			.operation()
			.onInstance(myExtensionalVsId_v2)
			.named("expand")
			.withParameter(Parameters.class, "filter", new StringType("systolic"))
			.execute();
		expanded = (ValueSet) respParam.getParameter().get(0).getResource();

		resp = myFhirContext.newXmlParser().setPrettyPrint(true).encodeResourceToString(expanded);
		ourLog.info(resp);
		assertThat(resp).contains("<display value=\"Systolic blood pressure at First encounter v2\"/>");
		assertThat(resp).doesNotContain("\"Foo Code\"");

	}

	@Test
	public void testExpandByUrlAndVersion() throws Exception {
		loadAndPersistCodeSystemAndValueSet();

		// Check expansion of multi-versioned ValueSet with version 1
		Parameters respParam = myClient
			.operation()
			.onType(ValueSet.class)
			.named("expand")
			.withParameter(Parameters.class, "url", new UriType("http://www.healthintersections.com.au/fhir/ValueSet/extensional-case-2"))
			.andParameter("valueSetVersion", new StringType("1"))
			.execute();
		ValueSet expanded = (ValueSet) respParam.getParameter().get(0).getResource();

		String resp = myFhirContext.newXmlParser().setPrettyPrint(true).encodeResourceToString(expanded);
		ourLog.info(resp);
		assertThat(resp).contains(
			"<code value=\"11378-7\"/>",
			"<display value=\"Systolic blood pressure at First encounter\"/>");

		// Check expansion of multi-versioned ValueSet with version set to null
		respParam = myClient
			.operation()
			.onType(ValueSet.class)
			.named("expand")
			.withParameter(Parameters.class, "url", new UriType("http://www.healthintersections.com.au/fhir/ValueSet/extensional-case-2"))
			.execute();
		expanded = (ValueSet) respParam.getParameter().get(0).getResource();

		// Should return v2 as this was the last version loaded.
		resp = myFhirContext.newXmlParser().setPrettyPrint(true).encodeResourceToString(expanded);
		ourLog.info(resp);
		assertThat(resp).contains(
			"<code value=\"11378-7\"/>",
			"<display value=\"Systolic blood pressure at First encounter v2\"/>");

		// Check expansion of version 2
		respParam = myClient
			.operation()
			.onType(ValueSet.class)
			.named("expand")
			.withParameter(Parameters.class, "url", new UriType("http://www.healthintersections.com.au/fhir/ValueSet/extensional-case-2"))
			.andParameter("valueSetVersion", new StringType("2"))
			.execute();
		expanded = (ValueSet) respParam.getParameter().get(0).getResource();

		resp = myFhirContext.newXmlParser().setPrettyPrint(true).encodeResourceToString(expanded);
		ourLog.info(resp);
		assertThat(resp).contains(
			"<code value=\"11378-7\"/>",
			"<display value=\"Systolic blood pressure at First encounter v2\"/>");

	}

	@Test
	public void testExpandByUrlAndVersionNoPreExpansion() throws Exception {
		myStorageSettings.setPreExpandValueSets(false);
		loadAndPersistCodeSystemAndValueSet();

		// Check expansion of multi-versioned ValueSet with version 1
		Parameters respParam = myClient
			.operation()
			.onType(ValueSet.class)
			.named("expand")
			.withParameter(Parameters.class, "url", new UriType("http://www.healthintersections.com.au/fhir/ValueSet/extensional-case-2"))
			.andParameter("valueSetVersion", new StringType("1"))
			.execute();
		ValueSet expanded = (ValueSet) respParam.getParameter().get(0).getResource();

		String resp = myFhirContext.newXmlParser().setPrettyPrint(true).encodeResourceToString(expanded);
		ourLog.info(resp);
		assertThat(resp).contains(
			"<code value=\"11378-7\"/>",
			"<display value=\"Systolic blood pressure at First encounter\"/>");

		// Check expansion of multi-versioned ValueSet with version set to null
		respParam = myClient
			.operation()
			.onType(ValueSet.class)
			.named("expand")
			.withParameter(Parameters.class, "url", new UriType("http://www.healthintersections.com.au/fhir/ValueSet/extensional-case-2"))
			.execute();
		expanded = (ValueSet) respParam.getParameter().get(0).getResource();

		// Should return v2 as this was the last version loaded.
		resp = myFhirContext.newXmlParser().setPrettyPrint(true).encodeResourceToString(expanded);
		ourLog.info(resp);
		assertThat(resp).contains(
			"<code value=\"11378-7\"/>",
			"<display value=\"Systolic blood pressure at First encounter v2\"/>");

		// Check expansion of version 2
		respParam = myClient
			.operation()
			.onType(ValueSet.class)
			.named("expand")
			.withParameter(Parameters.class, "url", new UriType("http://www.healthintersections.com.au/fhir/ValueSet/extensional-case-2"))
			.andParameter("valueSetVersion", new StringType("2"))
			.execute();
		expanded = (ValueSet) respParam.getParameter().get(0).getResource();

		resp = myFhirContext.newXmlParser().setPrettyPrint(true).encodeResourceToString(expanded);
		ourLog.info(resp);
		assertThat(resp).contains(
			"<code value=\"11378-7\"/>",
			"<display value=\"Systolic blood pressure at First encounter v2\"/>");

	}

	@Test
	public void testExpandByUrlWithBogusVersion() throws Exception {
		loadAndPersistCodeSystemAndValueSet();

		try {
			myClient
				.operation()
				.onType(ValueSet.class)
				.named("expand")
				.withParameter(Parameters.class, "url", new UriType("http://www.healthintersections.com.au/fhir/ValueSet/extensional-case-2"))
				.andParameter("valueSetVersion", new StringType("3"))
				.execute();
		} catch (ResourceNotFoundException e) {
			assertEquals(404, e.getStatusCode());
			assertEquals("HTTP 404 Not Found: HAPI-2024: Unknown ValueSet: http%3A%2F%2Fwww.healthintersections.com.au%2Ffhir%2FValueSet%2Fextensional-case-2%7C3", e.getMessage());
		}
	}

	@Test
	public void testExpandByUrlWithPreExpansion() throws Exception {
		myStorageSettings.setPreExpandValueSets(true);

		loadAndPersistCodeSystemAndValueSet();
		myTermSvc.preExpandDeferredValueSetsToTerminologyTables();

		Slice<TermValueSet> page = runInTransaction(()->myTermValueSetDao.findByExpansionStatus(PageRequest.of(0, 10), TermValueSetPreExpansionStatusEnum.EXPANDED));
		assertThat(page.getContent()).hasSize(2);

		// Check expansion of multi-versioned ValueSet with version 1
		Parameters respParam = myClient
			.operation()
			.onType(ValueSet.class)
			.named("expand")
			.withParameter(Parameters.class, "url", new UriType("http://www.healthintersections.com.au/fhir/ValueSet/extensional-case-2"))
			.andParameter("valueSetVersion", new StringType("1"))
			.execute();
		ValueSet expanded = (ValueSet) respParam.getParameter().get(0).getResource();

		String resp = myFhirContext.newXmlParser().setPrettyPrint(true).encodeResourceToString(expanded);
		ourLog.info(resp);
		assertThat(resp).contains(
			"<code value=\"11378-7\"/>",
			"<display value=\"Systolic blood pressure at First encounter\"/>");

		// Check expansion of multi-versioned ValueSet with version set to null
		respParam = myClient
			.operation()
			.onType(ValueSet.class)
			.named("expand")
			.withParameter(Parameters.class, "url", new UriType("http://www.healthintersections.com.au/fhir/ValueSet/extensional-case-2"))
			.execute();
		expanded = (ValueSet) respParam.getParameter().get(0).getResource();

		// Should return v2 as this was the last version loaded.
		resp = myFhirContext.newXmlParser().setPrettyPrint(true).encodeResourceToString(expanded);
		ourLog.info(resp);
		assertThat(resp).contains(
			"<code value=\"11378-7\"/>",
			"<display value=\"Systolic blood pressure at First encounter v2\"/>");

		// Check expansion of version 2
		respParam = myClient
			.operation()
			.onType(ValueSet.class)
			.named("expand")
			.withParameter(Parameters.class, "url", new UriType("http://www.healthintersections.com.au/fhir/ValueSet/extensional-case-2"))
			.andParameter("valueSetVersion", new StringType("2"))
			.execute();
		expanded = (ValueSet) respParam.getParameter().get(0).getResource();

		resp = myFhirContext.newXmlParser().setPrettyPrint(true).encodeResourceToString(expanded);
		ourLog.info(resp);
		assertThat(resp).contains(
			"<code value=\"11378-7\"/>",
			"<display value=\"Systolic blood pressure at First encounter v2\"/>");

	}

	@Test
	public void testExpandByUrlWithPreExpansionAndBogusVersion() throws Exception {
		myStorageSettings.setPreExpandValueSets(true);

		loadAndPersistCodeSystemAndValueSet();
		myTermSvc.preExpandDeferredValueSetsToTerminologyTables();

		try {
			myClient
				.operation()
				.onType(ValueSet.class)
				.named("expand")
				.withParameter(Parameters.class, "url", new UriType("http://www.healthintersections.com.au/fhir/ValueSet/extensional-case-2"))
				.andParameter("valueSetVersion", new StringType("3"))
				.execute();
		} catch (ResourceNotFoundException e) {
			assertEquals(404, e.getStatusCode());
			assertEquals("HTTP 404 Not Found: HAPI-2024: Unknown ValueSet: http%3A%2F%2Fwww.healthintersections.com.au%2Ffhir%2FValueSet%2Fextensional-case-2%7C3", e.getMessage());
		}
	}

	@Test
	public void testExpandByValueSet() throws IOException {
		loadAndPersistCodeSystem();

		// Test with no version specified
		ValueSet toExpand = loadResourceFromClasspath(ValueSet.class, "/extensional-case-3-vs.xml");

		Parameters respParam = myClient
			.operation()
			.onType(ValueSet.class)
			.named("expand")
			.withParameter(Parameters.class, "valueSet", toExpand)
			.execute();
		ValueSet expanded = (ValueSet) respParam.getParameter().get(0).getResource();

		// Should return v2 as this was the last updated.
		String resp = myFhirContext.newXmlParser().setPrettyPrint(true).encodeResourceToString(expanded);
		ourLog.info(resp);
		assertThat(resp).contains(
			"<code value=\"11378-7\"/>",
			"<display value=\"Systolic blood pressure at First encounter v2\"/>");

		// Test with version 1 specified.
		toExpand.setVersion("1");
		toExpand.setId("ValueSet/vs1");
		toExpand.getCompose().getInclude().get(0).setVersion("1");

		respParam = myClient
			.operation()
			.onType(ValueSet.class)
			.named("expand")
			.withParameter(Parameters.class, "valueSet", toExpand)
			.andParameter("valueSetVersion", new StringType("1"))
			.execute();
		expanded = (ValueSet) respParam.getParameter().get(0).getResource();

		// Should return v1.
		resp = myFhirContext.newXmlParser().setPrettyPrint(true).encodeResourceToString(expanded);
		ourLog.info(resp);
		assertThat(resp).contains(
			"<code value=\"11378-7\"/>",
			"<display value=\"Systolic blood pressure at First encounter\"/>");

		// Test with version 2 specified.
		toExpand.setVersion("2");
		toExpand.setId("ValueSet/vs2");
		toExpand.getCompose().getInclude().get(0).setVersion("2");

		respParam = myClient
			.operation()
			.onType(ValueSet.class)
			.named("expand")
			.withParameter(Parameters.class, "valueSet", toExpand)
			.andParameter("valueSetVersion", new StringType("2"))
			.execute();
		expanded = (ValueSet) respParam.getParameter().get(0).getResource();

		// Should return v2.
		resp = myFhirContext.newXmlParser().setPrettyPrint(true).encodeResourceToString(expanded);
		ourLog.info(resp);
		assertThat(resp).contains(
			"<code value=\"11378-7\"/>",
			"<display value=\"Systolic blood pressure at First encounter v2\"/>");

	}

	@Test
	public void testExpandByValueSetWithPreExpansion() throws IOException {
		myStorageSettings.setPreExpandValueSets(true);

		loadAndPersistCodeSystem();
		myTermSvc.preExpandDeferredValueSetsToTerminologyTables();

		// Test with no version specified
		ValueSet toExpand = loadResourceFromClasspath(ValueSet.class, "/extensional-case-3-vs.xml");

		Parameters respParam = myClient
			.operation()
			.onType(ValueSet.class)
			.named("expand")
			.withParameter(Parameters.class, "valueSet", toExpand)
			.execute();
		ValueSet expanded = (ValueSet) respParam.getParameter().get(0).getResource();

		// Should return v2 as this was the last updated.
		String resp = myFhirContext.newXmlParser().setPrettyPrint(true).encodeResourceToString(expanded);
		ourLog.info(resp);
		assertThat(resp).contains(
			"<code value=\"11378-7\"/>",
			"<display value=\"Systolic blood pressure at First encounter v2\"/>");

		// Test with version 1 specified.
		toExpand.setVersion("1");
		toExpand.setId("ValueSet/vs1");
		toExpand.getCompose().getInclude().get(0).setVersion("1");

		respParam = myClient
			.operation()
			.onType(ValueSet.class)
			.named("expand")
			.withParameter(Parameters.class, "valueSet", toExpand)
			.andParameter("valueSetVersion", new StringType("1"))
			.execute();
		expanded = (ValueSet) respParam.getParameter().get(0).getResource();

		// Should return v1.
		resp = myFhirContext.newXmlParser().setPrettyPrint(true).encodeResourceToString(expanded);
		ourLog.info(resp);
		assertThat(resp).contains(
			"<code value=\"11378-7\"/>",
			"<display value=\"Systolic blood pressure at First encounter\"/>");

		// Test with version 2 specified.
		toExpand.setVersion("2");
		toExpand.setId("ValueSet/vs2");
		toExpand.getCompose().getInclude().get(0).setVersion("2");

		respParam = myClient
			.operation()
			.onType(ValueSet.class)
			.named("expand")
			.withParameter(Parameters.class, "valueSet", toExpand)
			.andParameter("valueSetVersion", new StringType("2"))
			.execute();
		expanded = (ValueSet) respParam.getParameter().get(0).getResource();

		// Should return v2.
		resp = myFhirContext.newXmlParser().setPrettyPrint(true).encodeResourceToString(expanded);
		ourLog.info(resp);
		assertThat(resp).contains(
			"<code value=\"11378-7\"/>",
			"<display value=\"Systolic blood pressure at First encounter v2\"/>");


	}

	@Test
	public void testExpandInlineVsAgainstExternalCs() {
		createExternalCsAndLocalVs();
		assertNotNull(myLocalVs_v1);
		assertNotNull(myLocalVs_v2);

		myLocalVs_v1.setId("");
		Parameters respParam = myClient
			.operation()
			.onType(ValueSet.class)
			.named("expand")
			.withParameter(Parameters.class, "valueSet", myLocalVs_v1)
			.execute();
		ValueSet expanded = (ValueSet) respParam.getParameter().get(0).getResource();

		String resp = myFhirContext.newXmlParser().setPrettyPrint(true).encodeResourceToString(expanded);
		ourLog.info(resp);

		assertThat(resp).containsIgnoringCase("<display value=\"Child AAA1\"/>");
		assertThat(resp).containsIgnoringCase("<display value=\"Child AAB1\"/>");
		assertThat(resp).doesNotContainIgnoringCase("<display value=\"Parent A1\"/>");

		myLocalVs_v2.setId("");
		respParam = myClient
			.operation()
			.onType(ValueSet.class)
			.named("expand")
			.withParameter(Parameters.class, "valueSet", myLocalVs_v2)
			.execute();
		expanded = (ValueSet) respParam.getParameter().get(0).getResource();

		resp = myFhirContext.newXmlParser().setPrettyPrint(true).encodeResourceToString(expanded);
		ourLog.info(resp);

		assertThat(resp).containsIgnoringCase("<display value=\"Child AAA2\"/>");
		assertThat(resp).containsIgnoringCase("<display value=\"Child AAB2\"/>");
		assertThat(resp).doesNotContainIgnoringCase("<display value=\"Parent A2\"/>");

	}

	@Test
	public void testExpandLocalVsAgainstExternalCs() {
		createExternalCsAndLocalVs();
		assertNotNull(myLocalValueSetId_v1);
		assertNotNull(myLocalValueSetId_v2);

		// Validate ValueSet v1
		Parameters respParam = myClient
			.operation()
			.onInstance(myLocalValueSetId_v1)
			.named("expand")
			.withNoParameters(Parameters.class)
			.execute();
		ValueSet expanded = (ValueSet) respParam.getParameter().get(0).getResource();

		String resp = myFhirContext.newXmlParser().setPrettyPrint(true).encodeResourceToString(expanded);
		ourLog.info(resp);

		assertThat(resp).containsIgnoringCase("<display value=\"Child AAA1\"/>");
		assertThat(resp).containsIgnoringCase("<display value=\"Child AAB1\"/>");
		assertThat(resp).doesNotContainIgnoringCase("<display value=\"Parent A1\"/>");

		// Validate ValueSet v2
		respParam = myClient
			.operation()
			.onInstance(myLocalValueSetId_v2)
			.named("expand")
			.withNoParameters(Parameters.class)
			.execute();
		expanded = (ValueSet) respParam.getParameter().get(0).getResource();

		resp = myFhirContext.newXmlParser().setPrettyPrint(true).encodeResourceToString(expanded);
		ourLog.info(resp);

		assertThat(resp).containsIgnoringCase("<display value=\"Child AAA2\"/>");
		assertThat(resp).containsIgnoringCase("<display value=\"Child AAB2\"/>");
		assertThat(resp).doesNotContainIgnoringCase("<display value=\"Parent A2\"/>");

	}

	@Test
	public void testExpandLocalVsCanonicalAgainstExternalCs() {
		createExternalCsAndLocalVs();
		assertNotNull(myLocalValueSetId_v1);
		assertNotNull(myLocalValueSetId_v2);

		Parameters respParam = myClient
			.operation()
			.onType(ValueSet.class)
			.named("expand")
			.withParameter(Parameters.class, "url", new UriType(URL_MY_VALUE_SET))
			.execute();

		// Canonical expand should only return most recently updated version, v2.
		assertThat(respParam.getParameter()).hasSize(1);
		ValueSet expanded = (ValueSet) respParam.getParameter().get(0).getResource();

		String resp = myFhirContext.newXmlParser().setPrettyPrint(true).encodeResourceToString(expanded);
		ourLog.info(resp);

		assertThat(resp).containsIgnoringCase("<display value=\"Child AAA2\"/>");
		assertThat(resp).containsIgnoringCase("<display value=\"Child AAB2\"/>");
		assertThat(resp).doesNotContainIgnoringCase("<display value=\"Parent A2\"/>");

	}

	@Test
	public void testExpandValueSetBasedOnCodeSystemWithChangedUrlAndVersion() {

		CodeSystem cs = new CodeSystem();
		cs.setId("CodeSystem/CS");
		cs.setContent(CodeSystemContentMode.COMPLETE);
		cs.setUrl("http://foo1");
		cs.setVersion("1");
		cs.addConcept().setCode("foo1").setDisplay("foo1");
		myClient.update().resource(cs).execute();

		ValueSet vs = new ValueSet();
		vs.setId("ValueSet/VS179789");
		vs.setUrl("http://bar");
		vs.getCompose().addInclude().setSystem("http://foo1").setVersion("1").addConcept().setCode("foo1");
		myClient.update().resource(vs).execute();

		ValueSet expanded = myClient
			.operation()
			.onInstance(new IdType("ValueSet/VS179789"))
			.named("$expand")
			.withNoParameters(Parameters.class)
			.returnResourceType(ValueSet.class)
			.execute();
		ourLog.debug("Expanded: {}", myFhirContext.newJsonParser().setPrettyPrint(true).encodeResourceToString(expanded));
		assertThat(expanded.getExpansion().getContains()).hasSize(1);

		// Update the CodeSystem Version and Codes
		cs = new CodeSystem();
		cs.setId("CodeSystem/CS");
		cs.setContent(CodeSystemContentMode.COMPLETE);
		cs.setUrl("http://foo2");
		cs.setVersion("2");
		cs.addConcept().setCode("foo2").setDisplay("foo2");
		myClient.update().resource(cs).execute();

		vs = new ValueSet();
		vs.setId("ValueSet/VS179789");
		vs.setUrl("http://bar");
		vs.getCompose().addInclude().setSystem("http://foo2").setVersion("2").addConcept().setCode("foo2");
		myClient.update().resource(vs).execute();

		expanded = myClient
			.operation()
			.onInstance(new IdType("ValueSet/VS179789"))
			.named("$expand")
			.withNoParameters(Parameters.class)
			.returnResourceType(ValueSet.class)
			.execute();
		ourLog.debug("Expanded: {}", myFhirContext.newJsonParser().setPrettyPrint(true).encodeResourceToString(expanded));
		assertThat(expanded.getExpansion().getContains()).hasSize(1);
	}


	@Test
	public void testUpdateValueSetTriggersAnotherPreExpansion() throws Exception {
		myStorageSettings.setPreExpandValueSets(true);

		loadAndPersistCodeSystemAndValueSetWithDesignations();

		CodeSystem codeSystem_v1 = myCodeSystemDao.read(myExtensionalCsId_v1);
		ourLog.debug("CodeSystem:\n" + myFhirContext.newJsonParser().setPrettyPrint(true).encodeResourceToString(codeSystem_v1));
		CodeSystem codeSystem_v2 = myCodeSystemDao.read(myExtensionalCsId_v2);
		ourLog.debug("CodeSystem:\n" + myFhirContext.newJsonParser().setPrettyPrint(true).encodeResourceToString(codeSystem_v2));

		ValueSet valueSet_v1 = myValueSetDao.read(myExtensionalVsId_v1);
		ourLog.debug("ValueSet:\n" + myFhirContext.newJsonParser().setPrettyPrint(true).encodeResourceToString(valueSet_v1));
		ValueSet valueSet_v2 = myValueSetDao.read(myExtensionalVsId_v2);
		ourLog.debug("ValueSet:\n" + myFhirContext.newJsonParser().setPrettyPrint(true).encodeResourceToString(valueSet_v2));

		String initialValueSetName_v1 = valueSet_v1.getName();
		validateTermValueSetNotExpanded(initialValueSetName_v1, "1", myExtensionalVsIdOnResourceTable_v1);
		String initialValueSetName_v2 = valueSet_v2.getName();
		validateTermValueSetNotExpanded(initialValueSetName_v2, "2", myExtensionalVsIdOnResourceTable_v2);
		myTermSvc.preExpandDeferredValueSetsToTerminologyTables();
		validateTermValueSetExpandedAndChildrenV1(initialValueSetName_v1, codeSystem_v1);
		validateTermValueSetExpandedAndChildrenV2(initialValueSetName_v2, codeSystem_v2);

		ValueSet updatedValueSet_v1 = valueSet_v1;
		updatedValueSet_v1.setName(valueSet_v1.getName().concat(" - MODIFIED"));
		persistSingleValueSet(updatedValueSet_v1, HttpVerb.PUT);
		updatedValueSet_v1 = myValueSetDao.read(myExtensionalVsId_v1);
		ourLog.debug("Updated ValueSet:\n" + myFhirContext.newJsonParser().setPrettyPrint(true).encodeResourceToString(updatedValueSet_v1));

		String updatedValueSetName_v1 = valueSet_v1.getName();
		validateTermValueSetNotExpanded(updatedValueSetName_v1,"1", myExtensionalVsIdOnResourceTable_v1);

		ValueSet updatedValueSet_v2 = valueSet_v2;
		updatedValueSet_v2.setName(valueSet_v2.getName().concat(" - MODIFIED"));
		persistSingleValueSet(updatedValueSet_v2, HttpVerb.PUT);
		updatedValueSet_v2 = myValueSetDao.read(myExtensionalVsId_v2);
		ourLog.debug("Updated ValueSet:\n" + myFhirContext.newJsonParser().setPrettyPrint(true).encodeResourceToString(updatedValueSet_v2));

		String updatedValueSetName_v2 = valueSet_v2.getName();
		validateTermValueSetNotExpanded(updatedValueSetName_v2,"2", myExtensionalVsIdOnResourceTable_v2);

		myTermSvc.preExpandDeferredValueSetsToTerminologyTables();
		validateTermValueSetExpandedAndChildrenV1(updatedValueSetName_v1, codeSystem_v1);
		validateTermValueSetExpandedAndChildrenV2(updatedValueSetName_v2, codeSystem_v2);
	}


	@Test
	public void testUpdateValueSetTriggersAnotherPreExpansionUsingTransactionBundle() throws Exception {
		myStorageSettings.setPreExpandValueSets(true);

		loadAndPersistCodeSystemAndValueSetWithDesignations();

		CodeSystem codeSystem_v1 = myCodeSystemDao.read(myExtensionalCsId_v1);
		ourLog.debug("CodeSystem:\n" + myFhirContext.newJsonParser().setPrettyPrint(true).encodeResourceToString(codeSystem_v1));
		CodeSystem codeSystem_v2 = myCodeSystemDao.read(myExtensionalCsId_v2);
		ourLog.debug("CodeSystem:\n" + myFhirContext.newJsonParser().setPrettyPrint(true).encodeResourceToString(codeSystem_v2));

		ValueSet valueSet_v1 = myValueSetDao.read(myExtensionalVsId_v1);
		ourLog.debug("ValueSet:\n" + myFhirContext.newJsonParser().setPrettyPrint(true).encodeResourceToString(valueSet_v1));
		ValueSet valueSet_v2 = myValueSetDao.read(myExtensionalVsId_v2);
		ourLog.debug("ValueSet:\n" + myFhirContext.newJsonParser().setPrettyPrint(true).encodeResourceToString(valueSet_v2));

		String initialValueSetName_v1 = valueSet_v1.getName();
		validateTermValueSetNotExpanded(initialValueSetName_v1, "1", myExtensionalVsIdOnResourceTable_v1);
		String initialValueSetName_v2 = valueSet_v2.getName();
		validateTermValueSetNotExpanded(initialValueSetName_v2, "2", myExtensionalVsIdOnResourceTable_v2);
		myTermSvc.preExpandDeferredValueSetsToTerminologyTables();
		validateTermValueSetExpandedAndChildrenV1(initialValueSetName_v1, codeSystem_v1);
		validateTermValueSetExpandedAndChildrenV2(initialValueSetName_v2, codeSystem_v2);

		ValueSet updatedValueSet_v1 = valueSet_v1;
		updatedValueSet_v1.setName(valueSet_v1.getName().concat(" - MODIFIED"));

		logAllValueSets();

		String url = myClient.getServerBase().concat("/").concat(myExtensionalVsId_v1.getValueAsString());
		Bundle bundle = new Bundle();
		bundle.setType(Bundle.BundleType.TRANSACTION);
		bundle
			.addEntry()
			.setFullUrl(url)
			.setResource(updatedValueSet_v1)
			.getRequest()
			.setMethod(Bundle.HTTPVerb.PUT)
			.setUrl("ValueSet/" + updatedValueSet_v1.getIdElement().getIdPart());
		ourLog.debug("Transaction Bundle:\n" + myFhirContext.newJsonParser().setPrettyPrint(true).encodeResourceToString(bundle));
		myClient.transaction().withBundle(bundle).execute();

		updatedValueSet_v1 = myValueSetDao.read(myExtensionalVsId_v1);
		ourLog.debug("Updated ValueSet:\n" + myFhirContext.newJsonParser().setPrettyPrint(true).encodeResourceToString(updatedValueSet_v1));

		String updatedValueSetName_v1 = valueSet_v1.getName();
		validateTermValueSetNotExpanded(updatedValueSetName_v1, "1", myExtensionalVsIdOnResourceTable_v1);

		ValueSet updatedValueSet_v2 = valueSet_v2;
		updatedValueSet_v2.setName(valueSet_v2.getName().concat(" - MODIFIED"));

		url = myClient.getServerBase().concat("/").concat(myExtensionalVsId_v2.getValueAsString());
		bundle = new Bundle();
		bundle.setType(Bundle.BundleType.TRANSACTION);
		bundle
			.addEntry()
			.setFullUrl(url)
			.setResource(updatedValueSet_v2)
			.getRequest()
			.setMethod(Bundle.HTTPVerb.PUT)
			.setUrl("ValueSet/" + updatedValueSet_v2.getIdElement().getIdPart());
		ourLog.info("Transaction Bundle:\n" + myFhirContext.newJsonParser().setPrettyPrint(true).encodeResourceToString(bundle));
		myClient.transaction().withBundle(bundle).execute();

		updatedValueSet_v2 = myValueSetDao.read(myExtensionalVsId_v2);
		ourLog.debug("Updated ValueSet:\n" + myFhirContext.newJsonParser().setPrettyPrint(true).encodeResourceToString(updatedValueSet_v2));

		String updatedValueSetName_v2 = valueSet_v2.getName();
		validateTermValueSetNotExpanded(updatedValueSetName_v2, "2", myExtensionalVsIdOnResourceTable_v2);

		myTermSvc.preExpandDeferredValueSetsToTerminologyTables();
		validateTermValueSetExpandedAndChildrenV1(updatedValueSetName_v1, codeSystem_v1);
		validateTermValueSetExpandedAndChildrenV2(updatedValueSetName_v2, codeSystem_v2);

	}

	private void validateTermValueSetNotExpanded(String theValueSetName, String theVersion, JpaPid theId) {
		runInTransaction(() -> {
			Optional<TermValueSet> optionalValueSetByResourcePid = myTermValueSetDao.findByResourcePid(theId);
			assertTrue(optionalValueSetByResourcePid.isPresent());

			Optional<TermValueSet> optionalValueSetByUrl = myTermValueSetDao.findTermValueSetByUrlAndVersion("http://www.healthintersections.com.au/fhir/ValueSet/extensional-case-2", theVersion);
			assertTrue(optionalValueSetByUrl.isPresent());

			TermValueSet termValueSet = optionalValueSetByUrl.get();
			assertThat(termValueSet).isSameAs(optionalValueSetByResourcePid.get());
			ourLog.info("ValueSet:\n" + termValueSet.toString());
			assertEquals("http://www.healthintersections.com.au/fhir/ValueSet/extensional-case-2", termValueSet.getUrl());
			assertEquals(theValueSetName, termValueSet.getName());
			assertEquals(0, termValueSet.getConcepts().size());
			assertEquals(TermValueSetPreExpansionStatusEnum.NOT_EXPANDED, termValueSet.getExpansionStatus());
		});
	}

	private void validateTermValueSetExpandedAndChildrenV1(String theValueSetName, CodeSystem theCodeSystem) {
		runInTransaction(() -> {
			Optional<TermValueSet> optionalValueSetByResourcePid = myTermValueSetDao.findByResourcePid(myExtensionalVsIdOnResourceTable_v1);
			assertTrue(optionalValueSetByResourcePid.isPresent());

			Optional<TermValueSet> optionalValueSetByUrl = myTermValueSetDao.findTermValueSetByUrlAndVersion("http://www.healthintersections.com.au/fhir/ValueSet/extensional-case-2", "1");
			assertTrue(optionalValueSetByUrl.isPresent());

			TermValueSet termValueSet = optionalValueSetByUrl.get();
			assertThat(termValueSet).isSameAs(optionalValueSetByResourcePid.get());
			ourLog.info("ValueSet:\n" + termValueSet.toString());
			assertEquals("http://www.healthintersections.com.au/fhir/ValueSet/extensional-case-2", termValueSet.getUrl());
			assertEquals(theValueSetName, termValueSet.getName());
			assertEquals(theCodeSystem.getConcept().size(), termValueSet.getConcepts().size());
			assertEquals(TermValueSetPreExpansionStatusEnum.EXPANDED, termValueSet.getExpansionStatus());

			TermValueSetConcept concept = assertTermValueSetContainsConceptAndIsInDeclaredOrder(termValueSet, "http://acme.org", "8450-9", "Systolic blood pressure--expiration", 2);
			assertTermConceptContainsDesignation(concept, "nl", "http://snomed.info/sct", "900000000000013009", "Synonym", "Systolische bloeddruk - expiratie");
			assertTermConceptContainsDesignation(concept, "sv", "http://snomed.info/sct", "900000000000013009", "Synonym", "Systoliskt blodtryck - utgång");

			assertTermValueSetContainsConceptAndIsInDeclaredOrder(termValueSet, "http://acme.org", "11378-7", "Systolic blood pressure at First encounter", 0);

			// ...

			TermValueSetConcept otherConcept = assertTermValueSetContainsConceptAndIsInDeclaredOrder(termValueSet, "http://acme.org", "8491-3", "Systolic blood pressure 1 hour minimum", 1);
			assertTermConceptContainsDesignation(otherConcept, "nl", "http://snomed.info/sct", "900000000000013009", "Synonym", "Systolische bloeddruk minimaal 1 uur");

			assertTermValueSetContainsConceptAndIsInDeclaredOrder(termValueSet, "http://acme.org", "8492-1", "Systolic blood pressure 8 hour minimum", 0);

		});
	}

	private void validateTermValueSetExpandedAndChildrenV2(String theValueSetName, CodeSystem theCodeSystem) {

		runInTransaction(() -> {
			Optional<TermValueSet> optionalValueSetByResourcePid = myTermValueSetDao.findByResourcePid(myExtensionalVsIdOnResourceTable_v2);
			assertTrue(optionalValueSetByResourcePid.isPresent());

			Optional<TermValueSet> optionalValueSetByUrl = myTermValueSetDao.findTermValueSetByUrlAndVersion("http://www.healthintersections.com.au/fhir/ValueSet/extensional-case-2", "2");
			assertTrue(optionalValueSetByUrl.isPresent());

			TermValueSet termValueSet = optionalValueSetByUrl.get();
			assertThat(termValueSet).isSameAs(optionalValueSetByResourcePid.get());
			ourLog.info("ValueSet:\n" + termValueSet.toString());
			assertEquals("http://www.healthintersections.com.au/fhir/ValueSet/extensional-case-2", termValueSet.getUrl());
			assertEquals(theValueSetName, termValueSet.getName());
			assertEquals(theCodeSystem.getConcept().size(), termValueSet.getConcepts().size());
			assertEquals(TermValueSetPreExpansionStatusEnum.EXPANDED, termValueSet.getExpansionStatus());

			TermValueSetConcept concept = assertTermValueSetContainsConceptAndIsInDeclaredOrder(termValueSet, "http://acme.org", "8450-9", "Systolic blood pressure--expiration v2", 2);
			assertEquals("2", concept.getSystemVersion());
			assertTermConceptContainsDesignation(concept, "nl", "http://snomed.info/sct", "900000000000013009", "Synonym", "Systolische bloeddruk - expiratie");
			assertTermConceptContainsDesignation(concept, "sv", "http://snomed.info/sct", "900000000000013009", "Synonym", "Systoliskt blodtryck - utgång");

			TermValueSetConcept termValueSetConcept1 = assertTermValueSetContainsConceptAndIsInDeclaredOrder(termValueSet, "http://acme.org", "11378-7", "Systolic blood pressure at First encounter v2", 0);
			assertEquals("2", termValueSetConcept1.getSystemVersion());

			// ...

			TermValueSetConcept otherConcept = assertTermValueSetContainsConceptAndIsInDeclaredOrder(termValueSet, "http://acme.org", "8491-3", "Systolic blood pressure 1 hour minimum v2", 1);
			assertEquals("2", otherConcept.getSystemVersion());
			assertTermConceptContainsDesignation(otherConcept, "nl", "http://snomed.info/sct", "900000000000013009", "Synonym", "Systolische bloeddruk minimaal 1 uur");

			TermValueSetConcept termValueSetConcept = assertTermValueSetContainsConceptAndIsInDeclaredOrder(termValueSet, "http://acme.org", "8492-1", "Systolic blood pressure 8 hour minimum v2", 0);
			assertEquals("2", termValueSetConcept.getSystemVersion());
		});
	}

	@Test
	public void testCreateDuplicateValueSetVersion() {
		createExternalCsAndLocalVs();
		try {
			persistLocalVs(createLocalVs(URL_MY_CODE_SYSTEM, "1"));
			fail("");
		} catch (UnprocessableEntityException theE) {
			assertThat(theE.getMessage()).contains("Can not create multiple ValueSet resources with ValueSet.url \"" + URL_MY_VALUE_SET + "\" and ValueSet.version \"1\", already have one with resource ID: ");
		}

	}

	@Test
	public void testValidateCodeOperationByCodeAndSystem() throws Exception {
		loadAndPersistCodeSystemAndValueSet();

		// With correct system version specified. Should pass.
		Parameters respParam = myClient
			.operation()
			.onType(ValueSet.class)
			.named("validate-code")
			.withParameter(Parameters.class, "code", new CodeType("8495-4"))
			.andParameter("system", new UriType("http://acme.org"))
			.andParameter("systemVersion", new StringType("1"))
			.andParameter("url", new UriType("http://www.healthintersections.com.au/fhir/ValueSet/extensional-case-2"))
			.andParameter("valueSetVersion", new StringType("1"))
			.execute();

		String resp = myFhirContext.newXmlParser().setPrettyPrint(true).encodeResourceToString(respParam);
		ourLog.info(resp);

		assertTrue(((BooleanType) respParam.getParameter().get(0).getValue()).booleanValue());

		respParam = myClient
			.operation()
			.onType(ValueSet.class)
			.named("validate-code")
			.withParameter(Parameters.class, "code", new CodeType("8495-4"))
			.andParameter("system", new UriType("http://acme.org"))
			.andParameter("systemVersion", new StringType("2"))
			.andParameter("url", new UriType("http://www.healthintersections.com.au/fhir/ValueSet/extensional-case-2"))
			.andParameter("valueSetVersion", new StringType("2"))
			.execute();

		resp = myFhirContext.newXmlParser().setPrettyPrint(true).encodeResourceToString(respParam);
		ourLog.info(resp);

		assertTrue(((BooleanType) respParam.getParameter().get(0).getValue()).booleanValue());

		// With incorrect version specified. Should fail.
		respParam = myClient
			.operation()
			.onType(ValueSet.class)
			.named("validate-code")
			.withParameter(Parameters.class, "code", new CodeType("8495-4"))
			.andParameter("system", new UriType("http://acme.org"))
			.andParameter("systemVersion", new StringType("1"))
			.andParameter("url", new UriType("http://www.healthintersections.com.au/fhir/ValueSet/extensional-case-2"))
			.andParameter("valueSetVersion", new StringType("2"))
			.execute();

		resp = myFhirContext.newXmlParser().setPrettyPrint(true).encodeResourceToString(respParam);
		ourLog.info(resp);

		assertFalse(((BooleanType) respParam.getParameter().get(0).getValue()).booleanValue());

		respParam = myClient
			.operation()
			.onType(ValueSet.class)
			.named("validate-code")
			.withParameter(Parameters.class, "code", new CodeType("8495-4"))
			.andParameter("system", new UriType("http://acme.org"))
			.andParameter("systemVersion", new StringType("2"))
			.andParameter("url", new UriType("http://www.healthintersections.com.au/fhir/ValueSet/extensional-case-2"))
			.andParameter("valueSetVersion", new StringType("1"))
			.execute();

		resp = myFhirContext.newXmlParser().setPrettyPrint(true).encodeResourceToString(respParam);
		ourLog.info(resp);

		assertFalse(((BooleanType) respParam.getParameter().get(0).getValue()).booleanValue());

	}

	@Test
	public void testValidateCodeOperationOnInstanceByCodeAndSystem() throws Exception {
		loadAndPersistCodeSystemAndValueSet();

		// With correct system version specified. Should pass.
		Parameters respParam = myClient
			.operation()
			.onInstance(myExtensionalVsId_v1)
			.named("validate-code")
			.withParameter(Parameters.class, "code", new CodeType("8495-4"))
			.andParameter("system", new UriType("http://acme.org"))
			.andParameter("systemVersion", new StringType("1"))
			.execute();

		String resp = myFhirContext.newXmlParser().setPrettyPrint(true).encodeResourceToString(respParam);
		ourLog.info(resp);

		assertTrue(((BooleanType) respParam.getParameter().get(0).getValue()).booleanValue());

		respParam = myClient
			.operation()
			.onInstance(myExtensionalVsId_v2)
			.named("validate-code")
			.withParameter(Parameters.class, "code", new CodeType("8495-4"))
			.andParameter("system", new UriType("http://acme.org"))
			.andParameter("systemVersion", new StringType("2"))
			.execute();

		resp = myFhirContext.newXmlParser().setPrettyPrint(true).encodeResourceToString(respParam);
		ourLog.info(resp);

		assertTrue(((BooleanType) respParam.getParameter().get(0).getValue()).booleanValue());

		// With incorrect version specified. Should fail.
		respParam = myClient
			.operation()
			.onInstance(myExtensionalVsId_v1)
			.named("validate-code")
			.withParameter(Parameters.class, "code", new CodeType("8495-4"))
			.andParameter("system", new UriType("http://acme.org"))
			.andParameter("systemVersion", new StringType("2"))
			.execute();

		resp = myFhirContext.newXmlParser().setPrettyPrint(true).encodeResourceToString(respParam);
		ourLog.info(resp);

		assertFalse(((BooleanType) respParam.getParameter().get(0).getValue()).booleanValue());

		respParam = myClient
			.operation()
			.onInstance(myExtensionalVsId_v2)
			.named("validate-code")
			.withParameter(Parameters.class, "code", new CodeType("8495-4"))
			.andParameter("system", new UriType("http://acme.org"))
			.andParameter("systemVersion", new StringType("1"))
			.execute();

		resp = myFhirContext.newXmlParser().setPrettyPrint(true).encodeResourceToString(respParam);
		ourLog.info(resp);

		assertFalse(((BooleanType) respParam.getParameter().get(0).getValue()).booleanValue());

	}

	@Test
	public void testValidateCodeOperationByCoding() throws Exception {
		loadAndPersistCodeSystemAndValueSet();

		Coding codingToValidate_v1 = new Coding("http://acme.org", "8495-4", "Systolic blood pressure 24 hour minimum");
		codingToValidate_v1.setVersion("1");

		Coding codingToValidate_v2 = new Coding("http://acme.org", "8495-4", "Systolic blood pressure 24 hour minimum v2");
		codingToValidate_v2.setVersion("2");

		// With correct system version specified. Should pass.
		Parameters respParam = myClient
			.operation()
			.onType(ValueSet.class)
			.named("validate-code")
			.withParameter(Parameters.class, "coding", codingToValidate_v1)
			.andParameter("url", new UriType("http://www.healthintersections.com.au/fhir/ValueSet/extensional-case-2"))
			.andParameter("valueSetVersion", new StringType("1"))
			.execute();

		String resp = myFhirContext.newXmlParser().setPrettyPrint(true).encodeResourceToString(respParam);
		ourLog.info(resp);

		assertTrue(((BooleanType) respParam.getParameter().get(0).getValue()).booleanValue());

		respParam = myClient
			.operation()
			.onType(ValueSet.class)
			.named("validate-code")
			.withParameter(Parameters.class, "coding", codingToValidate_v2)
			.andParameter("url", new UriType("http://www.healthintersections.com.au/fhir/ValueSet/extensional-case-2"))
			.andParameter("valueSetVersion", new StringType("2"))
			.execute();

		resp = myFhirContext.newXmlParser().setPrettyPrint(true).encodeResourceToString(respParam);
		ourLog.info(resp);

		assertTrue(((BooleanType) respParam.getParameter().get(0).getValue()).booleanValue());

		// With incorrect version specified. Should fail.
		respParam = myClient
			.operation()
			.onType(ValueSet.class)
			.named("validate-code")
			.withParameter(Parameters.class, "coding", codingToValidate_v1)
			.andParameter("url", new UriType("http://www.healthintersections.com.au/fhir/ValueSet/extensional-case-2"))
			.andParameter("valueSetVersion", new StringType("2"))
			.execute();

		resp = myFhirContext.newXmlParser().setPrettyPrint(true).encodeResourceToString(respParam);
		ourLog.info(resp);

		assertFalse(((BooleanType) respParam.getParameter().get(0).getValue()).booleanValue());

		respParam = myClient
			.operation()
			.onType(ValueSet.class)
			.named("validate-code")
			.withParameter(Parameters.class, "coding", codingToValidate_v2)
			.andParameter("url", new UriType("http://www.healthintersections.com.au/fhir/ValueSet/extensional-case-2"))
			.andParameter("valueSetVersion", new StringType("1"))
			.execute();

		resp = myFhirContext.newXmlParser().setPrettyPrint(true).encodeResourceToString(respParam);
		ourLog.info(resp);

		assertFalse(((BooleanType) respParam.getParameter().get(0).getValue()).booleanValue());

	}

	@Test
	public void testValidateCodeOperationByCodeableConcept() throws Exception {
		loadAndPersistCodeSystemAndValueSet();

		Coding codingToValidate = new Coding("http://acme.org", "8495-4", "Systolic blood pressure 24 hour minimum");
		codingToValidate.setVersion("1");
		CodeableConcept codeableConceptToValidate_v1 = new CodeableConcept(codingToValidate);

		codingToValidate = new Coding("http://acme.org", "8495-4", "Systolic blood pressure 24 hour minimum v2");
		codingToValidate.setVersion("2");
		CodeableConcept codeableConceptToValidate_v2 = new CodeableConcept(codingToValidate);

		// With correct system version specified. Should pass.
		Parameters respParam = myClient
			.operation()
			.onType(ValueSet.class)
			.named("validate-code")
			.withParameter(Parameters.class, "codeableConcept", codeableConceptToValidate_v1)
			.andParameter("url", new UriType("http://www.healthintersections.com.au/fhir/ValueSet/extensional-case-2"))
			.andParameter("valueSetVersion", new StringType("1"))
			.execute();

		String resp = myFhirContext.newXmlParser().setPrettyPrint(true).encodeResourceToString(respParam);
		ourLog.info(resp);

		assertTrue(((BooleanType) respParam.getParameter().get(0).getValue()).booleanValue());

		respParam = myClient
			.operation()
			.onType(ValueSet.class)
			.named("validate-code")
			.withParameter(Parameters.class, "codeableConcept", codeableConceptToValidate_v2)
			.andParameter("url", new UriType("http://www.healthintersections.com.au/fhir/ValueSet/extensional-case-2"))
			.andParameter("valueSetVersion", new StringType("2"))
			.execute();

		resp = myFhirContext.newXmlParser().setPrettyPrint(true).encodeResourceToString(respParam);
		ourLog.info(resp);

		assertTrue(((BooleanType) respParam.getParameter().get(0).getValue()).booleanValue());

		// With incorrect version specified. Should fail.
		respParam = myClient
			.operation()
			.onType(ValueSet.class)
			.named("validate-code")
			.withParameter(Parameters.class, "codeableConcept", codeableConceptToValidate_v1)
			.andParameter("url", new UriType("http://www.healthintersections.com.au/fhir/ValueSet/extensional-case-2"))
			.andParameter("valueSetVersion", new StringType("2"))
			.execute();

		resp = myFhirContext.newXmlParser().setPrettyPrint(true).encodeResourceToString(respParam);
		ourLog.info(resp);

		assertFalse(((BooleanType) respParam.getParameter().get(0).getValue()).booleanValue());

		respParam = myClient
			.operation()
			.onType(ValueSet.class)
			.named("validate-code")
			.withParameter(Parameters.class, "codeableConcept", codeableConceptToValidate_v2)
			.andParameter("url", new UriType("http://www.healthintersections.com.au/fhir/ValueSet/extensional-case-2"))
			.andParameter("valueSetVersion", new StringType("1"))
			.execute();

		resp = myFhirContext.newXmlParser().setPrettyPrint(true).encodeResourceToString(respParam);
		ourLog.info(resp);

		assertFalse(((BooleanType) respParam.getParameter().get(0).getValue()).booleanValue());

	}
	
	@AfterEach
	public void afterResetPreExpansionDefault() {
		myStorageSettings.setPreExpandValueSets(new JpaStorageSettings().isPreExpandValueSets());
	}

	public CodeSystem createExternalCs(IFhirResourceDao<CodeSystem> theCodeSystemDao, IResourceTableDao theResourceTableDao, ITermCodeSystemStorageSvc theTermCodeSystemStorageSvc, ServletRequestDetails theRequestDetails) {
		CodeSystem codeSystem = new CodeSystem();
		codeSystem.setUrl(URL_MY_CODE_SYSTEM);
		codeSystem.setVersion("SYSTEM VERSION");
		codeSystem.setContent(CodeSystemContentMode.NOTPRESENT);
		IIdType id = theCodeSystemDao.create(codeSystem, theRequestDetails).getId().toUnqualified();

		ResourceTable table = theResourceTableDao.findById(id.getIdPartAsLong()).orElseThrow(IllegalStateException::new);

		TermCodeSystemVersion cs = new TermCodeSystemVersion();
		cs.setResource(table);

		TermConcept parentA = new TermConcept(cs, "ParentA").setDisplay("Parent A");
		cs.getConcepts().add(parentA);

		TermConcept childAA = new TermConcept(cs, "childAA").setDisplay("Child AA");
		parentA.addChild(childAA, RelationshipTypeEnum.ISA);

		TermConcept childAAA = new TermConcept(cs, "childAAA").setDisplay("Child AAA");
		childAA.addChild(childAAA, RelationshipTypeEnum.ISA);

		TermConcept childAAB = new TermConcept(cs, "childAAB").setDisplay("Child AAB");
		childAA.addChild(childAAB, RelationshipTypeEnum.ISA);

		TermConcept childAB = new TermConcept(cs, "childAB").setDisplay("Child AB");
		parentA.addChild(childAB, RelationshipTypeEnum.ISA);

		TermConcept parentB = new TermConcept(cs, "ParentB").setDisplay("Parent B");
		cs.getConcepts().add(parentB);

		theTermCodeSystemStorageSvc.storeNewCodeSystemVersion(URL_MY_CODE_SYSTEM, "SYSTEM NAME", "SYSTEM VERSION", cs, table);
		return codeSystem;
	}

	public static CodeSystem createExternalCs(BaseJpaTest theTest, IFhirResourceDao<CodeSystem> theCodeSystemDao, IResourceTableDao theResourceTableDao, ITermCodeSystemStorageSvc theTermCodeSystemStorageSvc, ServletRequestDetails theRequestDetails, String theCodeSystemVersion) {
		CodeSystem codeSystem = new CodeSystem();
		codeSystem.setUrl(URL_MY_CODE_SYSTEM);
		codeSystem.setVersion(theCodeSystemVersion);
		codeSystem.setContent(CodeSystemContentMode.NOTPRESENT);
		IIdType id = theCodeSystemDao.create(codeSystem, theRequestDetails).getId().toUnqualified();

		ResourceTable table = theTest.runInTransaction(()->theResourceTableDao.findById(JpaPid.fromId(id.getIdPartAsLong())).orElseThrow(IllegalStateException::new));

		TermCodeSystemVersion cs = new TermCodeSystemVersion();
		cs.setResource(table);

		TermConcept parentA = new TermConcept(cs, "ParentA").setDisplay("Parent A" + theCodeSystemVersion);
		cs.getConcepts().add(parentA);

		TermConcept childAA = new TermConcept(cs, "childAA").setDisplay("Child AA" + theCodeSystemVersion);
		parentA.addChild(childAA, RelationshipTypeEnum.ISA);

		TermConcept childAAA = new TermConcept(cs, "childAAA").setDisplay("Child AAA" + theCodeSystemVersion);
		childAA.addChild(childAAA, RelationshipTypeEnum.ISA);

		TermConcept childAAB = new TermConcept(cs, "childAAB").setDisplay("Child AAB" + theCodeSystemVersion);
		childAA.addChild(childAAB, RelationshipTypeEnum.ISA);

		TermConcept childAB = new TermConcept(cs, "childAB").setDisplay("Child AB" + theCodeSystemVersion);
		parentA.addChild(childAB, RelationshipTypeEnum.ISA);

		TermConcept parentB = new TermConcept(cs, "ParentB").setDisplay("Parent B" + theCodeSystemVersion);
		cs.getConcepts().add(parentB);

		theTermCodeSystemStorageSvc.storeNewCodeSystemVersion(URL_MY_CODE_SYSTEM, "SYSTEM NAME", theCodeSystemVersion, cs, table);
		return codeSystem;
	}

}
