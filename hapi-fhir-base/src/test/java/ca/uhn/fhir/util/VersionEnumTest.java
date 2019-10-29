package ca.uhn.fhir.util;

import org.junit.Ignore;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static org.hamcrest.CoreMatchers.hasItem;
import static org.junit.Assert.assertThat;

public class VersionEnumTest {

	@Test
	@Ignore("The fut-hapi-fhir fork has 'unofficial' Maven versions that do not need to be supported by hapi-fhir db migration tasks")
	public void testCurrentVersionExists() {
		List<String> versions = Arrays.stream(VersionEnum.values())
			.map(Enum::name)
			.collect(Collectors.toList());

		String version = VersionUtil.getVersion();
		version = "V" + version.replace(".", "_");
		version = version.replace("-SNAPSHOT", "");

		assertThat(versions, hasItem(version));
	}


}
