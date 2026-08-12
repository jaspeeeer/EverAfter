package com.wedding.planner.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

/**
 * Full-HTTP proof of the attachment feature: upload → list → download → delete on a vendor, RBAC
 * (the owning couple has full read/write access; an unrelated planner is denied entirely), and
 * survival through a soft-deleted-then-restored owning vendor (attachments are never touched by
 * {@code VendorService.delete}/{@code restore} — nothing is actually removed, so there's nothing
 * to clean up or bring back).
 */
@Transactional
class AttachmentControllerIntegrationTest extends AbstractIntegrationTest {

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

    private JsonNode json(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private String createProject(String token, String name, String ownerEmail) throws Exception {
        var body = new java.util.HashMap<String, Object>();
        body.put("name", name);
        if (ownerEmail != null) body.put("ownerEmail", ownerEmail);
        MvcResult result = mockMvc.perform(post("/api/projects")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andReturn();
        return json(result).get("id").asText();
    }

    private String categoryId(String token) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/vendor-categories")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        return json(result).get(0).get("id").asText();
    }

    private String createVendor(String token, String projectId, String name) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/projects/" + projectId + "/vendors")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", name,
                                "categoryId", categoryId(token),
                                "booked", false))))
                .andExpect(status().isCreated())
                .andReturn();
        return json(result).get("id").asText();
    }

    private MockMultipartFile pdf(String filename) {
        return new MockMultipartFile("file", filename, "application/pdf",
                "%PDF-1.4 fake contract bytes".getBytes());
    }

    @Test
    void plannerUploadsListsDownloadsAndDeletesAVendorAttachment() throws Exception {
        String planner = register("attach-planner-" + System.nanoTime() + "@t", "ROLE_PLANNER");
        String projectId = createProject(planner, "Attachment Wedding", null);
        String vendorId = createVendor(planner, projectId, "Bloom Florist");

        MvcResult uploaded = mockMvc.perform(multipart(
                        "/api/projects/" + projectId + "/vendors/" + vendorId + "/attachments")
                        .file(pdf("contract.pdf"))
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + planner))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.filename").value("contract.pdf"))
                .andExpect(jsonPath("$.contentType").value("application/pdf"))
                .andReturn();
        String attachmentId = json(uploaded).get("id").asText();

        // Listed under the project.
        mockMvc.perform(get("/api/projects/" + projectId + "/attachments")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + planner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].filename").value("contract.pdf"));

        // Listed filtered by owner.
        mockMvc.perform(get("/api/projects/" + projectId + "/attachments")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + planner)
                        .param("ownerType", "VENDOR")
                        .param("ownerId", vendorId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));

        // Download round-trips the exact bytes and content type.
        MvcResult downloaded = mockMvc.perform(get(
                        "/api/projects/" + projectId + "/attachments/" + attachmentId + "/download")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + planner))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(downloaded.getResponse().getContentType()).isEqualTo("application/pdf");
        assertThat(downloaded.getResponse().getContentAsString())
                .isEqualTo("%PDF-1.4 fake contract bytes");
        assertThat(downloaded.getResponse().getHeader(HttpHeaders.CONTENT_DISPOSITION))
                .contains("contract.pdf");

        // Delete, then confirm it's gone from the list and 404s on download.
        mockMvc.perform(delete("/api/projects/" + projectId + "/attachments/" + attachmentId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + planner))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/projects/" + projectId + "/attachments")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + planner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
        mockMvc.perform(get(
                        "/api/projects/" + projectId + "/attachments/" + attachmentId + "/download")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + planner))
                .andExpect(status().isNotFound());
    }

    @Test
    void coupleCanUploadListDownloadAndDeleteTheirOwnAttachment() throws Exception {
        String planner = register("attach-couple-planner-" + System.nanoTime() + "@t", "ROLE_PLANNER");
        String coupleEmail = "attach-couple-" + System.nanoTime() + "@t";
        String couple = register(coupleEmail, "ROLE_USER");
        String projectId = createProject(planner, "Couple Wedding", coupleEmail);
        String vendorId = createVendor(planner, projectId, "Caterer");

        MvcResult plannerUpload = mockMvc.perform(multipart(
                        "/api/projects/" + projectId + "/vendors/" + vendorId + "/attachments")
                        .file(pdf("menu.pdf"))
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + planner))
                .andExpect(status().isCreated())
                .andReturn();
        String plannerAttachmentId = json(plannerUpload).get("id").asText();

        // Couple can read what the planner uploaded: list + download.
        mockMvc.perform(get("/api/projects/" + projectId + "/attachments")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + couple))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
        mockMvc.perform(get(
                        "/api/projects/" + projectId + "/attachments/" + plannerAttachmentId + "/download")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + couple))
                .andExpect(status().isOk());

        // Couple can also upload their own paperwork — canAccess now covers writes too.
        MvcResult coupleUpload = mockMvc.perform(multipart(
                        "/api/projects/" + projectId + "/vendors/" + vendorId + "/attachments")
                        .file(pdf("our-copy.pdf"))
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + couple))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.filename").value("our-copy.pdf"))
                .andReturn();
        String coupleAttachmentId = json(coupleUpload).get("id").asText();

        // The planner sees the couple's upload too — same project-scoped list.
        mockMvc.perform(get("/api/projects/" + projectId + "/attachments")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + planner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));

        // Couple can delete — their own upload, and the planner's.
        mockMvc.perform(delete("/api/projects/" + projectId + "/attachments/" + coupleAttachmentId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + couple))
                .andExpect(status().isNoContent());
        mockMvc.perform(delete("/api/projects/" + projectId + "/attachments/" + plannerAttachmentId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + couple))
                .andExpect(status().isNoContent());
    }

    @Test
    void unrelatedPlannerIsForbiddenEntirely() throws Exception {
        String plannerA = register("attach-a-" + System.nanoTime() + "@t", "ROLE_PLANNER");
        String plannerB = register("attach-b-" + System.nanoTime() + "@t", "ROLE_PLANNER");
        String projectId = createProject(plannerA, "Isolated Wedding", null);
        String vendorId = createVendor(plannerA, projectId, "DJ");

        mockMvc.perform(multipart(
                        "/api/projects/" + projectId + "/vendors/" + vendorId + "/attachments")
                        .file(pdf("contract.pdf"))
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + plannerB))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/projects/" + projectId + "/attachments")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + plannerB))
                .andExpect(status().isForbidden());
    }

    @Test
    void unsupportedFileTypeIsRejected() throws Exception {
        String planner = register("attach-badtype-" + System.nanoTime() + "@t", "ROLE_PLANNER");
        String projectId = createProject(planner, "Bad Type Wedding", null);
        String vendorId = createVendor(planner, projectId, "Photographer");

        MockMultipartFile exe = new MockMultipartFile(
                "file", "virus.exe", "application/x-msdownload", "MZ".getBytes());

        mockMvc.perform(multipart(
                        "/api/projects/" + projectId + "/vendors/" + vendorId + "/attachments")
                        .file(exe)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + planner))
                .andExpect(status().isUnsupportedMediaType());
    }

    @Test
    void softDeletingTheVendorKeepsItsAttachmentsAndRestoreBringsItBack() throws Exception {
        String planner = register("attach-cascade-" + System.nanoTime() + "@t", "ROLE_PLANNER");
        String projectId = createProject(planner, "Cascade Wedding", null);
        String vendorId = createVendor(planner, projectId, "Baker");

        mockMvc.perform(multipart(
                        "/api/projects/" + projectId + "/vendors/" + vendorId + "/attachments")
                        .file(pdf("cake-order.pdf"))
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + planner))
                .andExpect(status().isCreated());

        mockMvc.perform(delete("/api/projects/" + projectId + "/vendors/" + vendorId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + planner))
                .andExpect(status().isNoContent());

        // Soft delete — the vendor is gone from the list but its attachment row/file is untouched.
        mockMvc.perform(get("/api/projects/" + projectId + "/vendors")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + planner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
        mockMvc.perform(get("/api/projects/" + projectId + "/attachments")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + planner)
                        .param("ownerType", "VENDOR")
                        .param("ownerId", vendorId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].filename").value("cake-order.pdf"));

        mockMvc.perform(post("/api/projects/" + projectId + "/vendors/" + vendorId + "/restore")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + planner))
                .andExpect(status().isOk());

        // Restored — the vendor is back and the same attachment is still exactly there.
        mockMvc.perform(get("/api/projects/" + projectId + "/vendors")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + planner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
        mockMvc.perform(get("/api/projects/" + projectId + "/attachments")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + planner)
                        .param("ownerType", "VENDOR")
                        .param("ownerId", vendorId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].filename").value("cake-order.pdf"));
    }
}
