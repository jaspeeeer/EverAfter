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
 * Integration tests for the vendor catalog features: admin-managed categories, the global vendor
 * directory + add-from-directory, and vendor agreed price feeding the budget.
 */
@Transactional
class VendorCatalogIntegrationTest extends AbstractIntegrationTest {

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

    // --- Categories ---

    @Test
    void plannerReadsActiveCategoriesButCannotManageThem() throws Exception {
        String planner = register("cat-planner@wedding.test", "ROLE_PLANNER");

        mockMvc.perform(get("/api/vendor-categories")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + planner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.slug == 'VENUE')]").exists());

        mockMvc.perform(post("/api/admin/vendor-categories")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + planner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("name", "Officiant"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminCreatesRenamesAndDeleteDeactivatesInUseCategory() throws Exception {
        String admin = loginAdmin();

        // Create (slug derived).
        MvcResult created = mockMvc.perform(post("/api/admin/vendor-categories")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("name", "Officiant"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.slug").value("OFFICIANT"))
                .andReturn();
        String catId = json(created).get("id").asText();

        // Duplicate name → 409.
        mockMvc.perform(post("/api/admin/vendor-categories")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("name", "Officiant"))))
                .andExpect(status().isConflict());

        // Unused → hard delete.
        mockMvc.perform(delete("/api/admin/vendor-categories/" + catId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + admin))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/admin/vendor-categories")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + admin))
                .andExpect(jsonPath("$[?(@.slug == 'OFFICIANT')]").doesNotExist());

        // A seeded, in-use category (VENUE) can't be hard-deleted — it deactivates.
        String planner = register("cat-owner@wedding.test", "ROLE_PLANNER");
        String projectId = createProject(planner, "Cat Wedding");
        mockMvc.perform(post("/api/projects/" + projectId + "/vendors")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + planner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", "Grand Hall", "categoryId", categoryId(admin, "VENUE"),
                                "booked", false))))
                .andExpect(status().isCreated());

