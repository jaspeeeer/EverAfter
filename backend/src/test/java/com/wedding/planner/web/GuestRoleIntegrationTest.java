package com.wedding.planner.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wedding.planner.AbstractIntegrationTest;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

/**
 * Integration tests for the admin-managed guest role lookup (mirrors the vendor-category tests):
 * public read, admin CRUD with 409-on-dup and deactivate-if-in-use, and guest role assignment.
 */
@Transactional
class GuestRoleIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private String register(String email, String role) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", email, "password", "password123",
                                "firstName", "T", "lastName", "U", "role", role))))
                .andExpect(status().isCreated())
                .andReturn();
        return json(result).get("token").asText();
    }

    private String loginAdmin() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", "admin@wedding.test", "password", "admin12345"))))
                .andExpect(status().isOk())
                .andReturn();
        return json(result).get("token").asText();
    }

    private JsonNode json(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private String roleId(String token, String slug) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/guest-roles")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        for (JsonNode node : json(result)) {
            if (node.get("slug").asText().equals(slug)) {
                return node.get("id").asText();
            }
        }
        throw new IllegalStateException("No seeded role " + slug);
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

    @Test
    void plannerReadsActiveRolesButCannotManageThem() throws Exception {
        String planner = register("role-planner@wedding.test", "ROLE_PLANNER");

        mockMvc.perform(get("/api/guest-roles")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + planner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.slug == 'BEST_MAN')]").exists());

        mockMvc.perform(post("/api/admin/guest-roles")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + planner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("name", "Usher"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminCreatesRenamesAndDeleteDeactivatesInUseRole() throws Exception {
        String admin = loginAdmin();

        // Create a fresh role (auto-slugged), duplicate name → 409, unused delete → hard-delete.
        mockMvc.perform(post("/api/admin/guest-roles")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("name", "Usher"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.slug").value("USHER"));
        mockMvc.perform(post("/api/admin/guest-roles")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("name", "Usher"))))
                .andExpect(status().isConflict());
        mockMvc.perform(delete("/api/admin/guest-roles/" + roleId(admin, "USHER"))
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + admin))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/admin/guest-roles")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + admin))
                .andExpect(jsonPath("$[?(@.slug == 'USHER')]").doesNotExist());

        // A seeded, in-use role (BEST_MAN) can't be hard-deleted — it deactivates.
        String planner = register("role-user@wedding.test", "ROLE_PLANNER");
        String projectId = createProject(planner, "Roled Wedding");
        mockMvc.perform(post("/api/projects/" + projectId + "/guests")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + planner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "firstName", "Best Man", "lastName", "Bob",
                                "rsvpStatus", "ATTENDING", "partySize", 1,
                                "roleId", roleId(planner, "BEST_MAN")))))
                .andExpect(status().isCreated());

        mockMvc.perform(delete("/api/admin/guest-roles/" + roleId(admin, "BEST_MAN"))
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + admin))
                .andExpect(status().isNoContent());
        // Hidden from the public (active) list but still present, deactivated, in the admin list.
        mockMvc.perform(get("/api/guest-roles")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + admin))
                .andExpect(jsonPath("$[?(@.slug == 'BEST_MAN')]").doesNotExist());
        mockMvc.perform(get("/api/admin/guest-roles")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + admin))
                .andExpect(jsonPath("$[?(@.slug == 'BEST_MAN' && @.active == false)]").exists());
    }

    @Test
    void seededEntourageRolesAreEligibleAndAdminCanToggleTheFlag() throws Exception {
        String admin = loginAdmin();

        mockMvc.perform(get("/api/admin/guest-roles")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + admin))
                .andExpect(jsonPath("$[?(@.slug == 'BEST_MAN')].entourageEligible").value(true))
                .andExpect(jsonPath("$[?(@.slug == 'PARENTS')].entourageEligible").value(false));

        // A newly created role defaults to ineligible unless the request says otherwise.
        mockMvc.perform(post("/api/admin/guest-roles")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("name", "Emcee", "entourageEligible", true))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.entourageEligible").value(true));

        // Admin toggles an existing (seeded) role off, then back on.
        String parentsId = roleId(admin, "PARENTS");
        mockMvc.perform(put("/api/admin/guest-roles/" + parentsId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", "Parents", "active", true, "entourageEligible", true))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entourageEligible").value(true));
    }

    @Test
    void secondarySponsorSubRolesAreSeededAndAdminCanAddAndReparent() throws Exception {
        String admin = loginAdmin();

        MvcResult adminList = mockMvc.perform(get("/api/admin/guest-roles")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + admin))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode roles = json(adminList);

        // Six brand-new sub-roles, seeded under Secondary Sponsor.
        for (String slug : new String[] {
                "CANDLE", "VEIL", "CORD", "ARRHAE_BEARER", "ROSARY_BEARER", "BIBLE_BEARER"}) {
            JsonNode role = findBySlug(roles, slug);
            assertThat(role.get("parentName").asText()).isEqualTo("Secondary Sponsor");
        }

        // Ring Bearer and Flower Girls (renamed from Flower Girl) are reparented, not new rows.
        JsonNode ringBearer = findBySlug(roles, "RING_BEARER");
        assertThat(ringBearer.get("parentName").asText()).isEqualTo("Secondary Sponsor");
        JsonNode flowerGirls = findBySlug(roles, "FLOWER_GIRL");
        assertThat(flowerGirls.get("parentName").asText()).isEqualTo("Secondary Sponsor");
        assertThat(flowerGirls.get("name").asText()).isEqualTo("Flower Girls");

        // Admin creates a new sub-role under Secondary Sponsor; it round-trips with the parent set.
        String secondarySponsorId = roleId(admin, "SECONDARY_SPONSOR");
        MvcResult created = mockMvc.perform(post("/api/admin/guest-roles")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", "Coin Bearer", "entourageEligible", true,
                                "parentId", secondarySponsorId))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.parentName").value("Secondary Sponsor"))
                .andReturn();
        String coinBearerId = json(created).get("id").asText();

        // Reparenting Secondary Sponsor (which now has children) under another role is rejected.
        String bestManId = roleId(admin, "BEST_MAN");
        mockMvc.perform(put("/api/admin/guest-roles/" + secondarySponsorId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", "Secondary Sponsor", "active", true,
                                "entourageEligible", true, "parentId", bestManId))))
                .andExpect(status().isBadRequest());

        // A sub-role cannot itself be given sub-roles (two levels of nesting rejected).
        mockMvc.perform(post("/api/admin/guest-roles")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", "Sub-sub role", "entourageEligible", false,
                                "parentId", coinBearerId))))
                .andExpect(status().isBadRequest());

        // A role with sub-roles can't be hard-deleted until they're removed.
        mockMvc.perform(delete("/api/admin/guest-roles/" + secondarySponsorId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + admin))
                .andExpect(status().isBadRequest());
    }

    private JsonNode findBySlug(JsonNode roles, String slug) {
        for (JsonNode node : roles) {
            if (node.get("slug").asText().equals(slug)) {
                return node;
            }
        }
        throw new IllegalStateException("No role with slug " + slug);
    }

    @Test
    void guestCarriesItsClassificationAndAnUnknownRoleIsRejected() throws Exception {
        String planner = register("role-guest-planner@wedding.test", "ROLE_PLANNER");
        String projectId = createProject(planner, "Classified Wedding");

        var body = new HashMap<String, Object>();
        body.put("firstName", "Priya");
        body.put("rsvpStatus", "ATTENDING");
        body.put("partySize", 1);
        body.put("priority", "A");
        body.put("relatedTo", "GROOM");
        body.put("relationship", "CLOSE_FRIEND");
        body.put("roleId", roleId(planner, "PRINCIPAL_SPONSOR"));
        mockMvc.perform(post("/api/projects/" + projectId + "/guests")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + planner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.priority").value("A"))
                .andExpect(jsonPath("$.relatedTo").value("GROOM"))
                .andExpect(jsonPath("$.relationship").value("CLOSE_FRIEND"))
                .andExpect(jsonPath("$.roleName").value("Principal Sponsor"));

        // An unknown roleId is a 400.
        body.put("firstName", "Bad");
        body.put("roleId", "00000000-0000-0000-0000-000000000000");
        mockMvc.perform(post("/api/projects/" + projectId + "/guests")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + planner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest());
    }
}
