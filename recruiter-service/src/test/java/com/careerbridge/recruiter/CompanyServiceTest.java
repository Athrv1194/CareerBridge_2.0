package com.careerbridge.recruiter;

import com.careerbridge.recruiter.dto.CompanyResponse;
import com.careerbridge.recruiter.dto.CreateCompanyRequest;
import com.careerbridge.recruiter.dto.UpdateCompanyRequest;
import com.careerbridge.recruiter.exception.CustomException;
import com.careerbridge.recruiter.model.Company;
import com.careerbridge.recruiter.repository.CompanyRepository;
import com.careerbridge.recruiter.service.CompanyServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CompanyServiceTest {

    private static final Long RECRUITER_ID = 7L;
    private static final Long COMPANY_ID = 1L;

    @Mock private CompanyRepository companyRepository;

    @InjectMocks private CompanyServiceImpl companyService;

    private Company company;

    @BeforeEach
    void setUp() {
        company = Company.builder()
                .id(COMPANY_ID).recruiterId(RECRUITER_ID).name("TechCorp India")
                .industry("IT Services").website("https://techcorp.example.com")
                .description("We build enterprise software.").build();
    }

    @Test
    @DisplayName("createCompany: saves with the recruiterId from the header, not the body")
    void createCompany_Success() {
        when(companyRepository.existsByRecruiterId(RECRUITER_ID)).thenReturn(false);
        when(companyRepository.save(any(Company.class))).thenAnswer(inv -> {
            Company c = inv.getArgument(0);
            c.setId(COMPANY_ID);
            return c;
        });

        CompanyResponse result = companyService.createCompany("RECRUITER", RECRUITER_ID,
                CreateCompanyRequest.builder().name("TechCorp India").industry("IT Services").build());

        assertEquals("TechCorp India", result.getName());

        ArgumentCaptor<Company> saved = ArgumentCaptor.forClass(Company.class);
        verify(companyRepository).save(saved.capture());
        assertEquals(RECRUITER_ID, saved.getValue().getRecruiterId());
    }

    /**
     * The 400 is the friendly half; uk_company_recruiter_id is the guarantee. Two concurrent
     * creates can both pass this check and the loser gets a DataIntegrityViolationException.
     */
    @Test
    @DisplayName("createCompany: a second company for the same recruiter is 400")
    void createCompany_AlreadyHasOne_Throws400() {
        when(companyRepository.existsByRecruiterId(RECRUITER_ID)).thenReturn(true);

        CustomException ex = assertThrows(CustomException.class,
                () -> companyService.createCompany("RECRUITER", RECRUITER_ID,
                        CreateCompanyRequest.builder().name("AnotherCorp").industry("Finance").build()));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
        assertTrue(ex.getMessage().contains("already have a company profile"));
        verify(companyRepository, never()).save(any());
    }

    @Test
    @DisplayName("createCompany: a STUDENT is refused with 403 before the duplicate check runs")
    void createCompany_StudentRole_Throws403() {
        CustomException ex = assertThrows(CustomException.class,
                () -> companyService.createCompany("STUDENT", RECRUITER_ID,
                        CreateCompanyRequest.builder().name("X").industry("Y").build()));

        assertEquals(HttpStatus.FORBIDDEN, ex.getStatus());
        verify(companyRepository, never()).existsByRecruiterId(anyLong());
    }

    @Test
    @DisplayName("updateCompany: null fields leave the stored values unchanged")
    void updateCompany_PartialUpdate_LeavesOtherFields() {
        when(companyRepository.findByIdAndRecruiterId(COMPANY_ID, RECRUITER_ID))
                .thenReturn(Optional.of(company));
        when(companyRepository.save(any(Company.class))).thenAnswer(inv -> inv.getArgument(0));

        CompanyResponse result = companyService.updateCompany("RECRUITER", RECRUITER_ID, COMPANY_ID,
                UpdateCompanyRequest.builder().name("TechCorp Global").build());

        assertEquals("TechCorp Global", result.getName());
        assertEquals("IT Services", result.getIndustry());
        assertEquals("https://techcorp.example.com", result.getWebsite());
        assertEquals("We build enterprise software.", result.getDescription());
    }

    @Test
    @DisplayName("updateCompany: another recruiter's company is 404")
    void updateCompany_NotOwner_Throws404() {
        when(companyRepository.findByIdAndRecruiterId(COMPANY_ID, 999L)).thenReturn(Optional.empty());

        CustomException ex = assertThrows(CustomException.class,
                () -> companyService.updateCompany("RECRUITER", 999L, COMPANY_ID,
                        UpdateCompanyRequest.builder().name("Hijacked").build()));

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatus());
        verify(companyRepository, never()).save(any());
    }

    /** No role restriction: a student browsing a job needs to see who posted it. */
    @Test
    @DisplayName("getCompanyById: readable without any role check")
    void getCompanyById_NoRoleNeeded() {
        when(companyRepository.findById(COMPANY_ID)).thenReturn(Optional.of(company));

        assertEquals("TechCorp India", companyService.getCompanyById(COMPANY_ID).getName());
    }

    @Test
    @DisplayName("getCompanyById: an unknown company is 404")
    void getCompanyById_NotFound_Throws404() {
        when(companyRepository.findById(COMPANY_ID)).thenReturn(Optional.empty());

        CustomException ex = assertThrows(CustomException.class,
                () -> companyService.getCompanyById(COMPANY_ID));

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatus());
    }

    @Test
    @DisplayName("getMyCompanies: a STUDENT is refused with 403")
    void getMyCompanies_StudentRole_Throws403() {
        CustomException ex = assertThrows(CustomException.class,
                () -> companyService.getMyCompanies("STUDENT", RECRUITER_ID));

        assertEquals(HttpStatus.FORBIDDEN, ex.getStatus());
        verify(companyRepository, never()).findByRecruiterId(anyLong());
    }

    @Test
    @DisplayName("getMyCompanies: returns the recruiter's own company")
    void getMyCompanies_Success() {
        when(companyRepository.findByRecruiterId(RECRUITER_ID)).thenReturn(List.of(company));

        List<CompanyResponse> result = companyService.getMyCompanies("RECRUITER", RECRUITER_ID);

        assertEquals(1, result.size());
        assertEquals(COMPANY_ID, result.get(0).getId());
    }
}
