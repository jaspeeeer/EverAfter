package com.wedding.planner.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wedding.planner.AbstractIntegrationTest;
import com.wedding.planner.domain.Vendor;
import com.wedding.planner.repository.VendorRepository;
import java.time.Instant;
import java.util.HashMap;
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
 * Integration tests for the admin vendor reports (cross-project aggregations).
 */
@Transactional
class VendorReportIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private VendorRepository vendorRepository;

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
                .andReturn();
        for (JsonNode node : json(result)) {
            if (node.get("slug").asText().equals(slug)) {
                return node.get("id").asText();
            }
        }
        throw new IllegalStateException("No seeded category " + slug);
    }

    private String project(String token, String name, String weddingDate) throws Exception {
        var body = new HashMap<String, Object>();
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

    private void vendor(String token, String projectId, String name, String slug,
                        boolean booked, Integer price) throws Exception {
        var body = new HashMap<String, Object>();
        body.put("name", name);
        body.put("categoryId", categoryId(token, slug));
        body.put("booked", booked);
        if (price != null) {
            body.put("agreedPrice", price);
        }
        mockMvc.perform(post("/api/projects/" + projectId + "/vendors")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated());
    }

    @Test
    void reportsAggregateAcrossProjectsAndRespectTheDateWindow() throws Exception {
        String planner = register("rep-planner@wedding.test", "ROLE_PLANNER");
        String admin = loginAdmin();

        // Two 2027 weddings both using "Grand Hall" (venue); one 2025 wedding also using it.
        String p1 = project(planner, "Rep A", "2027-06-01");
        vendor(planner, p1, "Grand Hall", "VENUE", true, 200000);
        vendor(planner, p1, "Tasty Catering", "CATERING", false, null);

        String p2 = project(planner, "Rep B", "2027-08-15");
        vendor(planner, p2, "Grand Hall", "VENUE", true, 220000);

        String p3 = project(planner, "Rep C", "2025-05-01");
        vendor(planner, p3, "Grand Hall", "VENUE", false, null);

        // Vendors by category: VENUE has 3 vendors, 2 booked.
        mockMvc.perform(get("/api/admin/reports/vendors-by-category")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.categoryName == 'Venue')].vendorCount").value(3))
                .andExpect(jsonPath("$[?(@.categoryName == 'Venue')].bookedCount").value(2));

        // In-demand within 2027: "Grand Hall" used twice (the 2025 one is excluded).
        mockMvc.perform(get("/api/admin/reports/in-demand-vendors")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + admin)
                        .param("from", "2027-01-01")
                        .param("to", "2027-12-31"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].vendorName").value("Grand Hall"))
                .andExpect(jsonPath("$[0].usageCount").value(2))
                .andExpect(jsonPath("$[0].bookedCount").value(2));

        // Booking conversion: overall considered/booked present.
        mockMvc.perform(get("/api/admin/reports/booking-conversion")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalConsidered").isNumber())
                .andExpect(jsonPath("$.totalBooked").isNumber())
                .andExpect(jsonPath("$.categories[?(@.categoryName == 'Venue')].booked").value(2));
    }

    @Test
    void packageItemsDoNotInflateTheReports() throws Exception {
        String planner = register("rep-pkg-planner@wedding.test", "ROLE_PLANNER");
        String admin = loginAdmin();
        String projectId = project(planner, "Rep Package Wedding", "2027-09-01");

        var pkgBody = new HashMap<String, Object>();
        pkgBody.put("name", "All-In Package");
        pkgBody.put("categoryId", categoryId(planner, "VENUE"));
        pkgBody.put("booked", true);
        pkgBody.put("agreedPrice", 400000);
        String pkgId = json(mockMvc.perform(post("/api/projects/" + projectId + "/vendors")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + planner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(pkgBody)))
                .andExpect(status().isCreated())
                .andReturn()).get("id").asText();

        // Two items bundled under it, in a different category each — neither should show up as
        // its own "vendor" in the reports; only the package (Venue, 1 vendor) should.
        var item1 = new HashMap<String, Object>();
        item1.put("name", "Bundled caterer");
        item1.put("categoryId", categoryId(planner, "CATERING"));
        item1.put("booked", true);
        item1.put("parentId", pkgId);
        mockMvc.perform(post("/api/projects/" + projectId + "/vendors")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + planner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(item1)))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/admin/reports/vendors-by-category")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.categoryName == 'Venue')].vendorCount").value(1))
                .andExpect(jsonPath("$[?(@.categoryName == 'Venue')].bookedCount").value(1))
                .andExpect(jsonPath("$[?(@.categoryName == 'Catering')]").doesNotExist());

        mockMvc.perform(get("/api/admin/reports/booking-conversion")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.categories[?(@.categoryName == 'Catering')]").doesNotExist());
    }

    @Test
    void softDeletedVendorsAreExcludedFromEveryReport() throws Exception {
        String planner = register("rep-soft-delete-planner@wedding.test", "ROLE_PLANNER");
        String admin = loginAdmin();
        String projectId = project(planner, "Rep Soft Delete Wedding", "2027-10-01");

        var body = new HashMap<String, Object>();
        body.put("name", "Fading Musician");
        body.put("categoryId", categoryId(planner, "MUSIC"));
        body.put("booked", true);
        String vendorId = json(mockMvc.perform(post("/api/projects/" + projectId + "/vendors")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + planner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andReturn()).get("id").asText();

        // Live: counted in vendors-by-category.
        mockMvc.perform(get("/api/admin/reports/vendors-by-category")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.categoryName == 'Music')].vendorCount").value(1));

        Vendor vendor = vendorRepository.findById(UUID.fromString(vendorId)).orElseThrow();
        vendor.setDeletedAt(Instant.now());
        vendorRepository.saveAndFlush(vendor);

        // Soft-deleted: gone from every report, same as a hard-deleted vendor always was.
        mockMvc.perform(get("/api/admin/reports/vendors-by-category")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.categoryName == 'Music')]").doesNotExist());
        mockMvc.perform(get("/api/admin/reports/in-demand-vendors")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.vendorName == 'Fading Musician')]").doesNotExist());
        mockMvc.perform(get("/api/admin/reports/booking-conversion")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.categories[?(@.categoryName == 'Music')]").doesNotExist());
    }

    @Test
    void reportsAreAdminOnly() throws Exception {
        String planner = register("rep-guard@wedding.test", "ROLE_PLANNER");
        mockMvc.perform(get("/api/admin/reports/vendors-by-category")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + planner))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/admin/reports/in-demand-vendors")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + planner))
                .andExpect(status().isForbidden());
    }
}
