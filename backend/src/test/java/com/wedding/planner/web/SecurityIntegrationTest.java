package com.wedding.planner.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
import org.springframework.transaction.annotation.Transactional;

/**
 * End-to-end security tests driven over real HTTP. This is the heart of Phase 2: proving that
 * unauthenticated and cross-tenant requests are rejected, while legitimate owners and admins get
 * through. Each test runs in a rolled-back transaction for isolation.
 */
@Transactional
class SecurityIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    // --- helpers ---

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
        return token(result);
    }

    private String login(String email, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", email, "password", password))))
                .andExpect(status().isOk())
                .andReturn();
        return token(result);
    }

    private String token(MvcResult result) throws Exception {
        JsonNode node = objectMapper.readTree(result.getResponse().getContentAsString());
        return node.get("token").asText();
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
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText();
    }

    // --- authentication ---

    @Test
    void unauthenticatedRequestIsRejectedWith401() throws Exception {
        mockMvc.perform(get("/api/projects"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void loginWithWrongPasswordIsRejectedWith401() throws Exception {
        register("wrongpw-planner@wedding.test", "ROLE_PLANNER");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", "wrongpw-planner@wedding.test",
                                "password", "not-the-password"))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void cannotSelfRegisterAsAdmin() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", "wannabe-admin@wedding.test",
                                "password", "password123",
                                "firstName", "Wannabe",
                                "lastName", "Admin",
                                "role", "ROLE_ADMIN"))))
                .andExpect(status().isBadRequest());
    }

    // --- cross-tenant isolation ---

    @Test
    void plannerCannotAccessAnotherPlannersProject() throws Exception {
        String plannerA = register("planner-a@wedding.test", "ROLE_PLANNER");
        String plannerB = register("planner-b@wedding.test", "ROLE_PLANNER");

        String projectId = createProject(plannerA, "A's Wedding", null);

        // Owner planner: allowed.
        mockMvc.perform(get("/api/projects/" + projectId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + plannerA))
                .andExpect(status().isOk());

        // Foreign planner: forbidden.
        mockMvc.perform(get("/api/projects/" + projectId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + plannerB))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminCanAccessAnyProject() throws Exception {
        String plannerA = register("admin-test-planner@wedding.test", "ROLE_PLANNER");
        String adminToken = login("admin@wedding.test", "admin12345");

        String projectId = createProject(plannerA, "Admin-visible Wedding", null);

        mockMvc.perform(get("/api/projects/" + projectId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk());
    }

    @Test
    void coupleCanAccessOnlyTheirOwnProject() throws Exception {
        String planner = register("couple-test-planner@wedding.test", "ROLE_PLANNER");
        String couple = register("couple@wedding.test", "ROLE_USER");
        String stranger = register("stranger-couple@wedding.test", "ROLE_USER");

        String projectId = createProject(planner, "Couple's Wedding", "couple@wedding.test");

        mockMvc.perform(get("/api/projects/" + projectId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + couple))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/projects/" + projectId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + stranger))
                .andExpect(status().isForbidden());
    }

    @Test
    void projectListingIsScopedToTheCallersRole() throws Exception {
        String plannerA = register("scoped-a@wedding.test", "ROLE_PLANNER");
        String plannerB = register("scoped-b@wedding.test", "ROLE_PLANNER");

        createProject(plannerA, "A-1", null);
        createProject(plannerA, "A-2", null);
        createProject(plannerB, "B-1", null);

        // Planner B sees only their single project.
        mockMvc.perform(get("/api/projects")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + plannerB))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].name").value("B-1"));
    }

    @Test
    void coupleCannotCreateProjects() throws Exception {
        String couple = register("no-create-couple@wedding.test", "ROLE_USER");

        mockMvc.perform(post("/api/projects")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + couple)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("name", "Sneaky Project"))))
                .andExpect(status().isForbidden());
    }

    // --- budget business logic over the wire ---

    @Test
    void budgetSummaryReflectsExpenses() throws Exception {
        String planner = register("budget-planner@wedding.test", "ROLE_PLANNER");

        var body = new java.util.HashMap<String, Object>();
        body.put("name", "Budget Wedding");
        body.put("totalBudget", 10000);
        MvcResult created = mockMvc.perform(post("/api/projects")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + planner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andReturn();
        String projectId =
                objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asText();

        addExpense(planner, projectId, "Venue deposit", 3000, true, "VENUE");
        addExpense(planner, projectId, "Catering", 2500, false, "CATERING");

        MvcResult budget = mockMvc.perform(get("/api/projects/" + projectId + "/budget")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + planner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.overBudget").value(false))
                .andReturn();

        JsonNode node = objectMapper.readTree(budget.getResponse().getContentAsString());
        assertThat(new java.math.BigDecimal(node.get("totalExpenses").asText()))
                .isEqualByComparingTo("5500");
        assertThat(new java.math.BigDecimal(node.get("totalPaid").asText()))
                .isEqualByComparingTo("3000");
        assertThat(new java.math.BigDecimal(node.get("totalOutstanding").asText()))
                .isEqualByComparingTo("2500");
        assertThat(new java.math.BigDecimal(node.get("remaining").asText()))
                .isEqualByComparingTo("4500");
    }

    private void addExpense(String token, String projectId, String description, int amount,
                            boolean paid, String category) throws Exception {
        mockMvc.perform(post("/api/projects/" + projectId + "/expenses")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "description", description,
                                "amount", amount,
                                "category", category,
                                "paid", paid))))
                .andExpect(status().isCreated());
    }

    @Test
    void coupleCannotAccessAnotherProjectsExpenses() throws Exception {
        String planner = register("exp-planner@wedding.test", "ROLE_PLANNER");
        String stranger = register("exp-stranger@wedding.test", "ROLE_USER");
        String projectId = createProject(planner, "Private Wedding", null);

        assertThat(projectId).isNotBlank();

        mockMvc.perform(get("/api/projects/" + projectId + "/expenses")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + stranger))
                .andExpect(status().isForbidden());
    }

    @Test
    void plannerCanManageGuestsButOutsidersCannot() throws Exception {
        String owner = register("guest-owner@wedding.test", "ROLE_PLANNER");
        String outsider = register("guest-outsider@wedding.test", "ROLE_PLANNER");
        String projectId = createProject(owner, "Guest List Wedding", null);

        // Owner adds a guest.
        mockMvc.perform(post("/api/projects/" + projectId + "/guests")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + owner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", "Alex & Jamie",
                                "rsvpStatus", "ATTENDING",
                                "partySize", 2))))
                .andExpect(status().isCreated());

        // Owner can list their guests.
        mockMvc.perform(get("/api/projects/" + projectId + "/guests")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].name").value("Alex & Jamie"));

        // A different planner cannot see them.
        mockMvc.perform(get("/api/projects/" + projectId + "/guests")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + outsider))
                .andExpect(status().isForbidden());
    }
}
