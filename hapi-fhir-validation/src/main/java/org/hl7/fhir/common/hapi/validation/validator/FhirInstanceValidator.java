package org.hl7.fhir.common.hapi.validation.validator;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.context.FhirVersionEnum;
import ca.uhn.fhir.context.support.DefaultProfileValidationSupport;
import ca.uhn.fhir.context.support.IValidationSupport;
import ca.uhn.fhir.validation.IInstanceValidatorModule;
import ca.uhn.fhir.validation.IValidationContext;
import com.google.common.annotations.VisibleForTesting;
import jakarta.annotation.Nonnull;
import org.apache.commons.lang3.Validate;
import org.hl7.fhir.exceptions.FHIRException;
import org.hl7.fhir.exceptions.PathEngineException;
import org.hl7.fhir.r5.fhirpath.FHIRPathEngine;
import org.hl7.fhir.r5.fhirpath.FHIRPathUtilityClasses.FunctionDetails;
import org.hl7.fhir.r5.fhirpath.TypeDetails;
import org.hl7.fhir.r5.model.Base;
import org.hl7.fhir.r5.model.ValueSet;
import org.hl7.fhir.r5.utils.validation.IValidationPolicyAdvisor;
import org.hl7.fhir.r5.utils.validation.IValidatorResourceFetcher;
import org.hl7.fhir.r5.utils.validation.constants.BestPracticeWarningLevel;
import org.hl7.fhir.utilities.validation.ValidationMessage;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

@SuppressWarnings({"PackageAccessibility", "Duplicates"})
public class FhirInstanceValidator extends BaseValidatorBridge implements IInstanceValidatorModule {

	private boolean myAnyExtensionsAllowed = true;
	private BestPracticeWarningLevel myBestPracticeWarningLevel;
	private IValidationSupport myValidationSupport;
	private boolean noTerminologyChecks = false;
	private boolean noExtensibleWarnings = false;
	private boolean noBindingMsgSuppressed = false;
	private WorkerContextValidationSupportAdapter myWrappedWorkerContext;
	private boolean errorForUnknownProfiles = true;

	private boolean assumeValidRestReferences;
	private List<String> myExtensionDomains = Collections.emptyList();
	private IValidatorResourceFetcher validatorResourceFetcher;
	private IValidationPolicyAdvisor validatorPolicyAdvisor = new FhirDefaultPolicyAdvisor();
	private boolean myAllowExamples;

	/**
	 * Constructor
	 * <p>
	 * Uses {@link DefaultProfileValidationSupport} for {@link IValidationSupport validation support}
	 */
	public FhirInstanceValidator(FhirContext theContext) {
		this(theContext.getValidationSupport());
	}

	/**
	 * Constructor which uses the given validation support
	 *
	 * @param theValidationSupport The validation support
	 */
	public FhirInstanceValidator(IValidationSupport theValidationSupport) {
		if (theValidationSupport.getFhirContext().getVersion().getVersion() == FhirVersionEnum.DSTU2) {
			myValidationSupport = new HapiToHl7OrgDstu2ValidatingSupportWrapper(theValidationSupport);
		} else {
			myValidationSupport = theValidationSupport;
		}
	}

	/**
	 * Every element in a resource or data type includes an optional <it>extension</it> child element
	 * which is identified by it's {@code url attribute}. There exists a number of predefined
	 * extension urls or extension domains:<ul>
	 * <li>any url which contains {@code example.org}, {@code nema.org}, or {@code acme.com}.</li>
	 * <li>any url which starts with {@code http://hl7.org/fhir/StructureDefinition/}.</li>
	 * </ul>
	 * It is possible to extend this list of known extension by defining custom extensions:
	 * Any url which starts which one of the elements in the list of custom extension domains is
	 * considered as known.
	 * <p>
	 * Any unknown extension domain will result in an information message when validating a resource.
	 * </p>
	 */
	public FhirInstanceValidator setCustomExtensionDomains(List<String> extensionDomains) {
		this.myExtensionDomains = extensionDomains;
		return this;
	}

	/**
	 * Every element in a resource or data type includes an optional <it>extension</it> child element
	 * which is identified by it's {@code url attribute}. There exists a number of predefined
	 * extension urls or extension domains:<ul>
	 * <li>any url which contains {@code example.org}, {@code nema.org}, or {@code acme.com}.</li>
	 * <li>any url which starts with {@code http://hl7.org/fhir/StructureDefinition/}.</li>
	 * </ul>
	 * It is possible to extend this list of known extension by defining custom extensions:
	 * Any url which starts which one of the elements in the list of custom extension domains is
	 * considered as known.
	 * <p>
	 * Any unknown extension domain will result in an information message when validating a resource.
	 * </p>
	 */
	public FhirInstanceValidator setCustomExtensionDomains(String... extensionDomains) {
		this.myExtensionDomains = Arrays.asList(extensionDomains);
		return this;
	}

