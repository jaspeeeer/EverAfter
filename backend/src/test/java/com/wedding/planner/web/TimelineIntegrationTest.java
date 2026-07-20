package com.wedding.planner.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wedding.planner.AbstractIntegrationTest;
import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

/**
 * Integration tests for the wedding-day timeline: CRUD + supplier links, the couple's
 * read-only access, tenant isolation, the early-morning sort wrap, and the typical-day
 * quick-start.
 */
@Transactional
class TimelineIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private EntityManager entityManager;

    private String register(String email, String role) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", email,
                                "password", "password123",
                                "firstName", "Test",
                                "lastName", "User",
                                "role", role))))
                .andExpect(status().isCreated())
                .andReturn();
        return json(result).get("token").asText();
    }

    private JsonNode json(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private String createProject(String token, String name, String ownerEmail) throws Exception {
        var body = new java.util.HashMap<String, Object>();
        body.put("name", name);
        if (ownerEmail != null) {
            body.put("ownerEmail", ownerEmail);
        }
        MvcResult result = mockMvc.perform(post("/api/projects")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andReturn();
        return json(result).get("id").asText();
    }

    private String createVendor(String token, String projectId, String name) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/projects/" + projectId + "/vendors")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", name, "category", "BEAUTY", "booked", true))))
                .andExpect(status().isCreated())
                .andReturn();
        return json(result).get("id").asText();
    }

    private String createEvent(String token, String projectId, Map<String, Object> body)
            throws Exception {
        MvcResult result = mockMvc.perform(post("/api/projects/" + projectId + "/timeline")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andReturn();
        return json(result).get("id").asText();
    }

    // --- CRUD + suppliers ---

    @Test
    void plannerCrudRoundTripWithLinkedSuppliers() throws Exception {
        String planner = register("tl-planner@wedding.test", "ROLE_PLANNER");
        String projectId = createProject(planner, "Timeline Wedding", null);
        String vendorId = createVendor(planner, projectId, "Glam Studio");

        String eventId = createEvent(planner, projectId, Map.of(
                "title", "Hair & makeup call",
                "location", "Bridal suite",
                "startTime", "06:00",
                "endTime", "09:00",
                "vendorIds", List.of(vendorId)));

        // Clicking the slot shows the involved supplier.
        mockMvc.perform(get("/api/projects/" + projectId + "/timeline")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + planner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].title").value("Hair & makeup call"))
                .andExpect(jsonPath("$[0].vendors.length()").value(1))
                .andExpect(jsonPath("$[0].vendors[0].name").value("Glam Studio"))
                .andExpect(jsonPath("$[0].vendors[0].category").value("BEAUTY"));

        // Update replaces fields and the vendor set.
        mockMvc.perform(put("/api/projects/" + projectId + "/timeline/" + eventId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + planner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "title", "Makeup call (moved)",
                                "startTime", "06:30",
                                "vendorIds", List.of()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Makeup call (moved)"))
                .andExpect(jsonPath("$.startTime").value("06:30:00"))
                .andExpect(jsonPath("$.vendors.length()").value(0));

        mockMvc.perform(delete("/api/projects/" + projectId + "/timeline/" + eventId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + planner))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/projects/" + projectId + "/timeline")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + planner))
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void eventsSortWithEarlyMorningWrap() throws Exception {
        String planner = register("tl-sort@wedding.test", "ROLE_PLANNER");
        String projectId = createProject(planner, "Sort Wedding", null);

        createEvent(planner, projectId, Map.of("title", "After-party", "startTime", "01:00"));
        createEvent(planner, projectId, Map.of("title", "Makeup call", "startTime", "06:00"));
        createEvent(planner, projectId, Map.of("title", "Party", "startTime", "23:00"));

        mockMvc.perform(get("/api/projects/" + projectId + "/timeline")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + planner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].title").value("Makeup call"))
                .andExpect(jsonPath("$[1].title").value("Party"))
                // 01:00 counts as "after midnight" and sorts last, not first.
                .andExpect(jsonPath("$[2].title").value("After-party"));
    }

    // --- Access rules ---

    @Test
    void coupleCanViewButNotEditTheTimeline() throws Exception {
        String planner = register("tl-owner-planner@wedding.test", "ROLE_PLANNER");
        register("tl-couple@wedding.test", "ROLE_USER");
        String projectId = createProject(planner, "Couple View Wedding", "tl-couple@wedding.test");
        createEvent(planner, projectId, Map.of("title", "Ceremony", "startTime", "11:00"));

        MvcResult login = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", "tl-couple@wedding.test", "password", "password123"))))
                .andExpect(status().isOk())
                .andReturn();
        String couple = json(login).get("token").asText();

        // Read: allowed.
        mockMvc.perform(get("/api/projects/" + projectId + "/timeline")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + couple))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].title").value("Ceremony"));

        // Writes: role-gated.
        mockMvc.perform(post("/api/projects/" + projectId + "/timeline")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + couple)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "title", "Sneaky event", "startTime", "12:00"))))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/projects/" + projectId + "/timeline/typical-day")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + couple))
                .andExpect(status().isForbidden());
    }

    @Test
    void foreignPlannerIsIsolated() throws Exception {
        String owner = register("tl-iso-owner@wedding.test", "ROLE_PLANNER");
        String outsider = register("tl-iso-out@wedding.test", "ROLE_PLANNER");
        String projectId = createProject(owner, "Isolated Timeline", null);

        mockMvc.perform(get("/api/projects/" + projectId + "/timeline")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + outsider))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/projects/" + projectId + "/timeline")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + outsider)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "title", "Nope", "startTime", "10:00"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void linkingAnotherProjectsVendorIsRejected() throws Exception {
        String planner = register("tl-vguard@wedding.test", "ROLE_PLANNER");
        String projectA = createProject(planner, "Guard A", null);
        String projectB = createProject(planner, "Guard B", null);
        String foreignVendor = createVendor(planner, projectB, "Foreign Florist");

        mockMvc.perform(post("/api/projects/" + projectA + "/timeline")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + planner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "title", "Flowers arrive",
                                "startTime", "08:00",
                                "vendorIds", List.of(foreignVendor)))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void deletingALinkedVendorDetachesItFromTheEvent() throws Exception {
        String planner = register("tl-vdel@wedding.test", "ROLE_PLANNER");
        String projectId = createProject(planner, "Vendor Delete Wedding", null);
        String vendorId = createVendor(planner, projectId, "Doomed DJ");
        createEvent(planner, projectId, Map.of(
                "title", "Party", "startTime", "20:00", "vendorIds", List.of(vendorId)));

        mockMvc.perform(delete("/api/projects/" + projectId + "/vendors/" + vendorId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + planner))
                .andExpect(status().isNoContent());

        // The whole test shares one persistence context; clear it so the next read sees the
        // database state (in production every request gets a fresh session).
        entityManager.flush();
        entityManager.clear();

        // The event survives; the join row cascaded away.
        mockMvc.perform(get("/api/projects/" + projectId + "/timeline")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + planner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].title").value("Party"))
                .andExpect(jsonPath("$[0].vendors.length()").value(0));
    }

    // --- Typical day quick-start ---

    @Test
    void typicalDaySeedsOnceAndOnlyOnEmptyTimelines() throws Exception {
        String planner = register("tl-typical@wedding.test", "ROLE_PLANNER");
        String projectId = createProject(planner, "Typical Day Wedding", null);

        mockMvc.perform(post("/api/projects/" + projectId + "/timeline/typical-day")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + planner))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.length()").value(9))
                .andExpect(jsonPath("$[0].title").value("Hair & makeup call"))
                .andExpect(jsonPath("$[8].title").value("After-party"));

        // Second call: rejected — the timeline is no longer empty.
        mockMvc.perform(post("/api/projects/" + projectId + "/timeline/typical-day")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + planner))
                .andExpect(status().isBadRequest());
    }

    @Test
    void unknownEventIs404() throws Exception {
        String planner = register("tl-404@wedding.test", "ROLE_PLANNER");
        String projectId = createProject(planner, "Missing Event Wedding", null);

        mockMvc.perform(put("/api/projects/" + projectId
                        + "/timeline/00000000-0000-0000-0000-000000000000")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + planner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "title", "Ghost", "startTime", "10:00"))))
                .andExpect(status().isNotFound());
    }
}
