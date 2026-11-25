package com.popman.arca.dto.v1.post;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import jakarta.validation.constraints.Size;
import java.io.IOException;
import java.util.List;

public class PostUpdateRequest {
    @Size(max = 30, message = "Title must not exceed 30 characters")
    private String title;

    private String content;

    @com.fasterxml.jackson.annotation.JsonProperty("post_tag")
    private String postTag;

    @io.swagger.v3.oas.annotations.media.Schema(types = {"array", "null"}, description = "Ordered HTTP(S) links, maximum 10. Omitted/null copies source version links; [] clears only the new version; canonical duplicates reject the request.")
    @jakarta.validation.constraints.Size(max = 10)
    private java.util.List<PostLinkRequest> links;
    public java.util.List<PostLinkRequest> getLinks() { return links; }
    public void setLinks(java.util.List<PostLinkRequest> links) { this.links = links; }

    @com.fasterxml.jackson.annotation.JsonProperty("retain_attachment_ids")
    @io.swagger.v3.oas.annotations.media.Schema(types = {"array", "null"},
            description = "Unique attachment IDs from the source revision to keep. Omitted/null keeps all; [] keeps none. "
                    + "Retained attachments get new IDs, reuse stored bytes, and count toward the three-file limit. "
                    + "To replace files, POST to /api/v1/files/upload before approval, passing the update response's id as post_id. "
                    + "Historical attachments are unchanged.")
    @JsonDeserialize(contentUsing = AttachmentIdDeserializer.class)
    @Size(max = 3)
    private List<Long> retainAttachmentIds;

    public List<Long> getRetainAttachmentIds() { return retainAttachmentIds; }
    public void setRetainAttachmentIds(List<Long> retainAttachmentIds) { this.retainAttachmentIds = retainAttachmentIds; }

    public static final class AttachmentIdDeserializer extends JsonDeserializer<Long> {
        @Override
        public Long deserialize(JsonParser parser, DeserializationContext context) throws IOException {
            if (!parser.hasToken(JsonToken.VALUE_NUMBER_INT)) {
                return context.reportInputMismatch(Long.class, "Attachment IDs must be JSON integers");
            }
            return parser.getLongValue();
        }
    }

    public PostUpdateRequest() {}

    // Getters and Setters
    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getPostTag() {
        return postTag;
    }

    public void setPostTag(String postTag) {
        this.postTag = postTag;
    }
}
