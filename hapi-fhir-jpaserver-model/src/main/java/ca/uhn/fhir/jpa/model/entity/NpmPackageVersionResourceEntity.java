/*-
 * #%L
 * HAPI FHIR JPA Model
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
package ca.uhn.fhir.jpa.model.entity;

import ca.uhn.fhir.context.FhirVersionEnum;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinColumns;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import jakarta.persistence.Temporal;
import jakarta.persistence.TemporalType;
import jakarta.persistence.Version;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.Date;

@Entity()
@Table(
		name = "NPM_PACKAGE_VER_RES",
		indexes = {
			@Index(name = "IDX_PACKVERRES_URL", columnList = "CANONICAL_URL"),
			@Index(name = "FK_NPM_PACKVERRES_PACKVER", columnList = "PACKVER_PID"),
			@Index(name = "FK_NPM_PKVR_RESID", columnList = "BINARY_RES_ID")
		})
public class NpmPackageVersionResourceEntity {

	@Id
	@SequenceGenerator(name = "SEQ_NPM_PACKVERRES", sequenceName = "SEQ_NPM_PACKVERRES")
	@GeneratedValue(strategy = GenerationType.AUTO, generator = "SEQ_NPM_PACKVERRES")
	@Column(name = "PID")
	private Long myId;

	@ManyToOne
	@JoinColumn(
			name = "PACKVER_PID",
			referencedColumnName = "PID",
			foreignKey = @ForeignKey(name = "FK_NPM_PACKVERRES_PACKVER"),
			nullable = false)
	private NpmPackageVersionEntity myPackageVersion;

	@ManyToOne
	@JoinColumns(
			value = {
				@JoinColumn(
						name = "BINARY_RES_ID",
						referencedColumnName = "RES_ID",
						nullable = false,
						insertable = false,
						updatable = false),
				@JoinColumn(
						name = "PARTITION_ID",
						referencedColumnName = "PARTITION_ID",
						nullable = false,
						insertable = false,
						updatable = false)
			},
			foreignKey = @ForeignKey(name = "FK_NPM_PKVR_RESID"))
	private ResourceTable myResourceBinary;

	@Column(name = "BINARY_RES_ID", nullable = false)
	private Long myResourcePid;

	@Column(name = "PARTITION_ID", nullable = true)
	private Integer myPartitionId;

	@Column(name = "FILE_DIR", length = 200)
	private String myDirectory;

	@Column(name = "FILE_NAME", length = 200)
	private String myFilename;

	@Column(name = "RES_TYPE", length = ResourceTable.RESTYPE_LEN, nullable = false)
	private String myResourceType;

	@Column(name = "CANONICAL_URL", length = 200)
	private String myCanonicalUrl;

	@Column(name = "CANONICAL_VERSION", length = 200)
	private String myCanonicalVersion;

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(name = "FHIR_VERSION", length = NpmPackageVersionEntity.FHIR_VERSION_LENGTH, nullable = false)
	private FhirVersionEnum myFhirVersion;

	@Column(name = "FHIR_VERSION_ID", length = NpmPackageVersionEntity.FHIR_VERSION_ID_LENGTH, nullable = false)
	private String myFhirVersionId;

	@Column(name = "RES_SIZE_BYTES", nullable = false)
	private long myResSizeBytes;

	@Temporal(TemporalType.TIMESTAMP)
	@Version
	@Column(name = "UPDATED_TIME", nullable = false)
	private Date myVersion;

	public long getResSizeBytes() {
		return myResSizeBytes;
	}

	public void setResSizeBytes(long theResSizeBytes) {
		myResSizeBytes = theResSizeBytes;
	}

	public String getCanonicalVersion() {
		return myCanonicalVersion;
	}

	public void setCanonicalVersion(String theCanonicalVersion) {
		myCanonicalVersion = theCanonicalVersion;
	}

	public ResourceTable getResourceBinary() {
		return myResourceBinary;
	}

	public void setResourceBinary(ResourceTable theResourceBinary) {
		myResourceBinary = theResourceBinary;
		myResourcePid = theResourceBinary.getId().getId();
		myPartitionId = theResourceBinary.getPersistentId().getPartitionId();
	}

	public String getFhirVersionId() {
		return myFhirVersionId;
	}

	public String getPackageId() {
		return myPackageVersion.getPackageId();
	}

	public String getPackageVersion() {
		return myPackageVersion.getVersionId();
	}

	public void setFhirVersionId(String theFhirVersionId) {
		myFhirVersionId = theFhirVersionId;
	}

	public FhirVersionEnum getFhirVersion() {
		return myFhirVersion;
	}

	public void setFhirVersion(FhirVersionEnum theFhirVersion) {
		myFhirVersion = theFhirVersion;
	}

	public void setPackageVersion(NpmPackageVersionEntity thePackageVersion) {
		myPackageVersion = thePackageVersion;
	}

	public String getDirectory() {
		return myDirectory;
	}

	public void setDirectory(String theDirectory) {
		myDirectory = theDirectory;
	}

	public String getFilename() {
		return myFilename;
	}

	public void setFilename(String theFilename) {
		myFilename = theFilename;
	}

	public String getResourceType() {
		return myResourceType;
	}

	public void setResourceType(String theResourceType) {
		myResourceType = theResourceType;
	}

	public String getCanonicalUrl() {
		return myCanonicalUrl;
	}

	public void setCanonicalUrl(String theCanonicalUrl) {
		myCanonicalUrl = theCanonicalUrl;
	}

	@Override
	public String toString() {

		return new ToStringBuilder(this, ToStringStyle.SHORT_PREFIX_STYLE)
				.append("myId", myId)
				.append("myCanonicalUrl", myCanonicalUrl)
				.append("myCanonicalVersion", myCanonicalVersion)
				.append("myResourceType", myResourceType)
				.append("myDirectory", myDirectory)
				.append("myFilename", myFilename)
				.append("myPackageVersion", myPackageVersion)
				.append("myResSizeBytes", myResSizeBytes)
				.append("myVersion", myVersion)
				.toString();
	}
}
