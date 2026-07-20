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
 * Integration tests for the template feature: admin-only authoring, planner browsing, and
 * applying templates to projects (with the tenant-isolation and role rules).
 */
@Transactional
class TemplateIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

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

    private String loginAdmin() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", "admin@wedding.test",
                                "password", "admin12345"))))
                .andExpect(status().isOk())
                .andReturn();
        return json(result).get("token").asText();
    }

    private JsonNode json(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private String createProject(String token, String name, String weddingDate) throws Exception {
        var body = new java.util.HashMap<String, Object>();
        body.put("name", name);
        if (weddingDate != null) {
            body.put("weddingDate", weddingDate);
        }
        MvcResult result = mockMvc.perform(post("/api/projects")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andReturn();
        return json(result).get("id").asText();
    }

    private String createChecklistTemplate(String adminToken) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/templates/checklist")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", "Test Checklist",
                                "description", "For tests",
                                "items", List.of(
                                        Map.of("title", "Book venue", "daysBeforeWedding", 300),
                                        Map.of("title", "Book photographer", "daysBeforeWedding", 240),
                                        Map.of("title", "Write vows"))))))
                .andExpect(status().isCreated())
                .andReturn();
        return json(result).get("id").asText();
    }

    // --- Authoring authorization ---

    @Test
    void plannerCannotCreateUpdateOrDeleteTemplates() throws Exception {
        String planner = register("tpl-planner-authz@wedding.test", "ROLE_PLANNER");
        String admin = loginAdmin();
        String templateId = createChecklistTemplate(admin);

        String body = objectMapper.writeValueAsString(Map.of(
                "name", "Nope",
                "items", List.of(Map.of("title", "x"))));

        mockMvc.perform(post("/api/templates/checklist")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + planner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden());
        mockMvc.perform(put("/api/templates/checklist/" + templateId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + planner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/templates/checklist/" + templateId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + planner))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminCrudRoundTripsAndPutReplacesItems() throws Exception {
        String admin = loginAdmin();
        String templateId = createChecklistTemplate(admin);

        // Update replaces the item list wholesale.
        mockMvc.perform(put("/api/templates/checklist/" + templateId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", "Renamed Checklist",
                                "items", List.of(Map.of("title", "Only task", "daysBeforeWedding", 10))))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Renamed Checklist"))
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].title").value("Only task"));

        mockMvc.perform(delete("/api/templates/checklist/" + templateId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + admin))
                .andExpect(status().isNoContent());

        mockMvc.perform(put("/api/templates/checklist/" + templateId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", "Ghost",
                                "items", List.of(Map.of("title", "x"))))))
                .andExpect(status().isNotFound());
    }

    @Test
    void plannerCanBrowseTemplatesIncludingSeededStarters() throws Exception {
        String planner = register("tpl-browse@wedding.test", "ROLE_PLANNER");

        mockMvc.perform(get("/api/templates/checklist")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + planner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.name == 'Classic Wedding Checklist')]").exists());

        mockMvc.perform(get("/api/templates/vendors")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + planner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.name == 'Essential Vendors')]").exists());
    }

    // --- Applying ---

    @Test
    void applyingChecklistTemplateComputesDueDatesFromWeddingDate() throws Exception {
        String planner = register("tpl-apply-dated@wedding.test", "ROLE_PLANNER");
        String admin = loginAdmin();
        String templateId = createChecklistTemplate(admin);
        String projectId = createProject(planner, "Dated Wedding", "2027-06-30");

        mockMvc.perform(post("/api/projects/" + projectId + "/tasks/apply-template")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + planner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("templateId", templateId))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.length()").value(3))
                // 2027-06-30 minus 300 days = 2026-09-03
                .andExpect(jsonPath("$[0].title").value("Book venue"))
                .andExpect(jsonPath("$[0].dueDate").value("2026-09-03"))
                .andExpect(jsonPath("$[0].status").value("TODO"))
                // item without daysBeforeWedding keeps a null due date
                .andExpect(jsonPath("$[2].title").value("Write vows"))
                .andExpect(jsonPath("$[2].dueDate").doesNotExist());

        mockMvc.perform(get("/api/projects/" + projectId + "/tasks")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + planner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3));
    }

    @Test
    void applyingToUndatedProjectLeavesDueDatesNull() throws Exception {
        String planner = register("tpl-apply-undated@wedding.test", "ROLE_PLANNER");
        String admin = loginAdmin();
        String templateId = createChecklistTemplate(admin);
        String projectId = createProject(planner, "Undated Wedding", null);

        mockMvc.perform(post("/api/projects/" + projectId + "/tasks/apply-template")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + planner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("templateId", templateId))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$[0].dueDate").doesNotExist());
    }

    @Test
    void applyingVendorTemplateCreatesUnbookedSlots() throws Exception {
        String planner = register("tpl-apply-vendors@wedding.test", "ROLE_PLANNER");
        String projectId = createProject(planner, "Vendor Slots Wedding", null);

        // Use the seeded starter vendor template.
        MvcResult list = mockMvc.perform(get("/api/templates/vendors")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + planner))
                .andExpect(status().isOk())
                .andReturn();
        String templateId = json(list).get(0).get("id").asText();

        mockMvc.perform(post("/api/projects/" + projectId + "/vendors/apply-template")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + planner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("templateId", templateId))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.length()").value(6))
                .andExpect(jsonPath("$[0].booked").value(false));
    }

    @Test
    void applyIsBlockedAcrossTenantsAndForCouples() throws Exception {
        String owner = register("tpl-owner@wedding.test", "ROLE_PLANNER");
        String outsider = register("tpl-outsider@wedding.test", "ROLE_PLANNER");
        String admin = loginAdmin();
        String templateId = createChecklistTemplate(admin);
        String projectId = createProject(owner, "Isolated Wedding", null);

        // Foreign planner: 403.
        mockMvc.perform(post("/api/projects/" + projectId + "/tasks/apply-template")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + outsider)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("templateId", templateId))))
                .andExpect(status().isForbidden());

        // A couple cannot apply templates even on a project they own (role gate, not access gate).
        String couple = register("tpl-couple@wedding.test", "ROLE_USER");
        String ownedProjectId = createProjectWithOwner(owner, "Couple Owned", "tpl-couple@wedding.test");

        mockMvc.perform(post("/api/projects/" + ownedProjectId + "/tasks/apply-template")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + couple)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("templateId", templateId))))
                .andExpect(status().isForbidden());

        // Unknown template: 404 for an authorized caller.
        mockMvc.perform(post("/api/projects/" + projectId + "/tasks/apply-template")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + owner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("templateId", "00000000-0000-0000-0000-000000000000"))))
                .andExpect(status().isNotFound());
    }

    private String createProjectWithOwner(String token, String name, String ownerEmail)
            throws Exception {
        MvcResult result = mockMvc.perform(post("/api/projects")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", name,
                                "ownerEmail", ownerEmail))))
                .andExpect(status().isCreated())
                .andReturn();
        return json(result).get("id").asText();
    }
}