        mockMvc.perform(delete("/api/admin/vendor-categories/" + categoryId(admin, "VENUE"))
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + admin))
                .andExpect(status().isNoContent());
        // Still present but inactive (hidden from the public active list).
        mockMvc.perform(get("/api/vendor-categories")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + admin))
                .andExpect(jsonPath("$[?(@.slug == 'VENUE')]").doesNotExist());
        mockMvc.perform(get("/api/admin/vendor-categories")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + admin))
                .andExpect(jsonPath("$[?(@.slug == 'VENUE' && @.active == false)]").exists());
    }

    @Test
    void creatingAVendorWithAnUnknownCategoryIsRejected() throws Exception {
        String planner = register("cat-bad@wedding.test", "ROLE_PLANNER");
        String projectId = createProject(planner, "Bad Cat Wedding");

        mockMvc.perform(post("/api/projects/" + projectId + "/vendors")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + planner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", "Mystery",
                                "categoryId", "00000000-0000-0000-0000-000000000000",
                                "booked", false))))
                .andExpect(status().isBadRequest());
    }

    // --- Directory + add from directory ---

    @Test
    void adminManagesDirectoryAndPlannerAddsFromIt() throws Exception {
        String admin = loginAdmin();
        String planner = register("dir-planner@wedding.test", "ROLE_PLANNER");
        String projectId = createProject(planner, "Directory Wedding");

        MvcResult entry = mockMvc.perform(post("/api/admin/vendor-directory")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", "Blooms & Co",
                                "categoryId", categoryId(admin, "FLORIST"),
                                "contactEmail", "hello@blooms.test",
                                "typicalPrice", 45000))))
                .andExpect(status().isCreated())
                .andReturn();
        String directoryId = json(entry).get("id").asText();

        // Planner can browse the active directory.
        mockMvc.perform(get("/api/vendor-directory")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + planner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.name == 'Blooms & Co')]").exists());

        // Add it into the project — copies details + keeps the link.
        mockMvc.perform(post("/api/projects/" + projectId + "/vendors/from-directory")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + planner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("directoryId", directoryId))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Blooms & Co"))
                .andExpect(jsonPath("$.categoryName").value("Florist"))
                .andExpect(jsonPath("$.directoryId").value(directoryId));

        // A directory entry in use can't be hard-deleted; it deactivates.
        mockMvc.perform(delete("/api/admin/vendor-directory/" + directoryId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + admin))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/admin/vendor-directory")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + admin))
                .andExpect(jsonPath("$[?(@.id == '" + directoryId + "' && @.active == false)]").exists());
    }

    @Test
    void plannerCannotManageDirectoryOrReadTheAdminList() throws Exception {
        String planner = register("dir-guard@wedding.test", "ROLE_PLANNER");
        mockMvc.perform(get("/api/admin/vendor-directory")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + planner))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/admin/vendor-directory")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + planner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", "X", "categoryId", categoryId(planner, "MUSIC")))))
                .andExpect(status().isForbidden());
    }

    // --- Agreed price → budget ---

    @Test
    void agreedPriceCreatesAndClearsALinkedBudgetExpense() throws Exception {
        String planner = register("price-planner@wedding.test", "ROLE_PLANNER");
        String projectId = createProject(planner, "Priced Wedding");

        MvcResult created = mockMvc.perform(post("/api/projects/" + projectId + "/vendors")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + planner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", "Grand Venue",
                                "categoryId", categoryId(planner, "VENUE"),
                                "booked", true,
                                "agreedPrice", 250000))))
                .andExpect(status().isCreated())
                .andReturn();
        String vendorId = json(created).get("id").asText();

        entityManager.flush();
        entityManager.clear();

        // A linked expense appears and lands in the budget roll-up.
        mockMvc.perform(get("/api/projects/" + projectId + "/expenses")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + planner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].vendorId").value(vendorId))
                .andExpect(jsonPath("$[0].description").value("Grand Venue"));
        mockMvc.perform(get("/api/projects/" + projectId + "/budget")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + planner))
                .andExpect(jsonPath("$.totalExpenses").value(250000));

        // Clearing the price removes the linked expense.
        mockMvc.perform(put("/api/projects/" + projectId + "/vendors/" + vendorId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + planner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", "Grand Venue",
                                "categoryId", categoryId(planner, "VENUE"),
                                "booked", true))))
                .andExpect(status().isOk());

        entityManager.flush();
        entityManager.clear();

        mockMvc.perform(get("/api/projects/" + projectId + "/expenses")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + planner))
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void deletingAPricedVendorCascadesItsLinkedExpense() throws Exception {
        String planner = register("price-del@wedding.test", "ROLE_PLANNER");
        String projectId = createProject(planner, "Cascade Price Wedding");

        MvcResult created = mockMvc.perform(post("/api/projects/" + projectId + "/vendors")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + planner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", "Caterer",
                                "categoryId", categoryId(planner, "CATERING"),
                                "booked", true,
                                "agreedPrice", 120000))))
                .andExpect(status().isCreated())
                .andReturn();
        String vendorId = json(created).get("id").asText();

        mockMvc.perform(delete("/api/projects/" + projectId + "/vendors/" + vendorId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + planner))
                .andExpect(status().isNoContent());

        entityManager.flush();
        entityManager.clear();

        mockMvc.perform(get("/api/projects/" + projectId + "/expenses")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + planner))
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void expenseCanBeMappedToAVendorAndRemainsEditable() throws Exception {
        String planner = register("map-planner@wedding.test", "ROLE_PLANNER");
        String projectId = createProject(planner, "Mapped Wedding");

        // A plain vendor (no agreed price → no managed line).
        String vendorId = json(mockMvc.perform(post("/api/projects/" + projectId + "/vendors")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + planner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", "Sweet Cakes",
                                "categoryId", categoryId(planner, "CATERING"),
                                "booked", false))))
                .andExpect(status().isCreated())
                .andReturn()).get("id").asText();

        // A user expense mapped to that vendor exposes the vendor and is not managed.
        String expenseId = json(mockMvc.perform(post("/api/projects/" + projectId + "/expenses")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + planner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "description", "Cake deposit",
                                "amount", 8000,
                                "categoryId", categoryId(planner, "CATERING"),
                                "vendorId", vendorId,
                                "paid", false))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.vendorId").value(vendorId))
                .andExpect(jsonPath("$.vendorName").value("Sweet Cakes"))
                .andExpect(jsonPath("$.managed").value(false))
                .andReturn()).get("id").asText();

        // Unlike the managed agreed-price line, a manual mapping can be deleted directly.
        mockMvc.perform(delete("/api/projects/" + projectId + "/expenses/" + expenseId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + planner))
                .andExpect(status().isNoContent());
    }

    @Test
    void deletingAVendorUnmapsManualExpensesButRemovesTheManagedLine() throws Exception {
        String planner = register("unmap-planner@wedding.test", "ROLE_PLANNER");
        String projectId = createProject(planner, "Unmap Wedding");

        // Vendor with an agreed price → a system-owned managed budget line.
        String pricedId = json(mockMvc.perform(post("/api/projects/" + projectId + "/vendors")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + planner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", "Grand Venue",
                                "categoryId", categoryId(planner, "VENUE"),
                                "booked", true,
                                "agreedPrice", 250000))))
                .andExpect(status().isCreated())
                .andReturn()).get("id").asText();

        // A plain vendor with a manual expense mapped to it.
        String plainId = json(mockMvc.perform(post("/api/projects/" + projectId + "/vendors")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + planner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", "DJ Bob",
                                "categoryId", categoryId(planner, "MUSIC"),
                                "booked", false))))
                .andExpect(status().isCreated())
                .andReturn()).get("id").asText();
        mockMvc.perform(post("/api/projects/" + projectId + "/expenses")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + planner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "description", "DJ balance",
                                "amount", 5000,
                                "categoryId", categoryId(planner, "MUSIC"),
                                "vendorId", plainId,
                                "paid", false))))
                .andExpect(status().isCreated());

        entityManager.flush();
        entityManager.clear();

        // Managed line + manual mapping.
        mockMvc.perform(get("/api/projects/" + projectId + "/expenses")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + planner))
                .andExpect(jsonPath("$.length()").value(2));

        // Deleting the plain vendor unmaps its expense (SET NULL) rather than deleting it.
        mockMvc.perform(delete("/api/projects/" + projectId + "/vendors/" + plainId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + planner))
                .andExpect(status().isNoContent());
        entityManager.flush();
        entityManager.clear();
        mockMvc.perform(get("/api/projects/" + projectId + "/expenses")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + planner))
                .andExpect(jsonPath("$.length()").value(2));

        // Deleting the priced vendor removes its managed line.
        mockMvc.perform(delete("/api/projects/" + projectId + "/vendors/" + pricedId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + planner))
                .andExpect(status().isNoContent());
        entityManager.flush();
        entityManager.clear();
        mockMvc.perform(get("/api/projects/" + projectId + "/expenses")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + planner))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].description").value("DJ balance"));
    }

    private String pricedVendor(String token, String projectId, String name, int fullAmount)
            throws Exception {
        return json(mockMvc.perform(post("/api/projects/" + projectId + "/vendors")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", name,
                                "categoryId", categoryId(token, "VENUE"),
                                "booked", true,
                                "agreedPrice", fullAmount))))
                .andExpect(status().isCreated())
                .andReturn()).get("id").asText();
    }

    @Test
    void vendorPaymentsTrackInstallmentsAndFeedTheBudget() throws Exception {
        String planner = register("pay-planner@wedding.test", "ROLE_PLANNER");
        String projectId = createProject(planner, "Installment Wedding");
        String vendorId = pricedVendor(planner, projectId, "Grand Venue", 100000);

        String payments = "/api/projects/" + projectId + "/vendors/" + vendorId + "/payments";
        mockMvc.perform(post(payments)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + planner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "amount", 40000, "paidOn", "2026-01-15", "note", "Deposit"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.amount").value(40000));
        mockMvc.perform(post(payments)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + planner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "amount", 60000, "paidOn", "2026-03-01"))))
                .andExpect(status().isCreated());

        entityManager.flush();
        entityManager.clear();

        mockMvc.perform(get(payments).header(HttpHeaders.AUTHORIZATION, "Bearer " + planner))
                .andExpect(jsonPath("$.length()").value(2));
        mockMvc.perform(get("/api/projects/" + projectId + "/vendors")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + planner))
                .andExpect(jsonPath("$[0].amountPaid").value(100000));
        mockMvc.perform(get("/api/projects/" + projectId + "/budget")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + planner))
                .andExpect(jsonPath("$.totalExpenses").value(100000))
                .andExpect(jsonPath("$.totalPaid").value(100000))
                .andExpect(jsonPath("$.totalOutstanding").value(0));

        // The balance is now zero, so a further payment is rejected.
        mockMvc.perform(post(payments)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + planner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "amount", 1, "paidOn", "2026-03-02"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void deletingAPaymentReducesTheBudgetPaidAmount() throws Exception {
        String planner = register("paydel-planner@wedding.test", "ROLE_PLANNER");
        String projectId = createProject(planner, "Refund Wedding");
        String vendorId = pricedVendor(planner, projectId, "Big Venue", 100000);

        String payments = "/api/projects/" + projectId + "/vendors/" + vendorId + "/payments";
        String paymentId = json(mockMvc.perform(post(payments)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + planner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "amount", 40000, "paidOn", "2026-02-01"))))
                .andExpect(status().isCreated())
                .andReturn()).get("id").asText();

        mockMvc.perform(get("/api/projects/" + projectId + "/budget")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + planner))
                .andExpect(jsonPath("$.totalPaid").value(40000));

        mockMvc.perform(delete(payments + "/" + paymentId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + planner))
                .andExpect(status().isNoContent());
        entityManager.flush();
        entityManager.clear();

        mockMvc.perform(get("/api/projects/" + projectId + "/budget")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + planner))
                .andExpect(jsonPath("$.totalPaid").value(0));
    }

    @Test
    void cannotRecordAPaymentWithoutAFullAmount() throws Exception {
        String planner = register("nofull-planner@wedding.test", "ROLE_PLANNER");
        String projectId = createProject(planner, "No Price Wedding");
        String vendorId = json(mockMvc.perform(post("/api/projects/" + projectId + "/vendors")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + planner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", "Undecided DJ",
                                "categoryId", categoryId(planner, "MUSIC"),
                                "booked", false))))
                .andExpect(status().isCreated())
                .andReturn()).get("id").asText();

        mockMvc.perform(post("/api/projects/" + projectId + "/vendors/" + vendorId + "/payments")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + planner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "amount", 5000, "paidOn", "2026-02-01"))))
                .andExpect(status().isBadRequest());
    }

    // --- Package vendors ---

    private String vendorWithParent(String token, String projectId, String name, String slug,
                                    String parentId) throws Exception {
        var body = new java.util.HashMap<String, Object>();
        body.put("name", name);
        body.put("categoryId", categoryId(token, slug));
        body.put("booked", false);
        if (parentId != null) {
            body.put("parentId", parentId);
        }
        return json(mockMvc.perform(post("/api/projects/" + projectId + "/vendors")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andReturn()).get("id").asText();
    }

    @Test
    void aPackagesPriceMakesOneManagedExpenseAndItsItemsGetNone() throws Exception {
        String planner = register("pkg-planner@wedding.test", "ROLE_PLANNER");
        String projectId = createProject(planner, "Package Wedding");

        // The package itself: one bundled price for everything underneath it.
        String pkgId = json(mockMvc.perform(post("/api/projects/" + projectId + "/vendors")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + planner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", "All-In Coordination Package",
                                "categoryId", categoryId(planner, "OTHER"),
                                "booked", true,
                                "agreedPrice", 300000))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.parentId").doesNotExist())
                .andReturn()).get("id").asText();

        // Two items nested under it — neither carries its own price.
        String cateringId = vendorWithParent(planner, projectId, "Catering (bundled)", "CATERING", pkgId);
        vendorWithParent(planner, projectId, "Florals (bundled)", "FLORIST", pkgId);

        entityManager.flush();
        entityManager.clear();

        // Exactly one expense in the whole project: the package's managed line.
        mockMvc.perform(get("/api/projects/" + projectId + "/expenses")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + planner))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].vendorId").value(pkgId))
                .andExpect(jsonPath("$[0].amount").value(300000));

        // The items don't inflate the budget — it's still just the one bundled price.
        mockMvc.perform(get("/api/projects/" + projectId + "/budget")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + planner))
                .andExpect(jsonPath("$.totalExpenses").value(300000));

        // The nested vendor list shows the parent link.
        mockMvc.perform(get("/api/projects/" + projectId + "/vendors")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + planner))
                .andExpect(jsonPath("$[?(@.id == '" + cateringId + "')].parentId").value(pkgId));
    }

    @Test
    void aPackageItemCannotHaveItsOwnPriceOrPayment() throws Exception {
        String planner = register("pkg-item-planner@wedding.test", "ROLE_PLANNER");
        String projectId = createProject(planner, "Item Wedding");

        String pkgId = json(mockMvc.perform(post("/api/projects/" + projectId + "/vendors")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + planner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", "Package", "categoryId", categoryId(planner, "OTHER"),
                                "booked", true, "agreedPrice", 100000))))
                .andExpect(status().isCreated())
                .andReturn()).get("id").asText();

        // Creating an item with its own price is rejected.
        mockMvc.perform(post("/api/projects/" + projectId + "/vendors")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + planner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", "Priced item", "categoryId", categoryId(planner, "CATERING"),
                                "booked", false, "agreedPrice", 5000, "parentId", pkgId))))
                .andExpect(status().isBadRequest());

        // A price-less item can't take a payment either.
        String itemId = vendorWithParent(planner, projectId, "Plain item", "CATERING", pkgId);
        mockMvc.perform(post("/api/projects/" + projectId + "/vendors/" + itemId + "/payments")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + planner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "amount", 1000, "paidOn", "2026-01-01"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void nestingIsOneLevelAndAPackageWithItemsCannotBecomeAnItem() throws Exception {
        String planner = register("pkg-nest-planner@wedding.test", "ROLE_PLANNER");
        String projectId = createProject(planner, "Nesting Wedding");

        String pkgId = vendorWithParent(planner, projectId, "Package", "OTHER", null);
        String itemId = vendorWithParent(planner, projectId, "Item", "CATERING", pkgId);

        // An item's parent must itself be top-level — nesting an item under another item is rejected.
        mockMvc.perform(post("/api/projects/" + projectId + "/vendors")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + planner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", "Sub-item", "categoryId", categoryId(planner, "MUSIC"),
                                "booked", false, "parentId", itemId))))
                .andExpect(status().isBadRequest());

        // A package that already has items can't itself become an item of another package.
        String otherPkgId = vendorWithParent(planner, projectId, "Other package", "OTHER", null);
        mockMvc.perform(put("/api/projects/" + projectId + "/vendors/" + pkgId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + planner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", "Package", "categoryId", categoryId(planner, "OTHER"),
                                "booked", true, "parentId", otherPkgId))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void deletingAPackageCascadesItsItemsAndTheirBudgetLine() throws Exception {
        String planner = register("pkg-del-planner@wedding.test", "ROLE_PLANNER");
        String projectId = createProject(planner, "Cascade Package Wedding");

        String pkgId = json(mockMvc.perform(post("/api/projects/" + projectId + "/vendors")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + planner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", "Package", "categoryId", categoryId(planner, "OTHER"),
                                "booked", true, "agreedPrice", 150000))))
                .andExpect(status().isCreated())
                .andReturn()).get("id").asText();
        vendorWithParent(planner, projectId, "Item", "CATERING", pkgId);

        mockMvc.perform(delete("/api/projects/" + projectId + "/vendors/" + pkgId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + planner))
                .andExpect(status().isNoContent());

        entityManager.flush();
        entityManager.clear();

        // Both the package and its item are gone, and so is the budget line.
        mockMvc.perform(get("/api/projects/" + projectId + "/vendors")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + planner))
                .andExpect(jsonPath("$.length()").value(0));
        mockMvc.perform(get("/api/projects/" + projectId + "/expenses")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + planner))
                .andExpect(jsonPath("$.length()").value(0));
    }
}
