package eu.openminted.content.bridge;

public class IndexResponse {
    private String openaAireId;
    private String hashKey;
    private String mimeType;
    private String url;

    String getOpenaAireId() {
        return openaAireId;
    }

    public void setOpenaAireId(String openaAireId) {
        this.openaAireId = openaAireId;
    }

    String getHashKey() {
        return hashKey;
    }

    public void setHashKey(String hashKey) {
        this.hashKey = hashKey;
    }

    String getMimeType() {
        return mimeType;
    }

    public void setMimeType(String mimeType) {
        this.mimeType = mimeType;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }
}
