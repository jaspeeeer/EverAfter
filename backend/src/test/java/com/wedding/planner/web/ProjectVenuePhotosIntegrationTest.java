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
 * Full-HTTP proof of the ceremony/reception photo slots — the two new named photo slots added
 * alongside the existing cover ({@link ProjectCoverIntegrationTest}). Same behavior per slot
 * (upload replaces old, public stream works, remove 404s, cross-tenant 403s), so each scenario
 * runs once per slot via a shared helper rather than duplicating the whole class twice.
 */
@Transactional
class ProjectVenuePhotosIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    /** A photo slot under test: its endpoint path segment, response id field, and public "has" flag. */
    private record Slot(String path, String idField, String hasField) {
        static final Slot CEREMONY = new Slot("ceremony-photo", "ceremonyPhotoAttachmentId", "hasCeremonyPhoto");
        static final Slot RECEPTION = new Slot("reception-photo", "receptionPhotoAttachmentId", "hasReceptionPhoto");
    }

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

    private String createProject(String token, String name) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/projects")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("name", name))))
                .andExpect(status().isCreated())
                .andReturn();
        return json(result).get("id").asText();
    }

    private String createGuestRsvpToken(String token, String projectId) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/projects/" + projectId + "/guests")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "firstName", "Guest", "rsvpStatus", "PENDING"))))
                .andExpect(status().isCreated())
                .andReturn();
        return json(result).get("rsvpToken").asText();
    }

    private MockMultipartFile jpeg(byte[] bytes) {
        return new MockMultipartFile("file", "photo.jpg", "image/jpeg", bytes);
    }

    private void verifyUploadReplaceAndRemove(Slot slot) throws Exception {
        String planner = register(slot.path() + "-planner-" + System.nanoTime() + "@t", "ROLE_PLANNER");
        String projectId = createProject(planner, slot.path() + " Wedding");
        String rsvpToken = createGuestRsvpToken(planner, projectId);

        // Before any photo, the invitation page shows nothing and the route 404s.
        mockMvc.perform(get("/api/public/rsvp/" + rsvpToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$." + slot.hasField()).value(false));
        mockMvc.perform(get("/api/public/rsvp/" + rsvpToken + "/" + slot.path()))
                .andExpect(status().isNotFound());

        // Upload the first photo.
        MvcResult firstUpload = mockMvc.perform(multipart("/api/projects/" + projectId + "/" + slot.path())
                        .file(jpeg("first-bytes".getBytes()))
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + planner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$." + slot.idField()).isNotEmpty())
                .andReturn();
        String firstAttachmentId = json(firstUpload).get(slot.idField()).asText();

        mockMvc.perform(get("/api/public/rsvp/" + rsvpToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$." + slot.hasField()).value(true));
        MvcResult firstPhoto = mockMvc.perform(get("/api/public/rsvp/" + rsvpToken + "/" + slot.path()))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(firstPhoto.getResponse().getContentType()).isEqualTo("image/jpeg");
        assertThat(firstPhoto.getResponse().getContentAsString()).isEqualTo("first-bytes");

        // Upload a second photo — it replaces the first, which stops existing entirely.
        MvcResult secondUpload = mockMvc.perform(multipart("/api/projects/" + projectId + "/" + slot.path())
                        .file(jpeg("second-bytes".getBytes()))
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + planner))
                .andExpect(status().isOk())
                .andReturn();
        String secondAttachmentId = json(secondUpload).get(slot.idField()).asText();
        assertThat(secondAttachmentId).isNotEqualTo(firstAttachmentId);

        MvcResult secondPhoto = mockMvc.perform(get("/api/public/rsvp/" + rsvpToken + "/" + slot.path()))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(secondPhoto.getResponse().getContentAsString()).isEqualTo("second-bytes");

        // The first attachment is gone, not merely unlinked — its own download route 404s too.
        mockMvc.perform(get("/api/projects/" + projectId + "/attachments/" + firstAttachmentId
                        + "/download")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + planner))
                .andExpect(status().isNotFound());

        // Removing the photo clears it from both the planner view and the public page.
        mockMvc.perform(delete("/api/projects/" + projectId + "/" + slot.path())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + planner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$." + slot.idField()).doesNotExist());
        mockMvc.perform(get("/api/public/rsvp/" + rsvpToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$." + slot.hasField()).value(false));
        mockMvc.perform(get("/api/public/rsvp/" + rsvpToken + "/" + slot.path()))
                .andExpect(status().isNotFound());
    }

    private void verifyRemovingNonExistentIs404(Slot slot) throws Exception {
        String planner = register(slot.path() + "-none-" + System.nanoTime() + "@t", "ROLE_PLANNER");
        String projectId = createProject(planner, "No " + slot.path() + " Wedding");

        mockMvc.perform(delete("/api/projects/" + projectId + "/" + slot.path())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + planner))
                .andExpect(status().isNotFound());
    }

    private void verifyUnrelatedPlannerIsForbidden(Slot slot) throws Exception {
        String plannerA = register(slot.path() + "-a-" + System.nanoTime() + "@t", "ROLE_PLANNER");
        String plannerB = register(slot.path() + "-b-" + System.nanoTime() + "@t", "ROLE_PLANNER");
        String projectId = createProject(plannerA, "Isolated " + slot.path() + " Wedding");

        mockMvc.perform(multipart("/api/projects/" + projectId + "/" + slot.path())
                        .file(jpeg("bytes".getBytes()))
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + plannerB))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/projects/" + projectId + "/" + slot.path())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + plannerB))
                .andExpect(status().isForbidden());
    }

    @Test
    void ceremonyPhotoUploadReplaceAndRemove() throws Exception {
        verifyUploadReplaceAndRemove(Slot.CEREMONY);
    }

    @Test
    void ceremonyPhotoRemovingNonExistentIs404() throws Exception {
        verifyRemovingNonExistentIs404(Slot.CEREMONY);
    }

    @Test
    void ceremonyPhotoUnrelatedPlannerIsForbidden() throws Exception {
        verifyUnrelatedPlannerIsForbidden(Slot.CEREMONY);
    }

    @Test
    void receptionPhotoUploadReplaceAndRemove() throws Exception {
        verifyUploadReplaceAndRemove(Slot.RECEPTION);
    }

    @Test
    void receptionPhotoRemovingNonExistentIs404() throws Exception {
        verifyRemovingNonExistentIs404(Slot.RECEPTION);
    }

    @Test
    void receptionPhotoUnrelatedPlannerIsForbidden() throws Exception {
        verifyUnrelatedPlannerIsForbidden(Slot.RECEPTION);
    }
}
