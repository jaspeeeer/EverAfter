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
import com.wedding.planner.domain.Expense;
import com.wedding.planner.domain.Guest;
import com.wedding.planner.domain.Task;
import com.wedding.planner.domain.Vendor;
import com.wedding.planner.repository.ExpenseRepository;
import com.wedding.planner.repository.GuestRepository;
import com.wedding.planner.repository.TaskRepository;
import com.wedding.planner.repository.VendorRepository;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
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

    @Autowired
    private GuestRepository guestRepository;

    @Autowired
    private TaskRepository taskRepository;

    @Autowired
    private VendorRepository vendorRepository;

    @Autowired
    private ExpenseRepository expenseRepository;

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

        // Unauthenticated respond. Party size is absent from the request, and the project hasn't
        // opted into guest-controlled party size (default off) — the planner's original value of
        // 2 is preserved, not reset.
        mockMvc.perform(put("/api/public/rsvp/" + rsvpToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "rsvpStatus", "ATTENDING",
                                "dietaryNotes", "1 vegan"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rsvpStatus").value("ATTENDING"))
                .andExpect(jsonPath("$.partySize").value(2));

        // The change is visible to the planner — party size untouched at 2.
        mockMvc.perform(get("/api/projects/" + projectId + "/guests")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + planner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].rsvpStatus").value("ATTENDING"))
                .andExpect(jsonPath("$[0].partySize").value(2));
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
    void softDeletedGuestsRsvpLinkIs404() throws Exception {
        String planner = register("rsvp-soft-delete-planner@wedding.test", "ROLE_PLANNER");
        String projectId = createProject(planner, "Soft Delete RSVP Wedding");

        MvcResult created = mockMvc.perform(post("/api/projects/" + projectId + "/guests")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + planner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "firstName", "Soon",
                                "lastName", "Gone",
                                "rsvpStatus", "PENDING",
                                "partySize", 1))))
                .andExpect(status().isCreated())
                .andReturn();
        String rsvpToken = json(created).get("rsvpToken").asText();
        UUID guestId = UUID.fromString(json(created).get("id").asText());

        // Still live: the link works.
        mockMvc.perform(get("/api/public/rsvp/" + rsvpToken))
                .andExpect(status().isOk());

        // Nothing wires a soft-delete through the API yet (that's a later change) — stamp the
        // tombstone directly, matching how a deleted guest will actually look in the DB.
        Guest guest = guestRepository.findById(guestId).orElseThrow();
        guest.setDeletedAt(Instant.now());
        guestRepository.saveAndFlush(guest);

        // A "deleted" guest's RSVP link must not keep working — @SQLRestriction hides it from
        // findByRsvpToken the same way it hides the row from every other read.
        mockMvc.perform(get("/api/public/rsvp/" + rsvpToken))
                .andExpect(status().isNotFound());
        mockMvc.perform(put("/api/public/rsvp/" + rsvpToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "rsvpStatus", "ATTENDING", "dietaryNotes", "n/a"))))
                .andExpect(status().isNotFound());
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

    @Test
    void adminStatsExcludeSoftDeletedRows() throws Exception {
        String planner = register("stats-soft-delete-planner@wedding.test", "ROLE_PLANNER");
        String projectId = createProject(planner, "Stats Soft Delete Wedding");
        String admin = loginAdmin();

        long before = statTotal(admin, "totalTasks")
                + statTotal(admin, "totalVendors")
                + statTotal(admin, "totalExpenses")
                + statTotal(admin, "totalGuests");

        String categoryId = firstVendorCategoryId(planner);

        UUID taskId = UUID.fromString(createEntity(planner, "/api/projects/" + projectId + "/tasks",
                Map.of("title", "Soon gone task", "status", "TODO")));
        UUID vendorId = UUID.fromString(createEntity(planner, "/api/projects/" + projectId + "/vendors",
                Map.of("name", "Soon gone vendor", "categoryId", categoryId)));
        UUID expenseId = UUID.fromString(createEntity(planner, "/api/projects/" + projectId + "/expenses",
                Map.of("description", "Soon gone expense", "amount", 100, "categoryId", categoryId,
                        "paid", false)));
        UUID guestId = UUID.fromString(createEntity(planner, "/api/projects/" + projectId + "/guests",
                Map.of("firstName", "Soon", "lastName", "Gone", "rsvpStatus", "PENDING",
                        "partySize", 1)));

        assertStatTotalsSum(admin, before + 4);

        Task task = taskRepository.findById(taskId).orElseThrow();
        task.setDeletedAt(Instant.now());
        taskRepository.saveAndFlush(task);

        Vendor vendor = vendorRepository.findById(vendorId).orElseThrow();
        vendor.setDeletedAt(Instant.now());
        vendorRepository.saveAndFlush(vendor);

        Expense expense = expenseRepository.findById(expenseId).orElseThrow();
        expense.setDeletedAt(Instant.now());
        expenseRepository.saveAndFlush(expense);

        Guest guest = guestRepository.findById(guestId).orElseThrow();
        guest.setDeletedAt(Instant.now());
        guestRepository.saveAndFlush(guest);

        // Every count that was inflated by the live rows drops back to the pre-creation baseline
        // — AdminService.stats() uses plain JpaRepository.count(), which @SQLRestriction filters
        // automatically, same as any other read.
        assertStatTotalsSum(admin, before);
    }

    private String firstVendorCategoryId(String token) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/vendor-categories")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andReturn();
        return json(result).get(0).get("id").asText();
    }

    private String createEntity(String token, String path, Map<String, Object> body) throws Exception {
        MvcResult result = mockMvc.perform(post(path)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andReturn();
        return json(result).get("id").asText();
    }

    private long statTotal(String adminToken, String field) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/admin/stats")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn();
        return json(result).get(field).asLong();
    }

    private void assertStatTotalsSum(String adminToken, long expected) throws Exception {
        long actual = statTotal(adminToken, "totalTasks")
                + statTotal(adminToken, "totalVendors")
                + statTotal(adminToken, "totalExpenses")
                + statTotal(adminToken, "totalGuests");
        assertThat(actual).isEqualTo(expected);
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

    // --- Invitation page metadata (V19) ---

    @Test
    void projectVenuesAndTimesRoundTripThroughPutAndSurfaceOnPublicRsvp() throws Exception {
        String planner = register("venue-planner@wedding.test", "ROLE_PLANNER");
        String projectId = createProject(planner, "Venue Wedding");

        // PUT the invitation-page metadata onto the project — both ceremony and reception.
        mockMvc.perform(put("/api/projects/" + projectId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + planner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", "Venue Wedding",
                                "ceremonyVenueName", "Manila Cathedral",
                                "ceremonyVenueAddress", "Cabildo St, Intramuros, Manila",
                                "receptionVenueName", "Grand Hall",
                                "receptionVenueAddress", "Hall Ave, Manila",
                                "ceremonyTime", "15:00:00",
                                "receptionTime", "18:30:00"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ceremonyVenueName").value("Manila Cathedral"))
                .andExpect(jsonPath("$.ceremonyVenueAddress").value("Cabildo St, Intramuros, Manila"))
                .andExpect(jsonPath("$.receptionVenueName").value("Grand Hall"))
                .andExpect(jsonPath("$.receptionVenueAddress").value("Hall Ave, Manila"))
                .andExpect(jsonPath("$.ceremonyTime").value("15:00:00"))
                .andExpect(jsonPath("$.receptionTime").value("18:30:00"));

        // A guest linked to this project sees the same fields on the public RSVP page.
        MvcResult guest = mockMvc.perform(post("/api/projects/" + projectId + "/guests")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + planner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "firstName", "Guest",
                                "lastName", "One",
                                "rsvpStatus", "PENDING"))))
                .andExpect(status().isCreated())
                .andReturn();
        String rsvpToken = json(guest).get("rsvpToken").asText();

        mockMvc.perform(get("/api/public/rsvp/" + rsvpToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ceremonyVenueName").value("Manila Cathedral"))
                .andExpect(jsonPath("$.ceremonyVenueAddress").value("Cabildo St, Intramuros, Manila"))
                .andExpect(jsonPath("$.receptionVenueName").value("Grand Hall"))
                .andExpect(jsonPath("$.receptionVenueAddress").value("Hall Ave, Manila"))
                .andExpect(jsonPath("$.ceremonyTime").value("15:00:00"))
                .andExpect(jsonPath("$.receptionTime").value("18:30:00"));
    }

    @Test
    void publicRsvpVenueFieldsAreNullWhenNotSet() throws Exception {
        String planner = register("no-venue-planner@wedding.test", "ROLE_PLANNER");
        String projectId = createProject(planner, "No Venue Wedding");

        MvcResult guest = mockMvc.perform(post("/api/projects/" + projectId + "/guests")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + planner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "firstName", "Guest",
                                "rsvpStatus", "PENDING"))))
                .andExpect(status().isCreated())
                .andReturn();
        String rsvpToken = json(guest).get("rsvpToken").asText();

        mockMvc.perform(get("/api/public/rsvp/" + rsvpToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ceremonyVenueName").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.ceremonyVenueAddress").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.receptionVenueName").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.receptionVenueAddress").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.ceremonyTime").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.receptionTime").value(org.hamcrest.Matchers.nullValue()));
    }

    // --- Attire, entourage, and invitation extras (V21) ---

    @Test
    void attireEntourageAndInvitationExtrasRoundTripThroughPutAndSurfaceOnPublicRsvp() throws Exception {
        String planner = register("attire-planner@wedding.test", "ROLE_PLANNER");
        String projectId = createProject(planner, "Attire Wedding");

        mockMvc.perform(put("/api/projects/" + projectId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + planner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", "Attire Wedding",
                                "dressCode", "Garden party formal",
                                "attireNotesMen", "Barong or long-sleeve, dark trousers",
                                "attireNotesWomen", "Cocktail-length or long dress",
                                "attirePalette", "#f4a5a5,#a5c4f4",
                                "rsvpDeadline", "2027-05-01",
                                "kidsPolicy", "Adults-only celebration",
                                "socialHashtag", "AttireWedding2027"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dressCode").value("Garden party formal"))
                .andExpect(jsonPath("$.attireNotesMen").value("Barong or long-sleeve, dark trousers"))
                .andExpect(jsonPath("$.attireNotesWomen").value("Cocktail-length or long dress"))
                .andExpect(jsonPath("$.attirePalette").value("#f4a5a5,#a5c4f4"))
                .andExpect(jsonPath("$.rsvpDeadline").value("2027-05-01"))
                .andExpect(jsonPath("$.kidsPolicy").value("Adults-only celebration"))
                .andExpect(jsonPath("$.socialHashtag").value("AttireWedding2027"));

        // Two entourage members, added in order.
        mockMvc.perform(post("/api/projects/" + projectId + "/entourage")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + planner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("role", "Best Man", "name", "Juan Dela Cruz"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sortOrder").value(0));
        mockMvc.perform(post("/api/projects/" + projectId + "/entourage")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + planner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("role", "Maid of Honor", "name", "Maria Santos"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sortOrder").value(1));

        MvcResult guest = mockMvc.perform(post("/api/projects/" + projectId + "/guests")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + planner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "firstName", "Guest", "rsvpStatus", "PENDING"))))
                .andExpect(status().isCreated())
                .andReturn();
        String rsvpToken = json(guest).get("rsvpToken").asText();

        mockMvc.perform(get("/api/public/rsvp/" + rsvpToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dressCode").value("Garden party formal"))
                .andExpect(jsonPath("$.attireNotesMen").value("Barong or long-sleeve, dark trousers"))
                .andExpect(jsonPath("$.attireNotesWomen").value("Cocktail-length or long dress"))
                .andExpect(jsonPath("$.attirePalette").value("#f4a5a5,#a5c4f4"))
                .andExpect(jsonPath("$.rsvpDeadline").value("2027-05-01"))
                .andExpect(jsonPath("$.kidsPolicy").value("Adults-only celebration"))
                .andExpect(jsonPath("$.socialHashtag").value("AttireWedding2027"))
                .andExpect(jsonPath("$.entourage.length()").value(2))
                .andExpect(jsonPath("$.entourage[0].role").value("Best Man"))
                .andExpect(jsonPath("$.entourage[0].name").value("Juan Dela Cruz"))
                .andExpect(jsonPath("$.entourage[0].id").doesNotExist())
                .andExpect(jsonPath("$.entourage[1].role").value("Maid of Honor"))
                .andExpect(jsonPath("$.entourage[1].name").value("Maria Santos"));
    }

    @Test
    void publicRsvpAttireAndExtrasAreNullAndEntourageIsEmptyWhenNotSet() throws Exception {
        String planner = register("no-attire-planner@wedding.test", "ROLE_PLANNER");
        String projectId = createProject(planner, "No Attire Wedding");

        MvcResult guest = mockMvc.perform(post("/api/projects/" + projectId + "/guests")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + planner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "firstName", "Guest", "rsvpStatus", "PENDING"))))
                .andExpect(status().isCreated())
                .andReturn();
        String rsvpToken = json(guest).get("rsvpToken").asText();

        mockMvc.perform(get("/api/public/rsvp/" + rsvpToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dressCode").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.kidsPolicy").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.socialHashtag").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.entourage").isArray())
                .andExpect(jsonPath("$.entourage.length()").value(0));
    }

    @Test
    void entourageMoveUpAndDownReorderMembersAndRemoveDeletesThem() throws Exception {
        String planner = register("entourage-planner@wedding.test", "ROLE_PLANNER");
        String projectId = createProject(planner, "Entourage Wedding");

        String firstId = addEntourageMember(planner, projectId, "Best Man", "First");
        String secondId = addEntourageMember(planner, projectId, "Groomsman", "Second");
        addEntourageMember(planner, projectId, "Groomsman", "Third");

        // Move the second entry up — it swaps with the first.
        mockMvc.perform(put("/api/projects/" + projectId + "/entourage/" + secondId + "/move-up")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + planner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sortOrder").value(0));

        mockMvc.perform(get("/api/projects/" + projectId + "/entourage")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + planner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Second"))
                .andExpect(jsonPath("$[1].name").value("First"))
                .andExpect(jsonPath("$[2].name").value("Third"));

        // Moving the first entry (now at the top) further up is a no-op.
        mockMvc.perform(put("/api/projects/" + projectId + "/entourage/" + secondId + "/move-up")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + planner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sortOrder").value(0));

        mockMvc.perform(delete("/api/projects/" + projectId + "/entourage/" + firstId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + planner))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/projects/" + projectId + "/entourage")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + planner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void entourageMemberFromAnotherProjectIsNotAccessible() throws Exception {
        String plannerA = register("entourage-tenant-a@wedding.test", "ROLE_PLANNER");
        String plannerB = register("entourage-tenant-b@wedding.test", "ROLE_PLANNER");
        String projectA = createProject(plannerA, "Tenant A Wedding");
        String projectB = createProject(plannerB, "Tenant B Wedding");
        String memberIdA = addEntourageMember(plannerA, projectA, "Best Man", "Tenant A Member");
        String memberIdB = addEntourageMember(plannerB, projectB, "Best Man", "Tenant B Member");

        // plannerA can access projectA, but memberIdB belongs to projectB — 404, not leaked.
        mockMvc.perform(put("/api/projects/" + projectA + "/entourage/" + memberIdB)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + plannerA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("role", "Best Man", "name", "Hijacked"))))
                .andExpect(status().isNotFound());

        // plannerB has no access to projectA at all — 403 before the service is even reached.
        mockMvc.perform(delete("/api/projects/" + projectA + "/entourage/" + memberIdA)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + plannerB))
                .andExpect(status().isForbidden());
    }

    @Test
    void importFromGuestsAddsEligibleGuestsSkipsOthersAndDedupsOnRerun() throws Exception {
        String planner = register("import-planner@wedding.test", "ROLE_PLANNER");
        String projectId = createProject(planner, "Import Wedding");

        String bestManRoleId = guestRoleId(planner, "BEST_MAN");
        String parentsRoleId = guestRoleId(planner, "PARENTS");

        String eligibleGuestId = createGuestWithRole(planner, projectId, "Ana Cruz", bestManRoleId);
        String ineligibleGuestId = createGuestWithRole(planner, projectId, "Ben Reyes", parentsRoleId);
        String noRoleGuestId = createGuestWithRole(planner, projectId, "No Role Guy", null);

        mockMvc.perform(post("/api/projects/" + projectId + "/entourage/import-from-guests")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + planner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "entries", List.of(
                                        Map.of("guestId", eligibleGuestId, "roleId", bestManRoleId),
                                        Map.of("guestId", ineligibleGuestId, "roleId", parentsRoleId),
                                        Map.of("guestId", noRoleGuestId, "roleId", bestManRoleId))))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.added").value(1))
                .andExpect(jsonPath("$.skippedNotEligible").value(2))
                .andExpect(jsonPath("$.skippedAlreadyPresent").value(0));

        mockMvc.perform(get("/api/projects/" + projectId + "/entourage")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + planner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].name").value("Ana Cruz"))
                .andExpect(jsonPath("$[0].role").value("Best Man"))
                .andExpect(jsonPath("$[0].sortOrder").value(0));

        // Re-running the import against the same (guest, role) pair is a no-op, not a duplicate row.
        mockMvc.perform(post("/api/projects/" + projectId + "/entourage/import-from-guests")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + planner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "entries",
                                List.of(Map.of("guestId", eligibleGuestId, "roleId", bestManRoleId))))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.added").value(0))
                .andExpect(jsonPath("$.skippedAlreadyPresent").value(1));

        mockMvc.perform(get("/api/projects/" + projectId + "/entourage")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + planner))
                .andExpect(jsonPath("$.length()").value(1));
    }

    private String guestRoleId(String token, String slug) throws Exception {
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

    private String createGuestWithRole(String token, String projectId, String name, String roleId)
            throws Exception {
        var body = new java.util.HashMap<String, Object>();
        body.put("firstName", name);
        body.put("rsvpStatus", "PENDING");
        body.put("partySize", 1);
        if (roleId != null) {
            body.put("roleIds", List.of(roleId));
        }
        MvcResult result = mockMvc.perform(post("/api/projects/" + projectId + "/guests")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andReturn();
        return json(result).get("id").asText();
    }

    private String addEntourageMember(String token, String projectId, String role, String name) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/projects/" + projectId + "/entourage")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("role", role, "name", name))))
                .andExpect(status().isCreated())
                .andReturn();
        return json(result).get("id").asText();
    }

    // --- Add-to-calendar (.ics) ---

    @Test
    void calendarIcsContainsTheWeddingDetailsAndIsKeyedToTheGuestsOwnToken() throws Exception {
        String planner = register("ics-planner@wedding.test", "ROLE_PLANNER");
        String projectId = createProject(planner, "ICS Wedding");
        mockMvc.perform(put("/api/projects/" + projectId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + planner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", "ICS Wedding",
                                "weddingDate", "2027-06-12",
                                "ceremonyVenueName", "Manila Cathedral",
                                "ceremonyVenueAddress", "Cabildo St, Intramuros, Manila",
                                "ceremonyTime", "15:00:00",
                                "receptionTime", "18:30:00"))))
                .andExpect(status().isOk());
        MvcResult guest = mockMvc.perform(post("/api/projects/" + projectId + "/guests")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + planner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "firstName", "Guest", "rsvpStatus", "PENDING"))))
                .andExpect(status().isCreated())
                .andReturn();
        String rsvpToken = json(guest).get("rsvpToken").asText();

        MvcResult ics = mockMvc.perform(get("/api/public/rsvp/" + rsvpToken + "/calendar.ics"))
                .andExpect(status().isOk())
                .andReturn();

        assertThat(ics.getResponse().getContentType()).startsWith("text/calendar");
        assertThat(ics.getResponse().getHeader(HttpHeaders.CONTENT_DISPOSITION))
                .contains("wedding.ics");
        String body = ics.getResponse().getContentAsString();
        assertThat(body).contains("BEGIN:VCALENDAR");
        assertThat(body).contains("SUMMARY:ICS Wedding");
        assertThat(body).contains("DTSTART:20270612T150000");
        assertThat(body).contains("DTEND:20270612T183000");
        assertThat(body).contains("LOCATION:Manila Cathedral\\, Cabildo St");
        assertThat(body).contains("UID:" + rsvpToken + "@wedding-planner");
        assertThat(body).contains("DESCRIPTION:RSVP:");
        assertThat(body).contains(rsvpToken);
    }

    @Test
    void calendarIcsWithoutAWeddingDateIs400() throws Exception {
        String planner = register("ics-nodate-planner@wedding.test", "ROLE_PLANNER");
        String projectId = createProject(planner, "No Date Wedding");
        MvcResult guest = mockMvc.perform(post("/api/projects/" + projectId + "/guests")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + planner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "firstName", "Guest", "rsvpStatus", "PENDING"))))
                .andExpect(status().isCreated())
                .andReturn();
        String rsvpToken = json(guest).get("rsvpToken").asText();

        mockMvc.perform(get("/api/public/rsvp/" + rsvpToken + "/calendar.ics"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void calendarIcsForAnUnknownTokenIs404() throws Exception {
        mockMvc.perform(
                        get("/api/public/rsvp/00000000-0000-0000-0000-000000000000/calendar.ics"))
                .andExpect(status().isNotFound());
    }
}
