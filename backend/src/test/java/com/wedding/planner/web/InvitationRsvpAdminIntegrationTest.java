package com.wedding.planner.web;

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
 * Integration tests for Phase-5+ features: the couple invitation flow, the public RSVP
 * endpoints, guest CSV import, and admin authorization.
 */
@Transactional
class InvitationRsvpAdminIntegrationTest extends AbstractIntegrationTest {

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

    private JsonNode json(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private String createProject(String token, String name) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/projects")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("name", name))))
                .andExpect(status().isCreated())
                .andReturn();
        return json(result).get("id").asText();
    }

    // --- Invitation flow ---

    @Test
    void invitationFlowAttachesCoupleAsProjectOwner() throws Exception {
        String planner = register("inv-planner@wedding.test", "ROLE_PLANNER");
        String projectId = createProject(planner, "Invited Wedding");

        // Planner issues an invite.
        MvcResult invite = mockMvc.perform(post("/api/projects/" + projectId + "/invitations")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + planner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("email", "invited-couple@wedding.test"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andReturn();
        String token = json(invite).get("token").asText();

        // The public preview shows the project name without auth.
        mockMvc.perform(get("/api/public/invitations/" + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.projectName").value("Invited Wedding"))
                .andExpect(jsonPath("$.email").value("invited-couple@wedding.test"));

        // Couple registers with the token and becomes the project owner.
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", "invited-couple@wedding.test",
                                "password", "password123",
                                "firstName", "Invited",
                                "lastName", "Couple",
                                "role", "ROLE_USER",
                                "inviteToken", token))))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/projects/" + projectId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + planner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ownerEmail").value("invited-couple@wedding.test"));

        // The invitation cannot be reused.
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", "second-couple@wedding.test",
                                "password", "password123",
                                "firstName", "Second",
                                "lastName", "Couple",
                                "role", "ROLE_USER",
                                "inviteToken", token))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void coupleCannotIssueInvitations() throws Exception {
        String planner = register("inv-planner2@wedding.test", "ROLE_PLANNER");
        String couple = register("inv-couple2@wedding.test", "ROLE_USER");
        String projectId = createProject(planner, "No Couple Invites");

        mockMvc.perform(post("/api/projects/" + projectId + "/invitations")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + couple)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", "x@y.test"))))
                .andExpect(status().isForbidden());
    }

    // --- Public RSVP ---

    @Test
    void guestCanRsvpThroughPublicTokenLink() throws Exception {
        String planner = register("rsvp-planner@wedding.test", "ROLE_PLANNER");
        String projectId = createProject(planner, "RSVP Wedding");

        MvcResult created = mockMvc.perform(post("/api/projects/" + projectId + "/guests")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + planner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "firstName", "Alex",
                                "lastName", "Jamie",
                                "rsvpStatus", "PENDING",
                                "partySize", 2))))
                .andExpect(status().isCreated())
                .andReturn();
        String rsvpToken = json(created).get("rsvpToken").asText();

        // Unauthenticated view.
        mockMvc.perform(get("/api/public/rsvp/" + rsvpToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.guestName").value("Alex Jamie"))
                .andExpect(jsonPath("$.projectName").value("RSVP Wedding"));

        // Unauthenticated respond. Party size is deliberately absent from the request — the
        // server always resets it to 1 on public RSVP (regardless of what the planner had set).
        mockMvc.perform(put("/api/public/rsvp/" + rsvpToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "rsvpStatus", "ATTENDING",
                                "dietaryNotes", "1 vegan"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rsvpStatus").value("ATTENDING"))
                .andExpect(jsonPath("$.partySize").value(1));

        // The change is visible to the planner — party size reset from the initial 2 to 1.
        mockMvc.perform(get("/api/projects/" + projectId + "/guests")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + planner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].rsvpStatus").value("ATTENDING"))
                .andExpect(jsonPath("$[0].partySize").value(1));
    }

    @Test
    void unknownRsvpTokenIs404AndGuestListStaysProtected() throws Exception {
        mockMvc.perform(get("/api/public/rsvp/00000000-0000-0000-0000-000000000000"))
                .andExpect(status().isNotFound());

        // The public surface must not expose authenticated endpoints.
        mockMvc.perform(get("/api/projects"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void malformedRsvpTokenIs404TheSameAsAnUnknownOne() throws Exception {
        // A non-UUID path segment must not be distinguishable from a well-formed-but-unknown
        // token — otherwise the two error shapes leak whether a guessed string merely fails to
        // parse vs. genuinely doesn't exist.
        mockMvc.perform(get("/api/public/rsvp/not-a-uuid"))
                .andExpect(status().isNotFound());
    }

    // --- Guest CSV import ---

    @Test
    void bulkGuestImportCreatesAllRows() throws Exception {
        String planner = register("import-planner@wedding.test", "ROLE_PLANNER");
        String projectId = createProject(planner, "Import Wedding");

        List<Map<String, Object>> rows = List.of(
                Map.of("firstName", "Row", "lastName", "One", "rsvpStatus", "PENDING", "partySize", 1),
                Map.of("firstName", "Row", "lastName", "Two", "rsvpStatus", "ATTENDING", "partySize", 4,
                        "tableNumber", 7, "dietaryNotes", "no nuts"));

        mockMvc.perform(post("/api/projects/" + projectId + "/guests/import")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + planner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(rows)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[1].tableNumber").value(7));

        mockMvc.perform(get("/api/projects/" + projectId + "/guests")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + planner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    // --- Admin ---

    @Test
    void adminEndpointsRequireAdminRole() throws Exception {
        String planner = register("admin-guard-planner@wedding.test", "ROLE_PLANNER");

        mockMvc.perform(get("/api/admin/users")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + planner))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/admin/stats")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + planner))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminCanListUsersToggleEnabledAndReadStats() throws Exception {
        register("admin-target@wedding.test", "ROLE_USER");
        MvcResult login = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", "admin@wedding.test",
                                "password", "admin12345"))))
                .andExpect(status().isOk())
                .andReturn();
        String admin = json(login).get("token").asText();

        // Find the target user's id.
        MvcResult users = mockMvc.perform(get("/api/admin/users")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + admin))
                .andExpect(status().isOk())
                .andReturn();
        String targetId = null;
        String adminId = null;
        for (JsonNode node : json(users)) {
            if (node.get("email").asText().equals("admin-target@wedding.test")) {
                targetId = node.get("id").asText();
            }
            if (node.get("email").asText().equals("admin@wedding.test")) {
                adminId = node.get("id").asText();
            }
        }

        // Disable the target account.
        mockMvc.perform(put("/api/admin/users/" + targetId + "/enabled")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("enabled", false))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(false));

        // A disabled account cannot log in.
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", "admin-target@wedding.test",
                                "password", "password123"))))
                .andExpect(status().isUnauthorized());

        // Admin cannot disable themselves.
        mockMvc.perform(put("/api/admin/users/" + adminId + "/enabled")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("enabled", false))))
                .andExpect(status().isBadRequest());

        // Stats respond with sane shapes.
        mockMvc.perform(get("/api/admin/stats")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalUsers").isNumber())
                .andExpect(jsonPath("$.usersByRole").isMap());
    }
}
