package com.contextsmith.api.data;

/**
 * Value class for attachment information from e-mails.
 * Created by beders on 6/28/17.
 */
public class Attachment {
    String urn;
    String name;
    String checksum;
    String mimeType;

    public Attachment(String urn, String fileName, String sha, String mimeType) {
        this.urn = urn;
        this.name = fileName;
        this.checksum = sha;
        this.mimeType = mimeType;
    }

    @Override
    public String toString() {
        return "[Attachment " + urn + " file:'" + name + "' mime:'" + mimeType + "' sha:" + checksum + "]";
    }
}