	/**
	 * Returns the "best practice" warning level (default is {@link BestPracticeWarningLevel#Hint}).
	 * <p>
	 * The FHIR Instance Validator has a number of checks for best practices in terms of FHIR usage. If this setting is
	 * set to {@link BestPracticeWarningLevel#Error}, any resource data which does not meet these best practices will be
	 * reported at the ERROR level. If this setting is set to {@link BestPracticeWarningLevel#Ignore}, best practice
	 * guielines will be ignored.
	 * </p>
	 *
	 * @see #setBestPracticeWarningLevel(BestPracticeWarningLevel)
	 */
	public BestPracticeWarningLevel getBestPracticeWarningLevel() {
		return myBestPracticeWarningLevel;
	}

	/**
	 * Sets the "best practice warning level". When validating, any deviations from best practices will be reported at
	 * this level.
	 * <p>
	 * The FHIR Instance Validator has a number of checks for best practices in terms of FHIR usage. If this setting is
	 * set to {@link BestPracticeWarningLevel#Error}, any resource data which does not meet these best practices will be
	 * reported at the ERROR level. If this setting is set to {@link BestPracticeWarningLevel#Ignore}, best practice
	 * guielines will be ignored.
	 * </p>
	 *
	 * @param theBestPracticeWarningLevel The level, must not be <code>null</code>
	 */
	public void setBestPracticeWarningLevel(BestPracticeWarningLevel theBestPracticeWarningLevel) {
		Validate.notNull(theBestPracticeWarningLevel);
		myBestPracticeWarningLevel = theBestPracticeWarningLevel;
	}

	/**
	 * Returns the {@link IValidationSupport validation support} in use by this validator. Default is an instance of
	 * DefaultProfileValidationSupport if the no-arguments constructor for this object was used.
	 */
	public IValidationSupport getValidationSupport() {
		return myValidationSupport;
	}

	/**
	 * Sets the {@link IValidationSupport validation support} in use by this validator. Default is an instance of
	 * DefaultProfileValidationSupport if the no-arguments constructor for this object was used.
	 */
	public void setValidationSupport(IValidationSupport theValidationSupport) {
		myValidationSupport = theValidationSupport;
		myWrappedWorkerContext = null;
	}

	/**
	 * Sets the {@link IValidationSupport validation support} in use by this validator, as well
	 * as a {@link WorkerContextValidationSupportAdapter}. This is useful if a single instance of
	 * the latter should be shared in multiple places.
	 *
	 * @since 8.4.0
	 */
	public void setWrappedWorkerContext(
			IValidationSupport theValidationSupport, WorkerContextValidationSupportAdapter theWrappedWorkerContext) {
		myValidationSupport = theValidationSupport;
		myWrappedWorkerContext = theWrappedWorkerContext;
	}

	/**
	 * If set to {@literal true} (default is true) extensions which are not known to the
	 * validator (e.g. because they have not been explicitly declared in a profile) will
	 * be validated but will not cause an error.
	 */
	public boolean isAnyExtensionsAllowed() {
		return myAnyExtensionsAllowed;
	}

	/**
	 * If set to {@literal true} (default is true) extensions which are not known to the
	 * validator (e.g. because they have not been explicitly declared in a profile) will
	 * be validated but will not cause an error.
	 */
	public void setAnyExtensionsAllowed(boolean theAnyExtensionsAllowed) {
		myAnyExtensionsAllowed = theAnyExtensionsAllowed;
	}

	public boolean isErrorForUnknownProfiles() {
		return errorForUnknownProfiles;
	}

	public void setErrorForUnknownProfiles(boolean errorForUnknownProfiles) {
		this.errorForUnknownProfiles = errorForUnknownProfiles;
	}

	/**
	 * If set to {@literal true} (default is false) the valueSet will not be validate
	 */
	public boolean isNoTerminologyChecks() {
		return noTerminologyChecks;
	}

	/**
	 * If set to {@literal true} (default is false) the valueSet will not be validate
	 */
	public void setNoTerminologyChecks(final boolean theNoTerminologyChecks) {
		noTerminologyChecks = theNoTerminologyChecks;
	}

	/**
	 * If set to {@literal true} (default is false) no extensible warnings suppressed
	 */
	public boolean isNoExtensibleWarnings() {
		return noExtensibleWarnings;
	}

	/**
	 * If set to {@literal true} (default is false) no extensible warnings is suppressed
	 */
	public void setNoExtensibleWarnings(final boolean theNoExtensibleWarnings) {
		noExtensibleWarnings = theNoExtensibleWarnings;
	}

	/**
	 * If set to {@literal true} (default is false) no binding message is suppressed
	 */
	public boolean isNoBindingMsgSuppressed() {
		return noBindingMsgSuppressed;
	}

	/**
	 * If set to {@literal true} (default is false) no binding message is suppressed
	 */
	public void setNoBindingMsgSuppressed(final boolean theNoBindingMsgSuppressed) {
		noBindingMsgSuppressed = theNoBindingMsgSuppressed;
	}

	public List<String> getExtensionDomains() {
		return myExtensionDomains;
	}

