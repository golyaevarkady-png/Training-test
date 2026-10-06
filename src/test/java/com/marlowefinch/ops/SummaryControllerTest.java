package com.marlowefinch.ops;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/** TODO-233: GET /api/summary. Values match /api/kpis and the seed for the same range. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("demo")
class SummaryControllerTest {

    @Autowired
    private MockMvc mvc;

    @Test
    void summaryDefaultsToTheLast30DaysAndCarriesTheKpisAndTheTwoNames() throws Exception {
        mvc.perform(get("/api/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(8))
                .andExpect(jsonPath("$.from").value("2026-08-22"))
                .andExpect(jsonPath("$.to").value("2026-09-21"))
                .andExpect(jsonPath("$.onTimeRate").value(0.937))
                .andExpect(jsonPath("$.openTickets").value(114))
                .andExpect(jsonPath("$.revenue").value(360095.5))
                .andExpect(jsonPath("$.orders").value(624))
                .andExpect(jsonPath("$.worstCarrier").value("Kessler Logistics"))
                .andExpect(jsonPath("$.busiestTicketCategory").value("Delivery delay"));
    }

    @Test
    void summaryForAnEmptyRangeHasNullNames() throws Exception {
        mvc.perform(get("/api/summary").param("from", "2025-01-01").param("to", "2025-01-31"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.from").value("2025-01-01"))
                .andExpect(jsonPath("$.to").value("2025-01-31"))
                .andExpect(jsonPath("$.onTimeRate").doesNotExist())
                .andExpect(jsonPath("$.openTickets").value(0))
                .andExpect(jsonPath("$.revenue").value(0.0))
                .andExpect(jsonPath("$.orders").value(0))
                .andExpect(jsonPath("$.worstCarrier").doesNotExist())
                .andExpect(jsonPath("$.busiestTicketCategory").doesNotExist());
    }

    @Test
    void summaryWithAMalformedDateIsRejectedWithA400() throws Exception {
        mvc.perform(get("/api/summary").param("from", "next-tuesday"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors", hasSize(1)))
                .andExpect(jsonPath("$.errors[0]").value("from must be an ISO date (YYYY-MM-DD)"));
    }
}
