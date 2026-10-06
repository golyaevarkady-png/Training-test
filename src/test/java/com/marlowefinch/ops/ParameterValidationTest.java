package com.marlowefinch.ops;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/** TODO-232: server-side validation of from / to / limit, rendered as 400 {"errors": [...]}. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("demo")
class ParameterValidationTest {

    private static final String FROM_NOT_ISO = "from must be an ISO date (YYYY-MM-DD)";
    private static final String TO_NOT_ISO = "to must be an ISO date (YYYY-MM-DD)";
    private static final String FROM_AFTER_TO = "from must be on or before to";
    private static final String SPAN_TOO_LONG = "the range may span at most 366 days";
    private static final String BAD_LIMIT = "limit must be an integer between 1 and 500";

    @Autowired
    private MockMvc mvc;

    private ResultActions expectSingleError(ResultActions result, String message) throws Exception {
        return result.andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.errors", hasSize(1)))
                .andExpect(jsonPath("$.errors[0]").value(message));
    }

    // AC-1

    @Test
    void malformedFromIsRejected() throws Exception {
        expectSingleError(mvc.perform(get("/api/kpis").param("from", "next-tuesday")), FROM_NOT_ISO);
    }

    @Test
    void impossibleCalendarDateIsRejected() throws Exception {
        expectSingleError(mvc.perform(get("/api/kpis").param("from", "2026-02-30")), FROM_NOT_ISO);
    }

    @Test
    void malformedToIsRejected() throws Exception {
        expectSingleError(mvc.perform(get("/api/kpis").param("to", "21/09/2026")), TO_NOT_ISO);
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/kpis", "/api/deliveries/on-time", "/api/deliveries/late", "/api/tickets/by-category"})
    void everyDateEndpointRejectsAMalformedDate(String endpoint) throws Exception {
        expectSingleError(mvc.perform(get(endpoint).param("from", "2026-13-01")), FROM_NOT_ISO);
    }

    // AC-2

    @Test
    void fromAfterToIsRejectedOnKpis() throws Exception {
        expectSingleError(mvc.perform(get("/api/kpis").param("from", "2026-09-21").param("to", "2026-09-20")), FROM_AFTER_TO);
    }

    @Test
    void sameDayRangeIsAccepted() throws Exception {
        mvc.perform(get("/api/kpis").param("from", "2026-09-21").param("to", "2026-09-21"))
                .andExpect(status().isOk());
    }

    @Test
    void a367DayRangeIsRejected() throws Exception {
        expectSingleError(mvc.perform(get("/api/kpis").param("from", "2025-09-19").param("to", "2026-09-21")), SPAN_TOO_LONG);
    }

    @Test
    void a366DayRangeIsAccepted() throws Exception {
        mvc.perform(get("/api/kpis").param("from", "2025-09-20").param("to", "2026-09-21"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.from").value("2025-09-20"))
                .andExpect(jsonPath("$.to").value("2026-09-21"));
    }

    @Test
    void onlyFromFarInThePastIsCheckedAgainstTheDefaultTo() throws Exception {
        expectSingleError(mvc.perform(get("/api/tickets/by-category").param("from", "2020-01-01")), SPAN_TOO_LONG);
    }

    // AC-3

    @ParameterizedTest
    @ValueSource(strings = {"0", "501", "-1", "abc", "2.5"})
    void outOfRangeOrNonIntegerLimitIsRejected(String limit) throws Exception {
        expectSingleError(mvc.perform(get("/api/deliveries/late").param("limit", limit)), BAD_LIMIT);
    }

    @ParameterizedTest
    @ValueSource(strings = {"1", "500"})
    void limitAtTheBoundsIsAccepted(String limit) throws Exception {
        mvc.perform(get("/api/deliveries/late").param("limit", limit))
                .andExpect(status().isOk());
    }

    @Test
    void limitOneReturnsOneRow() throws Exception {
        mvc.perform(get("/api/deliveries/late").param("limit", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)));
    }

    // AC-4

    @Test
    void severalProblemsProduceSeveralEntries() throws Exception {
        mvc.perform(get("/api/deliveries/late").param("from", "bad").param("limit", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.errors", hasSize(2)))
                .andExpect(jsonPath("$.errors", containsInAnyOrder(FROM_NOT_ISO, BAD_LIMIT)));
    }

    @Test
    void badFromBadToAndBadLimitProduceThreeEntries() throws Exception {
        mvc.perform(get("/api/deliveries/late").param("from", "x").param("to", "y").param("limit", "abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors", containsInAnyOrder(FROM_NOT_ISO, TO_NOT_ISO, BAD_LIMIT)));
    }

    // Valid requests still work

    @Test
    void validExplicitRequestStillWorks() throws Exception {
        mvc.perform(get("/api/deliveries/late").param("from", "2026-09-14").param("to", "2026-09-21").param("limit", "3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(3)));
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/kpis", "/api/deliveries/on-time", "/api/deliveries/late", "/api/tickets/by-category"})
    void noParametersStillUsesTheDefaults(String endpoint) throws Exception {
        mvc.perform(get(endpoint)).andExpect(status().isOk());
    }

    @Test
    void defaultsStillApplyToKpisAndLate() throws Exception {
        mvc.perform(get("/api/kpis"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.from").value("2026-08-22"))
                .andExpect(jsonPath("$.to").value("2026-09-21"));
        mvc.perform(get("/api/deliveries/late").param("from", "2026-08-01").param("to", "2026-09-21"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(20)));
    }
}
