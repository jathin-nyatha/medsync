
package com.medsync.patient.service;

import java.util.Set;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.medsync.patient.dto.CreateMedicalHistoryRequest;
import com.medsync.patient.dto.MedicalHistoryResponse;
import com.medsync.patient.entity.MedicalHistory;
import com.medsync.patient.entity.Patient;
import com.medsync.patient.exception.InvalidPaginationException;
import com.medsync.patient.exception.PatientNotFoundException;
import com.medsync.patient.repository.MedicalHistoryRepository;
import com.medsync.patient.repository.PatientRepository;

@Service
public class MedicalHistoryService {

	private static final int MAX_PAGE_SIZE = 100;

	private static final Set<String> ALLOWED_SORT_FIELDS = Set.of("id", "conditionName", "onsetDate", "recordedAt",
			"updatedAt");

	private final MedicalHistoryRepository medicalHistoryRepository;
	private final PatientRepository patientRepository;
	private final PatientAccessService patientAccessService;

	public MedicalHistoryService(MedicalHistoryRepository medicalHistoryRepository, PatientRepository patientRepository,
			PatientAccessService patientAccessService) {

		this.medicalHistoryRepository = medicalHistoryRepository;
		this.patientRepository = patientRepository;
		this.patientAccessService = patientAccessService;
	}

	/**
	 * Creates a medical history record for a patient. Only a doctor with an active
	 * patient assignment can perform this operation.
	 */
	@Transactional
	public MedicalHistoryResponse createMedicalHistory(Long patientId, Long doctorAuthUserId,
			String authorizationHeader, CreateMedicalHistoryRequest request) {

		Patient patient = getPatient(patientId);

		patientAccessService.validateDoctorAccess(patientId, doctorAuthUserId, authorizationHeader);

		MedicalHistory history = new MedicalHistory(patient, request.getConditionName().trim(),
				request.getDescription().trim(), request.getOnsetDate(),
				normalizeTreatmentDetails(request.getTreatmentDetails()), doctorAuthUserId);

		MedicalHistory savedHistory = medicalHistoryRepository.save(history);

		return MedicalHistoryResponse.from(savedHistory);
	}

	/**
	 * Retrieves paginated medical history records for a patient. Only a doctor with
	 * an active patient assignment can view these records.
	 */
	@Transactional(readOnly = true)
	public Page<MedicalHistoryResponse> getMedicalHistory(Long patientId, Long doctorAuthUserId,
			String authorizationHeader, Pageable pageable) {

		validatePatientAccess(patientId, doctorAuthUserId, authorizationHeader);

		validatePageable(pageable);

		return medicalHistoryRepository.findByPatient_Id(patientId, pageable).map(MedicalHistoryResponse::from);
	}

	/**
	 * Retrieves the patient or throws PatientNotFoundException.
	 */
	private Patient getPatient(Long patientId) {

		return patientRepository.findById(patientId)
				.orElseThrow(() -> new PatientNotFoundException("Patient not found with ID: " + patientId));
	}

	/**
	 * Verifies that the patient exists and that the doctor has access.
	 */
	private void validatePatientAccess(Long patientId, Long doctorAuthUserId, String authorizationHeader) {

		if (!patientRepository.existsById(patientId)) {
			throw new PatientNotFoundException("Patient not found with ID: " + patientId);
		}

		patientAccessService.validateDoctorAccess(patientId, doctorAuthUserId, authorizationHeader);
	}

	/**
	 * Validates pagination size and permitted sorting fields.
	 */
	private void validatePageable(Pageable pageable) {

		if (pageable.getPageSize() > MAX_PAGE_SIZE) {
			throw new InvalidPaginationException("Page size cannot exceed " + MAX_PAGE_SIZE);
		}

		pageable.getSort().forEach(order -> {

			if (!ALLOWED_SORT_FIELDS.contains(order.getProperty())) {
				throw new InvalidPaginationException("Unsupported sort field: " + order.getProperty());
			}
		});
	}

	/**
	 * Converts blank treatment details to null and trims nonblank values.
	 */
	private String normalizeTreatmentDetails(String treatmentDetails) {

		if (treatmentDetails == null || treatmentDetails.isBlank()) {
			return null;
		}

		return treatmentDetails.trim();
	}
}
