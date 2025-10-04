package com.testing.multisource.api.attachment;

public interface AttachmentService {
    void attachJson(AttachmentType type, String name, Object data);
    void attachText(AttachmentType type, String name, String content);
}
