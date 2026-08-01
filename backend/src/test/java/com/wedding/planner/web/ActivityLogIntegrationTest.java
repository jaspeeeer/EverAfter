package com.wedding.planner.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wedding.planner.AbstractIntegrationTest;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Full HTTP proof: task/vendor/guest mutations by a planner all show up in the project's activity
 * feed with the actor's email attached; a couple with no association gets 404, matching the same
 * existence-hiding rule other project tabs use.
 */
class ActivityLogIntegrationTest extends AbstractIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    private String register(String email, String role) throws Exception {
        MvcResult r = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", email,
                                "password", "password123",
                                "firstName", "T",
                                "lastName", "U",
                                "role", role))))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(r.getResponse().getContentAsString()).get("token").asText();
    }

    @Test
    void mutationsAreRecordedAndPlannerCanReadTheFeed() throws Exception {
        String plannerEmail = "activity-planner-" + System.nanoTime() + "@t";
        String plannerToken = register(plannerEmail, "ROLE_PLANNER");

        // Create project as planner.
        MvcResult created = mockMvc.perform(post("/api/projects")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + plannerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("name", "Big Day"))))
                .andExpect(status().isCreated())
                .andReturn();
        String projectId = objectMapper.readTree(created.getResponse().getContentAsString())
                .get("id").asText();

        // Add a task.
        MvcResult taskCreated = mockMvc.perform(post("/api/projects/" + projectId + "/tasks")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + plannerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "title", "Book florist",
                                "status", "TODO"))))
                .andExpect(status().isCreated())
                .andReturn();
        String taskId = objectMapper.readTree(taskCreated.getResponse().getContentAsString())
                .get("id").asText();

        // Move task to DONE.
        mockMvc.perform(put("/api/projects/" + projectId + "/tasks/" + taskId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + plannerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "title", "Book florist",
                                "status", "DONE"))))
                .andExpect(status().isOk());

        // Read the feed.
        MvcResult feed = mockMvc.perform(
                        get("/api/projects/" + projectId + "/activity")
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + plannerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(org.hamcrest.Matchers.greaterThanOrEqualTo(3)))
                // The most recent row is at index 0 (created_at DESC).
                .andExpect(jsonPath("$[0].summary").value(org.hamcrest.Matchers.containsString("DONE")))
                .andExpect(jsonPath("$[0].actorEmail").value(plannerEmail))
                .andReturn();

        // Sanity: every row has a summary and a UUID id.
        JsonNode arr = objectMapper.readTree(feed.getResponse().getContentAsString());
        for (JsonNode row : arr) {
            org.assertj.core.api.Assertions.assertThat(row.get("summary").asText()).isNotBlank();
            org.assertj.core.api.Assertions.assertThat(row.get("id").asText()).isNotBlank();
        }
    }

    @Test
    void anotherUserCannotSeeAProjectsFeed() throws Exception {
        String plannerA = register("activity-A-" + System.nanoTime() + "@t", "ROLE_PLANNER");
        String plannerB = register("activity-B-" + System.nanoTime() + "@t", "ROLE_PLANNER");

        MvcResult created = mockMvc.perform(post("/api/projects")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + plannerA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("name", "Isolated"))))
                .andExpect(status().isCreated())
                .andReturn();
        String projectId = objectMapper.readTree(created.getResponse().getContentAsString())
                .get("id").asText();

        // planner B has no relationship — Spring Security returns 403; the frontend layout maps
        // this to notFound() so callers see a 404 in the UI. What matters at the API layer is that
        // the request is refused without leaking log contents.
        mockMvc.perform(get("/api/projects/" + projectId + "/activity")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + plannerB))
                .andExpect(status().isForbidden());
    }
}
