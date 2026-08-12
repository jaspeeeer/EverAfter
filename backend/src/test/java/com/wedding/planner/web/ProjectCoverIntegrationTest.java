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
 * Full-HTTP proof of the project cover photo: upload replaces the old one (which stops being
 * downloadable), the public invitation page can stream it without auth, and removal 404s the
 * public route again.
 */
@Transactional
class ProjectCoverIntegrationTest extends AbstractIntegrationTest {

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
        return new MockMultipartFile("file", "cover.jpg", "image/jpeg", bytes);
    }

    @Test
    void uploadingACoverMakesItVisibleOnThePublicInvitationPageAndSecondUploadReplacesIt()
            throws Exception {
        String planner = register("cover-planner-" + System.nanoTime() + "@t", "ROLE_PLANNER");
        String projectId = createProject(planner, "Cover Wedding");
        String rsvpToken = createGuestRsvpToken(planner, projectId);

        // Before any cover, the invitation page shows nothing and the route 404s.
        mockMvc.perform(get("/api/public/rsvp/" + rsvpToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hasCover").value(false));
        mockMvc.perform(get("/api/public/rsvp/" + rsvpToken + "/cover"))
                .andExpect(status().isNotFound());

        // Upload the first cover.
        MvcResult firstUpload = mockMvc.perform(multipart("/api/projects/" + projectId + "/cover")
                        .file(jpeg("first-bytes".getBytes()))
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + planner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.coverAttachmentId").isNotEmpty())
                .andReturn();
        String firstAttachmentId = json(firstUpload).get("coverAttachmentId").asText();

        mockMvc.perform(get("/api/public/rsvp/" + rsvpToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hasCover").value(true));
        MvcResult firstCover = mockMvc.perform(get("/api/public/rsvp/" + rsvpToken + "/cover"))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(firstCover.getResponse().getContentType()).isEqualTo("image/jpeg");
        assertThat(firstCover.getResponse().getContentAsString()).isEqualTo("first-bytes");

        // Upload a second cover — it replaces the first, which stops existing entirely.
        MvcResult secondUpload = mockMvc.perform(multipart("/api/projects/" + projectId + "/cover")
                        .file(jpeg("second-bytes".getBytes()))
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + planner))
                .andExpect(status().isOk())
                .andReturn();
        String secondAttachmentId = json(secondUpload).get("coverAttachmentId").asText();
        assertThat(secondAttachmentId).isNotEqualTo(firstAttachmentId);

        MvcResult secondCover = mockMvc.perform(get("/api/public/rsvp/" + rsvpToken + "/cover"))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(secondCover.getResponse().getContentAsString()).isEqualTo("second-bytes");

        // The first attachment is gone, not merely unlinked — its own download route 404s too.
        mockMvc.perform(get("/api/projects/" + projectId + "/attachments/" + firstAttachmentId
                        + "/download")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + planner))
                .andExpect(status().isNotFound());

        // Removing the cover clears it from both the planner view and the public page.
        mockMvc.perform(delete("/api/projects/" + projectId + "/cover")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + planner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.coverAttachmentId").doesNotExist());
        mockMvc.perform(get("/api/public/rsvp/" + rsvpToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hasCover").value(false));
        mockMvc.perform(get("/api/public/rsvp/" + rsvpToken + "/cover"))
                .andExpect(status().isNotFound());
    }

    @Test
    void removingACoverThatDoesNotExistIs404() throws Exception {
        String planner = register("cover-none-" + System.nanoTime() + "@t", "ROLE_PLANNER");
        String projectId = createProject(planner, "No Cover Wedding");

        mockMvc.perform(delete("/api/projects/" + projectId + "/cover")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + planner))
                .andExpect(status().isNotFound());
    }

    @Test
    void unrelatedPlannerCannotSetOrRemoveAnotherProjectsCover() throws Exception {
        String plannerA = register("cover-a-" + System.nanoTime() + "@t", "ROLE_PLANNER");
        String plannerB = register("cover-b-" + System.nanoTime() + "@t", "ROLE_PLANNER");
        String projectId = createProject(plannerA, "Isolated Cover Wedding");

        mockMvc.perform(multipart("/api/projects/" + projectId + "/cover")
                        .file(jpeg("bytes".getBytes()))
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + plannerB))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/projects/" + projectId + "/cover")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + plannerB))
                .andExpect(status().isForbidden());
    }
}
