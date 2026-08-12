package com.wedding.planner.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wedding.planner.AbstractIntegrationTest;
import jakarta.persistence.EntityManager;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

/**
 * Integration tests for the {@code POST .../{id}/restore} endpoints — the undo side of the
 * soft-delete infrastructure added earlier. Covers the actual landmines: a package's items
 * restoring exactly alongside it (and only rows sharing its tombstone timestamp), a vendor's
 * managed expense being recreated rather than restored, tenant scoping, and restoring an
 * already-live row being a clean 404 rather than a 500.
 */
@Transactional
class RestoreIntegrationTest extends AbstractIntegrationTest {

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
                                "email", email, "password", "password123",
                                "firstName", "T", "lastName", "U", "role", role))))
                .andExpect(status().isCreated())
                .andReturn();
        return json(result).get("token").asText();
    }

    private JsonNode json(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private String categoryId(String token, String slug) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/vendor-categories")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        for (JsonNode node : json(result)) {
            if (node.get("slug").asText().equals(slug)) {
                return node.get("id").asText();
            }
        }
        throw new IllegalStateException("No seeded category " + slug);
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

    private String vendor(String token, String projectId, String name, String slug,
                         String parentId, Integer agreedPrice) throws Exception {
        var body = new HashMap<String, Object>();
        body.put("name", name);
        body.put("categoryId", categoryId(token, slug));
        body.put("booked", true);
        if (parentId != null) {
            body.put("parentId", parentId);
        }
        if (agreedPrice != null) {
            body.put("agreedPrice", agreedPrice);
        }
        return json(mockMvc.perform(post("/api/projects/" + projectId + "/vendors")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andReturn()).get("id").asText();
    }

    @Test
    void restoringAPackageRevivesExactlyTheItemsItTookWithItNotOnesDeletedSeparately()
            throws Exception {
        String planner = register("restore-pkg-planner@wedding.test", "ROLE_PLANNER");
        String projectId = createProject(planner, "Package Restore Wedding");

        String pkgId = vendor(planner, projectId, "All-In Package", "OTHER", null, 300000);
        String item1 = vendor(planner, projectId, "Bundled Caterer", "CATERING", pkgId, null);
        String item2 = vendor(planner, projectId, "Bundled Florist", "FLORIST", pkgId, null);

        // An item independently removed *before* the package goes — must stay gone.
        mockMvc.perform(delete("/api/projects/" + projectId + "/vendors/" + item2)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + planner))
                .andExpect(status().isNoContent());

        // Now delete the package — item1 (still live) cascades with it; item2 is untouched
        // (already gone, at an earlier tombstone timestamp).
        mockMvc.perform(delete("/api/projects/" + projectId + "/vendors/" + pkgId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + planner))
                .andExpect(status().isNoContent());
        entityManager.flush();
        entityManager.clear();

        mockMvc.perform(get("/api/projects/" + projectId + "/vendors")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + planner))
                .andExpect(jsonPath("$.length()").value(0));

        // Restore the package — item1 comes back with it; item2 (a different delete, a different
        // tombstone) does not.
        mockMvc.perform(post("/api/projects/" + projectId + "/vendors/" + pkgId + "/restore")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + planner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("All-In Package"));
        entityManager.flush();
        entityManager.clear();

        MvcResult afterRestore = mockMvc.perform(get("/api/projects/" + projectId + "/vendors")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + planner))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode vendors = json(afterRestore);
        assertNamesExactly(vendors, "All-In Package", "Bundled Caterer");

        // item2 stays restorable on its own — its tombstone was never touched by the package's.
        mockMvc.perform(post("/api/projects/" + projectId + "/vendors/" + item2 + "/restore")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + planner))
                .andExpect(status().isOk());
    }

    @Test
    void restoringAVendorRecreatesItsManagedExpense() throws Exception {
        String planner = register("restore-managed-planner@wedding.test", "ROLE_PLANNER");
        String projectId = createProject(planner, "Managed Restore Wedding");
        String vendorId = vendor(planner, projectId, "Grand Venue", "VENUE", null, 150000);

        mockMvc.perform(get("/api/projects/" + projectId + "/expenses")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + planner))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].managed").value(true));

        mockMvc.perform(delete("/api/projects/" + projectId + "/vendors/" + vendorId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + planner))
                .andExpect(status().isNoContent());
        entityManager.flush();
        entityManager.clear();

        // The managed line is hard-deleted alongside a vendor delete — it's derived bookkeeping,
        // not user data that needs an undo window of its own.
        mockMvc.perform(get("/api/projects/" + projectId + "/expenses")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + planner))
                .andExpect(jsonPath("$.length()").value(0));

        mockMvc.perform(post("/api/projects/" + projectId + "/vendors/" + vendorId + "/restore")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + planner))
                .andExpect(status().isOk());
        entityManager.flush();
        entityManager.clear();

        // A fresh managed line reappears, synced from the restored vendor's agreed price.
        mockMvc.perform(get("/api/projects/" + projectId + "/expenses")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + planner))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].managed").value(true))
                .andExpect(jsonPath("$[0].amount").value(150000));
    }

    @Test
    void restoreIsScopedToTheOwningProject() throws Exception {
        String planner = register("restore-tenant-planner@wedding.test", "ROLE_PLANNER");
        String projectA = createProject(planner, "Tenant A Wedding");
        String projectB = createProject(planner, "Tenant B Wedding");
        String vendorId = vendor(planner, projectA, "Only In A", "VENUE", null, null);

        mockMvc.perform(delete("/api/projects/" + projectA + "/vendors/" + vendorId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + planner))
                .andExpect(status().isNoContent());

        // Borrowing the id under the wrong project must 404, not restore across tenants.
        mockMvc.perform(post("/api/projects/" + projectB + "/vendors/" + vendorId + "/restore")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + planner))
                .andExpect(status().isNotFound());

        mockMvc.perform(post("/api/projects/" + projectA + "/vendors/" + vendorId + "/restore")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + planner))
                .andExpect(status().isOk());
    }

    @Test
    void restoringAnAlreadyLiveRowIs404NotAServerError() throws Exception {
        String planner = register("restore-noop-planner@wedding.test", "ROLE_PLANNER");
        String projectId = createProject(planner, "Noop Restore Wedding");
        String vendorId = vendor(planner, projectId, "Never Deleted", "VENUE", null, null);

        mockMvc.perform(post("/api/projects/" + projectId + "/vendors/" + vendorId + "/restore")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + planner))
                .andExpect(status().isNotFound());

        // And an unknown id entirely — same 404, no different error shape.
        mockMvc.perform(post("/api/projects/" + projectId
                        + "/vendors/00000000-0000-0000-0000-000000000000/restore")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + planner))
                .andExpect(status().isNotFound());
    }

    @Test
    void guestRestoreRoundTrips() throws Exception {
        String planner = register("restore-guest-planner@wedding.test", "ROLE_PLANNER");
        String projectId = createProject(planner, "Guest Restore Wedding");
        String guestId = json(mockMvc.perform(post("/api/projects/" + projectId + "/guests")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + planner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "firstName", "Restorable", "lastName", "Guest",
                                "rsvpStatus", "PENDING", "partySize", 1))))
                .andExpect(status().isCreated())
                .andReturn()).get("id").asText();

        mockMvc.perform(delete("/api/projects/" + projectId + "/guests/" + guestId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + planner))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/projects/" + projectId + "/guests")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + planner))
                .andExpect(jsonPath("$.length()").value(0));

        mockMvc.perform(post("/api/projects/" + projectId + "/guests/" + guestId + "/restore")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + planner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.firstName").value("Restorable"));
        mockMvc.perform(get("/api/projects/" + projectId + "/guests")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + planner))
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void taskRestoreRoundTrips() throws Exception {
        String planner = register("restore-task-planner@wedding.test", "ROLE_PLANNER");
        String projectId = createProject(planner, "Task Restore Wedding");
        String taskId = json(mockMvc.perform(post("/api/projects/" + projectId + "/tasks")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + planner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "title", "Restorable task", "status", "TODO"))))
                .andExpect(status().isCreated())
                .andReturn()).get("id").asText();

        mockMvc.perform(delete("/api/projects/" + projectId + "/tasks/" + taskId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + planner))
                .andExpect(status().isNoContent());

        mockMvc.perform(post("/api/projects/" + projectId + "/tasks/" + taskId + "/restore")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + planner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Restorable task"));
        mockMvc.perform(get("/api/projects/" + projectId + "/tasks")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + planner))
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void expenseRestoreRoundTrips() throws Exception {
        String planner = register("restore-expense-planner@wedding.test", "ROLE_PLANNER");
        String projectId = createProject(planner, "Expense Restore Wedding");
        String expenseId = json(mockMvc.perform(post("/api/projects/" + projectId + "/expenses")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + planner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "description", "Restorable expense", "amount", 500,
                                "categoryId", categoryId(planner, "OTHER"), "paid", false))))
                .andExpect(status().isCreated())
                .andReturn()).get("id").asText();

        mockMvc.perform(delete("/api/projects/" + projectId + "/expenses/" + expenseId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + planner))
                .andExpect(status().isNoContent());

        mockMvc.perform(post("/api/projects/" + projectId + "/expenses/" + expenseId + "/restore")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + planner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.description").value("Restorable expense"));
        mockMvc.perform(get("/api/projects/" + projectId + "/expenses")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + planner))
                .andExpect(jsonPath("$.length()").value(1));
    }

    private void assertNamesExactly(JsonNode array, String... expectedNames) {
        Set<String> actual = new HashSet<>();
        array.forEach(n -> actual.add(n.get("name").asText()));
        assertThat(actual).containsExactlyInAnyOrder(expectedNames);
    }
}