	@Override
	protected List<ValidationMessage> validate(IValidationContext<?> theValidationCtx) {
		WorkerContextValidationSupportAdapter wrappedWorkerContext = provideWorkerContext();

		return new ValidatorWrapper()
				.setAnyExtensionsAllowed(isAnyExtensionsAllowed())
				.setBestPracticeWarningLevel(getBestPracticeWarningLevel())
				.setErrorForUnknownProfiles(isErrorForUnknownProfiles())
				.setExtensionDomains(getExtensionDomains())
				.setValidationPolicyAdvisor(validatorPolicyAdvisor)
				.setNoTerminologyChecks(isNoTerminologyChecks())
				.setNoExtensibleWarnings(isNoExtensibleWarnings())
				.setNoBindingMsgSuppressed(isNoBindingMsgSuppressed())
				.setValidatorResourceFetcher(getValidatorResourceFetcher())
				.setAssumeValidRestReferences(isAssumeValidRestReferences())
				.setAllowExamples(isAllowExamples())
				.validate(wrappedWorkerContext, theValidationCtx);
	}

	@Nonnull
	protected WorkerContextValidationSupportAdapter provideWorkerContext() {
		WorkerContextValidationSupportAdapter wrappedWorkerContext = myWrappedWorkerContext;
		if (wrappedWorkerContext == null) {
			wrappedWorkerContext =
					WorkerContextValidationSupportAdapter.newVersionSpecificWorkerContextWrapper(myValidationSupport);
		}
		myWrappedWorkerContext = wrappedWorkerContext;
		return wrappedWorkerContext;
	}

	@VisibleForTesting
	public WorkerContextValidationSupportAdapter getWorkerContext() {
		return myWrappedWorkerContext;
	}

	public IValidationPolicyAdvisor getValidatorPolicyAdvisor() {
		return validatorPolicyAdvisor;
	}

	public void setValidatorPolicyAdvisor(IValidationPolicyAdvisor validatorPolicyAdvisor) {
		this.validatorPolicyAdvisor = validatorPolicyAdvisor;
	}

	public IValidatorResourceFetcher getValidatorResourceFetcher() {
		return validatorResourceFetcher;
	}

	public void setValidatorResourceFetcher(IValidatorResourceFetcher validatorResourceFetcher) {
		this.validatorResourceFetcher = validatorResourceFetcher;
	}

	public boolean isAssumeValidRestReferences() {
		return assumeValidRestReferences;
	}

	public void setAssumeValidRestReferences(boolean assumeValidRestReferences) {
		this.assumeValidRestReferences = assumeValidRestReferences;
	}

	/**
	 * Clear any cached data held by the validator or any of its internal stores. This is mostly intended
	 * for unit tests, but could be used for production uses too.
	 */
	public void invalidateCaches() {
		myValidationSupport.invalidateCaches();
		if (myWrappedWorkerContext != null) {
			myWrappedWorkerContext.invalidateCaches();
		}
	}

	/**
	 * Should the validator disallow URLs with common example names, such as {@literal http://acme.org}
	 * and {@literal http://example.com}.
	 *
	 * @since 8.2.0
	 */
	public void setAllowExamples(boolean theAllowExamples) {
		myAllowExamples = theAllowExamples;
	}

	public boolean isAllowExamples() {
		return myAllowExamples;
	}

	public static class NullEvaluationContext implements FHIRPathEngine.IEvaluationContext {

		@Override
		public List<Base> resolveConstant(
				FHIRPathEngine engine, Object appContext, String name, boolean beforeContext, boolean explicitConstant)
				throws PathEngineException {
			return Collections.emptyList();
		}

		@Override
		public TypeDetails resolveConstantType(
				FHIRPathEngine engine, Object appContext, String name, boolean explicitConstant)
				throws PathEngineException {
			return null;
		}

		@Override
		public boolean log(String argument, List<Base> focus) {
			return false;
		}

		@Override
		public FunctionDetails resolveFunction(FHIRPathEngine engine, String functionName) {
			return null;
		}

		@Override
		public TypeDetails checkFunction(
				FHIRPathEngine engine,
				Object appContext,
				String functionName,
				TypeDetails focus,
				List<TypeDetails> parameters)
				throws PathEngineException {
			return null;
		}

		@Override
		public List<Base> executeFunction(
				FHIRPathEngine engine,
				Object appContext,
				List<Base> focus,
				String functionName,
				List<List<Base>> parameters) {
			return null;
		}

		@Override
		public Base resolveReference(FHIRPathEngine engine, Object appContext, String url, Base refContext)
				throws FHIRException {
			return null;
		}

		@Override
		public boolean conformsToProfile(FHIRPathEngine engine, Object appContext, Base item, String url)
				throws FHIRException {
			return false;
		}

		@Override
		public ValueSet resolveValueSet(FHIRPathEngine engine, Object appContext, String url) {
			return null;
		}

		@Override
		public boolean paramIsType(String name, int index) {
			return false;
		}
	}
}
