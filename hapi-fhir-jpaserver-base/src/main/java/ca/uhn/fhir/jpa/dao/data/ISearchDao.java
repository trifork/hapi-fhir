/*
 * #%L
 * HAPI FHIR JPA Server
 * %%
 * Copyright (C) 2014 - 2025 Smile CDR, Inc.
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */
package ca.uhn.fhir.jpa.dao.data;

import ca.uhn.fhir.jpa.entity.Search;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.Date;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

public interface ISearchDao extends JpaRepository<Search, Long>, IHapiFhirJpaRepository {

	@Query("SELECT s FROM Search s LEFT OUTER JOIN FETCH s.myIncludes WHERE s.myUuid = :uuid")
	Optional<Search> findByUuidAndFetchIncludes(@Param("uuid") String theUuid);

	@Query(
			"SELECT s.myId FROM Search s WHERE (s.myCreated < :cutoff) AND (s.myExpiryOrNull IS NULL OR s.myExpiryOrNull < :now) AND (s.myDeleted IS NULL OR s.myDeleted = FALSE)")
	Stream<Long> findWhereCreatedBefore(@Param("cutoff") Date theCutoff, @Param("now") Date theNow);

	@Query("SELECT new ca.uhn.fhir.jpa.dao.data.SearchIdAndResultSize(" + "s.myId, "
			+ "(select max(sr.myOrder) as maxOrder from SearchResult sr where sr.mySearchPid = s.myId)) "
			+ "FROM Search s WHERE s.myDeleted = TRUE")
	Stream<SearchIdAndResultSize> findDeleted();

	@Query(
			"SELECT s FROM Search s WHERE s.myResourceType = :type AND s.mySearchQueryStringHash = :hash AND (s.myCreated > :cutoff) AND s.myDeleted = FALSE AND s.myStatus <> 'FAILED'")
	Collection<Search> findWithCutoffOrExpiry(
			@Param("type") String theResourceType,
			@Param("hash") int theHashCode,
			@Param("cutoff") Date theCreatedCutoff);

	@Query("SELECT COUNT(s) FROM Search s WHERE s.myDeleted = TRUE")
	int countDeleted();

	@Modifying
	@Query("UPDATE Search s SET s.myDeleted = :deleted WHERE s.myId in (:pids)")
	@CanIgnoreReturnValue
	int updateDeleted(@Param("pids") Set<Long> thePid, @Param("deleted") boolean theDeleted);

	@Modifying
	@Query("DELETE FROM Search s WHERE s.myId = :pid")
	void deleteByPid(@Param("pid") Long theId);

	@Modifying
	@Query("DELETE FROM Search s WHERE s.myId in (:pids)")
	void deleteByPids(@Param("pids") Collection<Long> theSearchToDelete);
}
