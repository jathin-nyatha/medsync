
package com.medsync.patient.service;

import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.medsync.patient.dto.CreateDiagnosisRequest;
import com.medsync.patient.dto.DiagnosisResponse;
import com.medsync.patient.dto.UpdateDiagnosisRequest;
import com.medsync.patient.entity.Diagnosis;
import com.medsync.patient.entity.Patient;
import com.medsync.patient.exception.DiagnosisNotFoundException;
import com.medsync.patient.exception.InvalidPaginationException;
import com.medsync.patient.exception.PatientNotFoundException;
import com.medsync.patient.repository.DiagnosisRepository;
import com.medsync.patient.repository.PatientRepository;

@Service
public class DiagnosisService {

	private static final int MAX_PAGE_SIZE = 100;

	private static final Set<String> ALLOWED_SORT_FIELDS = Set.of("id", "diagnosisName", "description",
			"diagnosedByAuthUserId", "diagnosedAt", "createdAt", "updatedAt");

	private final DiagnosisRepository diagnosisRepository;
	private final PatientRepository patientRepository;
	private final PatientAccessService patientAccessService;

	public DiagnosisService(DiagnosisRepository diagnosisRepository, PatientRepository patientRepository,
			PatientAccessService patientAccessService) {

		this.diagnosisRepository = diagnosisRepository;
		this.patientRepository = patientRepository;
		this.patientAccessService = patientAccessService;
	}

	/**
	 * Creates a diagnosis for a patient. Only a doctor with an active patient
	 * assignment can perform this operation.
	 */
	@Transactional
	public DiagnosisResponse createDiagnosis(Long patientId, Long doctorAuthUserId, String authorizationHeader,
			CreateDiagnosisRequest request) {

		Patient patient = getPatient(patientId);

		validateDoctorAccess(patientId, doctorAuthUserId, authorizationHeader);

		Diagnosis diagnosis = new Diagnosis(patient, normalizeDiagnosisName(request.getDiagnosisName()),
				normalizeDescription(request.getDescription()), doctorAuthUserId);

		Diagnosis savedDiagnosis = diagnosisRepository.save(diagnosis);

		return DiagnosisResponse.from(savedDiagnosis);
	}

	/**
	 * Retrieves a specific diagnosis belonging to a patient.
	 */
	@Transactional(readOnly = true)
	public DiagnosisResponse getDiagnosis(Long patientId, Long doctorAuthUserId, String authorizationHeader,
			Long diagnosisId) {

		getPatient(patientId);

		validateDoctorAccess(patientId, doctorAuthUserId, authorizationHeader);

		Diagnosis diagnosis = diagnosisRepository.findByIdAndPatient_Id(diagnosisId, patientId)
				.orElseThrow(() -> new DiagnosisNotFoundException("Diagnosis not found with ID: " + diagnosisId));

		return DiagnosisResponse.from(diagnosis);
	}

	/**
	 * Retrieves a paginated list of diagnoses belonging to a patient.
	 */
	@Transactional(readOnly = true)
	public Page<DiagnosisResponse> getDiagnoses(Long patientId, Long doctorAuthUserId, String authorizationHeader,
			Pageable pageable) {

		getPatient(patientId);

		validateDoctorAccess(patientId, doctorAuthUserId, authorizationHeader);

		validatePagination(pageable);

		return diagnosisRepository.findByPatient_Id(patientId, pageable).map(DiagnosisResponse::from);
	}

	/**
	 * Updates an existing diagnosis belonging to a patient. The original diagnosing
	 * doctor's ID and diagnosis timestamp are preserved.
	 */
	@Transactional
	public DiagnosisResponse updateDiagnosis(Long patientId, Long doctorAuthUserId, String authorizationHeader,
			Long diagnosisId, UpdateDiagnosisRequest request) {

		getPatient(patientId);

		validateDoctorAccess(patientId, doctorAuthUserId, authorizationHeader);

		Diagnosis diagnosis = diagnosisRepository.findByIdAndPatient_Id(diagnosisId, patientId)
				.orElseThrow(() -> new DiagnosisNotFoundException("Diagnosis not found with ID: " + diagnosisId));

		diagnosis.setDiagnosisName(normalizeDiagnosisName(request.getDiagnosisName()));

		diagnosis.setDescription(normalizeDescription(request.getDescription()));

		Diagnosis updatedDiagnosis = diagnosisRepository.save(diagnosis);

		return DiagnosisResponse.from(updatedDiagnosis);
	}

	/**
	 * Checks whether the patient exists.
	 */
	private Patient getPatient(Long patientId) {

		return patientRepository.findById(patientId)
				.orElseThrow(() -> new PatientNotFoundException("Patient not found with ID: " + patientId));
	}

	/**
	 * Verifies that the doctor has an active assignment with the patient. The
	 * Authorization header is forwarded for downstream JWT verification.
	 */
	private void validateDoctorAccess(Long patientId, Long doctorAuthUserId, String authorizationHeader) {

		patientAccessService.validateDoctorAccess(patientId, doctorAuthUserId, authorizationHeader);
	}

	/**
	 * Validates pagination size and requested sort fields.
	 */
	private void validatePagination(Pageable pageable) {

		if (pageable == null || pageable.isUnpaged()) {
			throw new InvalidPaginationException("Pagination is required for this endpoint.");
		}

		if (pageable.getPageSize() > MAX_PAGE_SIZE) {
			throw new InvalidPaginationException("Page size cannot exceed " + MAX_PAGE_SIZE + ".");
		}

		Set<String> invalidSortFields = pageable.getSort().stream().map(Sort.Order::getProperty)
				.filter(property -> !ALLOWED_SORT_FIELDS.contains(property)).collect(Collectors.toSet());

		if (!invalidSortFields.isEmpty()) {
			throw new InvalidPaginationException("Unsupported sort field(s): " + String.join(", ", invalidSortFields));
		}
	}

	/**
	 * Trims the diagnosis name before persistence.
	 */
	private String normalizeDiagnosisName(String diagnosisName) {

		return diagnosisName.trim();
	}

	/**
	 * Converts blank descriptions to null and trims non-blank descriptions.
	 */
	private String normalizeDescription(String description) {

		if (description == null || description.isBlank()) {
			return null;
		}

		return description.trim();
	}
}
